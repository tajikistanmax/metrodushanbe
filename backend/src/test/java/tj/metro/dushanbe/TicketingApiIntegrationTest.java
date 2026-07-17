package tj.metro.dushanbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Сквозной тест билетного контура (TKT-03…06, U-CIT-08) поверх реального
 * PostgreSQL/PostGIS: проверяет V023 вместе с инвариантами БД и публичным
 * контрактом.
 *
 * <p>Проверяются ровно те свойства, ради которых модуль устроен именно так:
 * токен выдаётся один раз, использованный билет не проходит повторно, возврат
 * двигает и платёж, и билет, заблокированный билет не валидируется, а цена
 * зафиксирована на момент покупки и за тарифом не едет.
 *
 * <p>Пути — без префикса /api: TestRestTemplate сам добавляет context-path.
 * Каждый тест работает со своим тарифом/покупателем — контейнер и контекст общие
 * на класс, а тесты не обязаны выполняться в каком-то порядке.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TicketingApiIntegrationTest {

    private static final String ADMIN_KEY = "test-admin-key";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("metro")
            .withUsername("metro")
            .withPassword("metro");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.admin.dev-key", () -> ADMIN_KEY);
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void purchaseIssuesDemoMarkedTicketAndReturnsTokenExactlyOnce() throws Exception {
        JsonNode purchase = buy("DEMO-SINGLE", "it-device-once", null, HttpStatus.CREATED);

        String token = purchase.path("token").asText();
        assertFalse(token.isBlank(), "токен выдаётся при покупке");
        assertTrue(purchase.path("demo").asBoolean(), "demo-контур обязан быть промаркирован");
        assertTrue(purchase.path("ticket").path("demo").asBoolean());
        assertTrue(purchase.path("payment").path("demo").asBoolean());
        assertFalse(purchase.path("notice").asText().isBlank(), "пассажир видит пометку демо");
        assertEquals("captured", purchase.path("payment").path("status").asText());
        assertEquals("demo", purchase.path("payment").path("provider").asText());

        // Второй раз токен не отдаётся никем: ни публичной карточкой билета...
        String code = purchase.path("ticket").path("code").asText();
        JsonNode ticket = objectMapper.readTree(
                rest.getForEntity("/v1/tickets/" + code, String.class).getBody());
        assertTrue(ticket.path("token").isMissingNode(), "токена нет в публичной карточке");
        assertTrue(ticket.path("tokenHash").isMissingNode(), "хеш наружу тоже не отдаётся");

        // ...ни консолью оператора.
        JsonNode adminTicket = objectMapper.readTree(adminGet("/v1/admin/tickets/" + code).getBody());
        assertTrue(adminTicket.path("token").isMissingNode(), "оператор токена не видит");
        assertTrue(adminTicket.path("tokenHash").isMissingNode());
    }

    @Test
    void usedSingleTicketIsRejectedOnSecondValidation() throws Exception {
        JsonNode purchase = buy("DEMO-SINGLE", "it-device-used", null, HttpStatus.CREATED);
        String token = purchase.path("token").asText();

        JsonNode first = validate(token);
        assertTrue(first.path("valid").asBoolean(), "первый проход разрешён");
        assertEquals("used", first.path("status").asText(), "разовый билет гасится");

        JsonNode second = validate(token);
        assertFalse(second.path("valid").asBoolean(), "повторный проход по тому же билету запрещён");
        assertEquals("ticket.already_used", second.path("reason").asText());
    }

    @Test
    void unknownTokenIsIndistinguishableFromAnInvalidTicket() throws Exception {
        // validate() внутри уже требует HTTP 200 — и это часть проверки: неизвестный
        // токен не отвечает 404, иначе эндпоинт работал бы оракулом для перебора.
        JsonNode result = validate("this-token-never-existed");

        assertFalse(result.path("valid").asBoolean());
        assertEquals("ticket.token_unknown", result.path("reason").asText());
        assertTrue(result.path("ticketCode").isNull(), "существование токена не раскрывается");
    }

    @Test
    void refundMovesBothPaymentAndTicketToRefunded() throws Exception {
        JsonNode purchase = buy("DEMO-SINGLE", "it-device-refund", null, HttpStatus.CREATED);
        String ticketCode = purchase.path("ticket").path("code").asText();
        String paymentCode = purchase.path("payment").path("code").asText();

        ResponseEntity<String> refunded = rest.exchange("/v1/tickets/" + ticketCode + "/refund",
                HttpMethod.POST, new HttpEntity<>(Map.of("reason", "передумал"), json()), String.class);

        assertEquals(HttpStatus.OK, refunded.getStatusCode());
        JsonNode refund = objectMapper.readTree(refunded.getBody());
        assertEquals("completed", refund.path("status").asText());
        assertEquals(paymentCode, refund.path("paymentCode").asText());
        assertTrue(refund.path("demo").asBoolean());

        JsonNode ticket = objectMapper.readTree(
                rest.getForEntity("/v1/tickets/" + ticketCode, String.class).getBody());
        assertEquals("refunded", ticket.path("status").asText(), "билет погашен возвратом");

        JsonNode payments = objectMapper.readTree(
                adminGet("/v1/admin/tickets/" + ticketCode + "/payments").getBody());
        assertEquals("refunded", payments.get(0).path("status").asText(), "платёж возвращён");

        // Возвращённый билет больше не проходит.
        assertEquals("ticket.refunded", validate(purchase.path("token").asText())
                .path("reason").asText());
    }

    @Test
    void blockedTicketIsNotValidated() throws Exception {
        JsonNode purchase = buy("DEMO-SINGLE", "it-device-blocked", null, HttpStatus.CREATED);
        String ticketCode = purchase.path("ticket").path("code").asText();

        ResponseEntity<String> blocked = rest.exchange("/v1/admin/blocklist", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "subjectType", "ticket",
                        "subjectValue", ticketCode,
                        "reason", "подозрение на подделку"), adminHeaders()), String.class);
        assertEquals(HttpStatus.CREATED, blocked.getStatusCode());

        // Блокировка действует немедленно, ещё до предъявления.
        JsonNode ticket = objectMapper.readTree(
                rest.getForEntity("/v1/tickets/" + ticketCode, String.class).getBody());
        assertEquals("blocked", ticket.path("status").asText());

        JsonNode result = validate(purchase.path("token").asText());
        assertFalse(result.path("valid").asBoolean(), "заблокированный билет не пропускается");
        assertEquals("ticket.blocked", result.path("reason").asText());
    }

    @Test
    void blocklistedRiderCannotBuyAndTheirTicketsAreBlocked() throws Exception {
        JsonNode purchase = buy("DEMO-SINGLE", "it-device-fraud", null, HttpStatus.CREATED);
        String ticketCode = purchase.path("ticket").path("code").asText();

        rest.exchange("/v1/admin/blocklist", HttpMethod.POST, new HttpEntity<>(Map.of(
                "subjectType", "rider",
                "subjectValue", "it-device-fraud",
                "reason", "мошенничество"), adminHeaders()), String.class);

        JsonNode ticket = objectMapper.readTree(
                rest.getForEntity("/v1/tickets/" + ticketCode, String.class).getBody());
        assertEquals("blocked", ticket.path("status").asText(), "уже выпущенные билеты блокируются");

        ResponseEntity<String> blockedPurchase = rest.exchange("/v1/tickets/purchase", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "fareProductCode", "DEMO-SINGLE",
                        "riderRef", "it-device-fraud"), json()), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, blockedPurchase.getStatusCode());
        assertEquals("ticketing.rider_blocked",
                objectMapper.readTree(blockedPurchase.getBody()).path("error").path("code").asText());
    }

    @Test
    void ticketKeepsPurchasePriceAfterFareGoesUpAndIsWithdrawn() throws Exception {
        // Свой тариф, чтобы правка цены не задела остальные тесты класса.
        Map<String, String> i18n = Map.of("tg", "Чипта", "ru", "Билет ИТ", "en", "IT ticket");
        ResponseEntity<String> created = rest.exchange("/v1/admin/fares", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "code", "IT-PRICE-LOCK",
                        "name", i18n,
                        "description", i18n,
                        "amount", 3.00,
                        "currency", "TJS",
                        "riderCategory", "all",
                        "validityMinutes", 90,
                        "active", true), adminHeaders()), String.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());

        JsonNode purchase = buy("IT-PRICE-LOCK", "it-device-price", null, HttpStatus.CREATED);
        String ticketCode = purchase.path("ticket").path("code").asText();
        assertEquals(3.00, purchase.path("ticket").path("priceAmount").asDouble());

        // Тариф дорожает вдвое, меняет категорию и снимается с продажи.
        ResponseEntity<String> updated = rest.exchange("/v1/admin/fares/IT-PRICE-LOCK",
                HttpMethod.PUT, new HttpEntity<>(Map.of(
                        "name", i18n,
                        "description", i18n,
                        "amount", 6.00,
                        "currency", "TJS",
                        "riderCategory", "adult",
                        "validityMinutes", 90,
                        "active", false), adminHeaders()), String.class);
        assertEquals(HttpStatus.OK, updated.getStatusCode());

        JsonNode ticket = objectMapper.readTree(
                rest.getForEntity("/v1/tickets/" + ticketCode, String.class).getBody());
        assertEquals(3.00, ticket.path("priceAmount").asDouble(),
                "цена зафиксирована на момент покупки");
        assertEquals("all", ticket.path("riderCategory").asText(), "категория тоже зафиксирована");
        assertNotEquals("blocked", ticket.path("status").asText(),
                "снятие тарифа с продажи не должно ломать уже выпущенный билет");

        // При этом купить по снятому тарифу больше нельзя.
        ResponseEntity<String> rejected = rest.exchange("/v1/tickets/purchase", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "fareProductCode", "IT-PRICE-LOCK",
                        "riderRef", "it-device-price-2"), json()), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, rejected.getStatusCode());
        assertEquals("ticketing.fare_inactive",
                objectMapper.readTree(rejected.getBody()).path("error").path("code").asText());
    }

    @Test
    void declinedPaymentReturns402AndLeavesFailedAttemptWithReason() throws Exception {
        JsonNode declined = buy("DEMO-SINGLE", "it-device-declined", "decline",
                HttpStatus.PAYMENT_REQUIRED);

        assertTrue(declined.path("ticket").isNull(), "билет без списания не выпускается");
        assertTrue(declined.path("token").isNull());
        assertEquals("failed", declined.path("payment").path("status").asText());
        assertFalse(declined.path("payment").path("failureReason").asText().isBlank(),
                "chk_payment_error: провал обязан быть объяснён");
        assertTrue(declined.path("payment").path("ticketCode").isNull());

        // Отклонённая попытка сохранена — она материал антифрода (TKT-06).
        JsonNode failedPayments = objectMapper.readTree(
                adminGet("/v1/admin/payments?status=failed").getBody());
        assertTrue(failedPayments.size() > 0, "запись об отказе должна дожить до коммита");
    }

    @Test
    void passIsNotConsumedByValidationAndTopUpExtendsIt() throws Exception {
        JsonNode purchase = buy("DEMO-MONTHLY", "it-device-pass", null, HttpStatus.CREATED);
        String ticketCode = purchase.path("ticket").path("code").asText();
        assertEquals("pass", purchase.path("ticket").path("kind").asText());
        String validUntilBefore = purchase.path("ticket").path("validUntil").asText();

        JsonNode validated = validate(purchase.path("token").asText());
        assertTrue(validated.path("valid").asBoolean());
        assertEquals("active", validated.path("status").asText(), "проездной не гасится");

        ResponseEntity<String> toppedUp = rest.exchange("/v1/tickets/" + ticketCode + "/topup",
                HttpMethod.POST, new HttpEntity<>(Map.of("amount", 50.00), json()), String.class);

        assertEquals(HttpStatus.OK, toppedUp.getStatusCode());
        JsonNode response = objectMapper.readTree(toppedUp.getBody());
        assertEquals("topup", response.path("payment").path("kind").asText());
        assertEquals(50.00, response.path("ticket").path("balanceAmount").asDouble());
        assertNotEquals(validUntilBefore, response.path("ticket").path("validUntil").asText(),
                "пополнение продлевает срок действия");
    }

    @Test
    void singleTicketCannotBeToppedUp() throws Exception {
        JsonNode purchase = buy("DEMO-SINGLE", "it-device-nottopup", null, HttpStatus.CREATED);
        String ticketCode = purchase.path("ticket").path("code").asText();

        ResponseEntity<String> response = rest.exchange("/v1/tickets/" + ticketCode + "/topup",
                HttpMethod.POST, new HttpEntity<>(Map.of("amount", 10.00), json()), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("ticket.topup_not_supported",
                objectMapper.readTree(response.getBody()).path("error").path("code").asText());
    }

    @Test
    void operatorRefundOfUsedTicketIsAllowedAndAudited() throws Exception {
        JsonNode purchase = buy("DEMO-SINGLE", "it-device-manual", null, HttpStatus.CREATED);
        String ticketCode = purchase.path("ticket").path("code").asText();
        validate(purchase.path("token").asText());

        // Пассажиру использованный билет не вернуть...
        ResponseEntity<String> publicRefund = rest.exchange("/v1/tickets/" + ticketCode + "/refund",
                HttpMethod.POST, new HttpEntity<>(Map.of("reason", "хочу назад"), json()), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, publicRefund.getStatusCode());
        assertEquals("ticket.not_refundable",
                objectMapper.readTree(publicRefund.getBody()).path("error").path("code").asText());

        // ...а оператору — можно, под своим именем.
        ResponseEntity<String> manual = rest.exchange("/v1/admin/tickets/" + ticketCode + "/refund",
                HttpMethod.POST, new HttpEntity<>(Map.of("reason", "сбой турникета"), adminHeaders()),
                String.class);
        assertEquals(HttpStatus.OK, manual.getStatusCode());
        assertEquals("completed", objectMapper.readTree(manual.getBody()).path("status").asText());

        JsonNode audit = objectMapper.readTree(adminGet("/v1/admin/audit?size=100").getBody())
                .path("items");
        boolean found = false;
        for (JsonNode item : audit) {
            if ("ticket.refund".equals(item.path("action").asText())
                    && ticketCode.equals(item.path("entityId").asText())) {
                found = true;
                assertEquals("admin", item.path("actor").asText());
                assertEquals("refunded", item.path("after").path("status").asText());
            }
        }
        assertTrue(found, "ручной возврат обязан быть в аудите с именем оператора");
    }

    @Test
    void blocklistEntryStoresTokenHashInsteadOfToken() throws Exception {
        ResponseEntity<String> created = rest.exchange("/v1/admin/blocklist", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "subjectType", "token",
                        "subjectValue", "leaked-token-value",
                        "reason", "токен утёк"), adminHeaders()), String.class);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        JsonNode entry = objectMapper.readTree(created.getBody());
        String subjectCode = entry.path("subjectCode").asText();
        assertNotEquals("leaked-token-value", subjectCode, "открытый токен в БД не попадает");
        assertEquals(64, subjectCode.length(), "SHA-256 в hex — 64 символа");

        // Снятие блокировки удаляет запись.
        ResponseEntity<String> removed = rest.exchange(
                "/v1/admin/blocklist/" + entry.path("code").asText(), HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()), String.class);
        assertEquals(HttpStatus.NO_CONTENT, removed.getStatusCode());
    }

    @Test
    void ticketingAdminEndpointsRequireAdminKey() throws Exception {
        ResponseEntity<String> response = rest.getForEntity("/v1/admin/tickets", String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("admin.unauthorized",
                objectMapper.readTree(response.getBody()).path("error").path("code").asText());
    }

    // ------------------------------ Хелперы ---------------------------------

    private JsonNode buy(String fareCode, String riderRef, String scenario, HttpStatus expected)
            throws Exception {
        Map<String, Object> body = scenario == null
                ? Map.of("fareProductCode", fareCode, "riderRef", riderRef)
                : Map.of("fareProductCode", fareCode, "riderRef", riderRef,
                        "demoScenario", scenario);
        ResponseEntity<String> response = rest.exchange("/v1/tickets/purchase", HttpMethod.POST,
                new HttpEntity<>(body, json()), String.class);
        assertEquals(expected, response.getStatusCode(), response.getBody());
        return objectMapper.readTree(response.getBody());
    }

    private JsonNode validate(String token) throws Exception {
        ResponseEntity<String> response = rest.exchange("/v1/tickets/validate", HttpMethod.POST,
                new HttpEntity<>(Map.of("token", token), json()), String.class);
        // Валидация всегда 200: турникету нужно решение, а не исключение.
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return objectMapper.readTree(response.getBody());
    }

    private ResponseEntity<String> adminGet(String path) {
        ResponseEntity<String> response = rest.exchange(path, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return response;
    }

    private static HttpHeaders json() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static HttpHeaders adminHeaders() {
        HttpHeaders headers = json();
        headers.set("X-Admin-Key", ADMIN_KEY);
        // 'admin' — первичный суперадмин из AdminUserBootstrap (dev-дефолт).
        headers.set("X-Admin-Actor", "admin");
        return headers;
    }
}
