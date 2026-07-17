package tj.metro.dushanbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import tj.metro.dushanbe.notification.domain.NotificationMessage;

/**
 * Проверяет V022 и публичный контракт уведомлений на реальном PostgreSQL/PostGIS:
 * jsonb-тексты и jsonb-массив каналов, CHECK-констрейнты через легальные переходы,
 * жизненный цикл рассылки (draft → send → появление в GET /v1/notifications) и
 * повтор доставки (NTF-06).
 *
 * <p>Демо-данных рассылок в V022 нет намеренно, поэтому фид наполняется через
 * admin-контур — это заодно проверяет, что запись и чтение сходятся на одной схеме.
 *
 * <p>Пути — без префикса /api: TestRestTemplate сам добавляет context-path.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationApiIntegrationTest {

    private static final String ADMIN_KEY = "test-admin-key";

    /** Бутстрап-суперадмин (см. AdminUserBootstrap): актор аудита обязан существовать. */
    private static final String ACTOR = "admin";

    private static final Map<String, String> I18N =
            Map.of("tg", "Огоҳӣ", "ru", "Внимание", "en", "Warning");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("metro")
            .withUsername("metro")
            .withPassword("metro");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.admin.dev-key", () -> ADMIN_KEY);
        // Счётчик запросов Hibernate — единственный способ доказать отсутствие
        // N+1 фактом, а не рассуждением; включаем только здесь, в проде статистика
        // стоит производительности.
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void draftIsInvisibleUntilSentAndThenAppearsInFeedWithFullI18n() throws Exception {
        createNotification("NTF-IT-1", List.of("in_app"), List.of());

        // Черновик получателю не виден: в фид попадает только отправленное.
        assertFalse(feedCodes(null, null).contains("NTF-IT-1"), "черновик не должен быть в фиде");

        ResponseEntity<String> sent = rest.exchange("/v1/admin/notifications/NTF-IT-1/send",
                HttpMethod.POST, new HttpEntity<>(adminHeaders()), String.class);
        assertEquals(HttpStatus.OK, sent.getStatusCode());
        assertEquals("sent", objectMapper.readTree(sent.getBody()).path("status").asText());

        JsonNode notification = feedItem("NTF-IT-1");
        assertEquals("info", notification.path("type").asText());
        assertEquals("sent", notification.path("status").asText());
        assertEquals("in_app", notification.path("channels").get(0).asText());
        assertFalse(notification.path("sentAt").asText().isBlank(), "sent обязан нести sentAt");
        // Полнота i18n публичных текстов (BR-CMS-1): tg/ru/en.
        for (String language : List.of("tg", "ru", "en")) {
            assertFalse(notification.path("title").path(language).asText().isBlank(),
                    "title." + language + " не заполнен");
            assertFalse(notification.path("body").path(language).asText().isBlank(),
                    "body." + language + " не заполнен");
        }
    }

    @Test
    void notificationWithoutInAppChannelStaysOutOfTheFeed() throws Exception {
        createNotification("NTF-IT-2", List.of("email", "sms"), List.of());
        send("NTF-IT-2");

        // Рассылка ушла в email/SMS — в ленте приложения ей делать нечего (NTF-01).
        assertFalse(feedCodes(null, null).contains("NTF-IT-2"));
    }

    @Test
    void targetFiltersFollowTheSameRuleAsAlerts() throws Exception {
        createNotification("NTF-IT-NET", List.of("in_app"), List.of());
        createNotification("NTF-IT-L1", List.of("in_app"),
                List.of(Map.of("type", "line", "code", "L1")));
        send("NTF-IT-NET");
        send("NTF-IT-L1");

        // Рассылка без таргетов = вся сеть: попадает при любом фильтре.
        assertTrue(feedCodes("L1", null).contains("NTF-IT-NET"));
        assertTrue(feedCodes("L2", null).contains("NTF-IT-NET"));
        // Таргетированная линией — только под своей линией.
        assertTrue(feedCodes("L1", null).contains("NTF-IT-L1"));
        assertFalse(feedCodes("L2", null).contains("NTF-IT-L1"));
    }

    @Test
    void sentNotificationCannotBeEditedAnyMore() throws Exception {
        createNotification("NTF-IT-3", List.of("in_app"), List.of());
        send("NTF-IT-3");

        Map<String, Object> body = Map.of("type", "promo", "title", I18N, "body", I18N,
                "channels", List.of("in_app"));
        ResponseEntity<String> response = rest.exchange("/v1/admin/notifications/NTF-IT-3",
                HttpMethod.PUT, new HttpEntity<>(body, adminHeaders()), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("notification.frozen",
                objectMapper.readTree(response.getBody()).path("error").path("code").asText());
    }

    @Test
    void sendCreatesDeliveryPerChannelAndFlagsSimulatedOnes() throws Exception {
        createNotification("NTF-IT-4", List.of("in_app", "email"), List.of());
        send("NTF-IT-4");

        ResponseEntity<String> response = rest.exchange(
                "/v1/admin/notifications/NTF-IT-4/deliveries", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode page = objectMapper.readTree(response.getBody());
        JsonNode deliveries = page.path("items");
        assertEquals(2, deliveries.size());
        assertEquals(2, page.path("totalElements").asInt(), "итоги считаются по всей очереди");
        assertEquals(0, page.path("page").asInt());
        for (JsonNode delivery : deliveries) {
            assertEquals("delivered", delivery.path("status").asText());
            assertEquals(1, delivery.path("attempts").asInt());
            // in_app доставляется реально, email — имитация: это обязано быть видно.
            assertEquals(!"in_app".equals(delivery.path("channel").asText()),
                    delivery.path("simulated").asBoolean());
        }
    }

    @Test
    void retryOfDeliveredDeliveryIsRejected() throws Exception {
        createNotification("NTF-IT-5", List.of("in_app"), List.of());
        send("NTF-IT-5");
        ResponseEntity<String> deliveries = rest.exchange(
                "/v1/admin/notifications/NTF-IT-5/deliveries", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);
        String id = objectMapper.readTree(deliveries.getBody()).path("items").get(0).path("id").asText();

        ResponseEntity<String> response = rest.exchange(
                "/v1/admin/notifications/deliveries/" + id + "/retry", HttpMethod.POST,
                new HttpEntity<>(adminHeaders()), String.class);

        // Повторять можно только провалившееся (NTF-06).
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("notification.retry_not_failed",
                objectMapper.readTree(response.getBody()).path("error").path("code").asText());
    }

    @Test
    void templateTextsAreCopiedIntoTheMessageAndSurviveTemplateRemoval() throws Exception {
        Map<String, String> templateI18n =
                Map.of("tg", "Кор", "ru", "Плановые работы", "en", "Maintenance");
        Map<String, Object> template = Map.of(
                "code", "TPL-IT-1",
                "name", "Плановые работы",
                "type", "maintenance",
                "title", templateI18n,
                "body", templateI18n,
                "channels", List.of("in_app"),
                "active", true);
        ResponseEntity<String> created = rest.exchange("/v1/admin/notification-templates",
                HttpMethod.POST, new HttpEntity<>(template, adminHeaders()), String.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());

        ResponseEntity<String> message = rest.exchange("/v1/admin/notifications", HttpMethod.POST,
                new HttpEntity<>(Map.of("code", "NTF-IT-6", "templateCode", "TPL-IT-1"),
                        adminHeaders()), String.class);
        assertEquals(HttpStatus.CREATED, message.getStatusCode());
        JsonNode created6 = objectMapper.readTree(message.getBody());
        assertEquals("maintenance", created6.path("type").asText());
        assertEquals("Плановые работы", created6.path("title").path("ru").asText());

        // Шаблон удалён — история рассылки обязана его пережить (FK нет, тексты скопированы).
        ResponseEntity<String> deleted = rest.exchange("/v1/admin/notification-templates/TPL-IT-1",
                HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), String.class);
        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode());

        send("NTF-IT-6");
        JsonNode fromFeed = feedItem("NTF-IT-6");
        assertEquals("Плановые работы", fromFeed.path("title").path("ru").asText());
        assertEquals("TPL-IT-1", fromFeed.path("templateCode").asText());
    }

    @Test
    void scheduledNotificationCarriesItsTimeAndIsNotSentYet() throws Exception {
        ResponseEntity<String> created = rest.exchange("/v1/admin/notifications", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "code", "NTF-IT-7",
                        "type", "info",
                        "title", I18N,
                        "body", I18N,
                        "channels", List.of("in_app"),
                        "scheduledAt", "2099-01-01T00:00:00Z"), adminHeaders()), String.class);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        JsonNode notification = objectMapper.readTree(created.getBody());
        // chk_notification_message_scheduled: scheduled обязан знать своё время.
        assertEquals("scheduled", notification.path("status").asText());
        assertFalse(notification.path("scheduledAt").asText().isBlank());
        assertFalse(feedCodes(null, null).contains("NTF-IT-7"), "запланированное ещё не отправлено");
    }

    @Test
    void publicFeedNeedsNoAdminKey() throws Exception {
        ResponseEntity<String> response = rest.getForEntity("/v1/notifications", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(objectMapper.readTree(response.getBody()).isArray());
    }

    /**
     * Настоящая проверка отсутствия N+1: не «сервис позвал нужный метод», а
     * счётчик SQL-запросов Hibernate. Лента из трёх рассылок с таргетами обязана
     * стоить столько же запросов, сколько лента из одной — иначе таргеты снова
     * подгружаются лениво, по запросу на строку.
     */
    @Test
    void adminListLoadsTargetsWithoutAQueryPerNotification() throws Exception {
        List<Map<String, String>> targets = List.of(
                Map.of("type", "line", "code", "L1"),
                Map.of("type", "station", "code", "ST-1"));
        createNotification("NTF-IT-N1-A", List.of("in_app"), targets);

        long oneRow = countAdminListQueries();

        createNotification("NTF-IT-N1-B", List.of("in_app"), targets);
        createNotification("NTF-IT-N1-C", List.of("in_app"), targets);

        long threeRows = countAdminListQueries();

        assertEquals(oneRow, threeRows,
                "число запросов обязано не зависеть от длины ленты — иначе это N+1");
    }

    /** Таргеты в ленте консоли обязаны быть развёрнуты, а не потеряны fetch join-ом. */
    @Test
    void adminListStillCarriesEveryTargetOfEachNotification() throws Exception {
        createNotification("NTF-IT-N2", List.of("in_app"), List.of(
                Map.of("type", "line", "code", "L1"),
                Map.of("type", "station", "code", "ST-1")));

        JsonNode row = adminListItem(null, "NTF-IT-N2");

        assertEquals(2, row.path("targets").size());
    }

    @Test
    void adminListWithStatusFilterAlsoCarriesTargets() throws Exception {
        createNotification("NTF-IT-N3", List.of("in_app"),
                List.of(Map.of("type", "line", "code", "L2")));

        JsonNode row = adminListItem("draft", "NTF-IT-N3");

        assertEquals(1, row.path("targets").size());
        assertEquals("L2", row.path("targets").get(0).path("code").asText());
    }

    // --- Пагинация ленты рассылок (NTF-01) -----------------------------------

    /** Контракт страницы ленты — тот же, что у импортов: items + page/size/totals. */
    @Test
    void adminListIsPagedWithImportsContract() throws Exception {
        for (int i = 0; i < 3; i++) {
            createNotification("NTF-IT-LP1-" + i, List.of("in_app"), List.of());
        }

        JsonNode first = adminPage("/v1/admin/notifications?page=0&size=2");

        assertEquals(2, first.path("items").size());
        assertEquals(0, first.path("page").asInt());
        assertEquals(2, first.path("size").asInt());
        assertTrue(first.path("totalElements").asInt() >= 3);
        assertTrue(first.path("totalPages").asInt() >= 2);
    }

    /**
     * Главная проверка: LIMIT уходит в SQL, а не в память.
     *
     * <p>Разница между настоящей пагинацией и фиктивной снаружи невидима — items
     * в обоих случаях правильные. Видна она по числу ГИДРАТИРОВАННЫХ сущностей:
     * при fetch join с Pageable Hibernate снимает LIMIT (HHH90003004), поднимает
     * в сессию ВСЕ рассылки и режет страницу уже в памяти. Значит, счётчик
     * загруженных NotificationMessage при size=2 обязан равняться двум, а не
     * размеру таблицы — это и есть доказательство, что резал SQL.
     */
    @Test
    void adminListPageLoadsOnlyThePageAndNotTheWholeTableIntoMemory() throws Exception {
        List<Map<String, String>> targets = List.of(
                Map.of("type", "line", "code", "L1"),
                Map.of("type", "station", "code", "ST-1"));
        for (int i = 0; i < 5; i++) {
            createNotification("NTF-IT-LP2-" + i, List.of("in_app"), targets);
        }

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        JsonNode page = adminPage("/v1/admin/notifications?page=0&size=2");

        assertEquals(2, page.path("items").size());
        assertTrue(page.path("totalElements").asInt() >= 5, "в таблице заведомо больше страницы");
        assertEquals(2, statistics.getEntityStatistics(NotificationMessage.class.getName())
                        .getLoadCount(),
                "загружена обязана быть только страница: иначе LIMIT снят и резка идёт в памяти");
    }

    /** Таргеты страницы приходят одним запросом: двухшаговая выборка не имеет права стать N+1. */
    @Test
    void adminListPageStillLoadsTargetsWithoutAQueryPerRow() throws Exception {
        List<Map<String, String>> targets = List.of(
                Map.of("type", "line", "code", "L1"),
                Map.of("type", "station", "code", "ST-1"));
        createNotification("NTF-IT-LP3-A", List.of("in_app"), targets);

        long onePerPage = countAdminPageQueries("/v1/admin/notifications?page=0&size=1");
        createNotification("NTF-IT-LP3-B", List.of("in_app"), targets);
        createNotification("NTF-IT-LP3-C", List.of("in_app"), targets);
        long threePerPage = countAdminPageQueries("/v1/admin/notifications?page=0&size=3");

        assertEquals(onePerPage, threePerPage,
                "число запросов обязано не зависеть от размера страницы — иначе это N+1");
    }

    /** Страницы не пересекаются: тай-брейк по code держит порядок при равном created_at. */
    @Test
    void adminListPagesDoNotOverlapDespiteIdenticalCreationTimestamps() throws Exception {
        for (int i = 0; i < 4; i++) {
            createNotification("NTF-IT-LP4-" + i, List.of("in_app"), List.of());
        }

        List<String> first = adminListCodes("/v1/admin/notifications?page=0&size=2");
        List<String> second = adminListCodes("/v1/admin/notifications?page=1&size=2");

        assertEquals(4, java.util.stream.Stream.concat(first.stream(), second.stream())
                .distinct().count(), "рассылка не имеет права попасть в обе страницы");
    }

    /** За последней страницей — пустой items и корректные итоги, а не 404. */
    @Test
    void adminListPageBeyondTheLastOneIsEmptyButOk() throws Exception {
        createNotification("NTF-IT-LP5", List.of("in_app"), List.of());

        JsonNode page = adminPage("/v1/admin/notifications?page=5000&size=10");

        assertTrue(page.path("items").isEmpty());
        assertEquals(5000, page.path("page").asInt());
        assertTrue(page.path("totalElements").asInt() >= 1, "итоги ленты остаются живыми");
    }

    /** Потолок размера страницы обязан держаться и здесь: иначе ?size= вернёт полную выдачу. */
    @Test
    void adminListPageSizeIsClampedToTheCeilingInsteadOfFailing() throws Exception {
        JsonNode page = adminPage("/v1/admin/notifications?page=-1&size=100000");

        assertEquals(0, page.path("page").asInt(), "отрицательная страница зажимается в первую");
        assertEquals(200, page.path("size").asInt(), "размер страницы зажимается потолком");
    }

    /** Фильтр по статусу обязан пагинироваться, а итоги — считаться по отфильтрованной ленте. */
    @Test
    void adminListStatusFilterIsPagedAndCountsOnlyMatchingRows() throws Exception {
        createNotification("NTF-IT-LP6-A", List.of("in_app"), List.of());
        createNotification("NTF-IT-LP6-B", List.of("in_app"), List.of());
        send("NTF-IT-LP6-B");

        JsonNode drafts = adminPage("/v1/admin/notifications?status=draft&page=0&size=200");
        JsonNode sent = adminPage("/v1/admin/notifications?status=sent&page=0&size=200");

        for (JsonNode item : drafts.path("items")) {
            assertEquals("draft", item.path("status").asText());
        }
        assertTrue(adminListCodes("/v1/admin/notifications?status=sent&page=0&size=200")
                .contains("NTF-IT-LP6-B"));
        assertEquals(drafts.path("items").size(), drafts.path("totalElements").asInt(),
                "итоги обязаны считаться по отфильтрованной ленте, а не по всей таблице");
        assertTrue(sent.path("totalElements").asInt() >= 1);
    }

    // --- Пагинация очередей доставки -----------------------------------------

    /** Контракт страницы — тот же, что у ленты импортов: items + page/size/totals. */
    @Test
    void deliveriesOfOneMessageArePagedWithImportsContract() throws Exception {
        createNotification("NTF-IT-P1", List.of("in_app", "email", "sms", "push"), List.of());
        send("NTF-IT-P1");

        JsonNode first = deliveryPage("/v1/admin/notifications/NTF-IT-P1/deliveries?page=0&size=2");
        assertEquals(2, first.path("items").size());
        assertEquals(0, first.path("page").asInt());
        assertEquals(2, first.path("size").asInt());
        assertEquals(4, first.path("totalElements").asInt());
        assertEquals(2, first.path("totalPages").asInt());

        JsonNode second = deliveryPage("/v1/admin/notifications/NTF-IT-P1/deliveries?page=1&size=2");
        assertEquals(2, second.path("items").size());
        assertEquals(1, second.path("page").asInt());
    }

    /** Страницы не пересекаются: тай-брейк по id держит порядок при равном updated_at. */
    @Test
    void deliveryPagesDoNotOverlapDespiteIdenticalTimestamps() throws Exception {
        createNotification("NTF-IT-P2", List.of("in_app", "email", "sms", "push"), List.of());
        send("NTF-IT-P2");

        List<String> first = deliveryIds("/v1/admin/notifications/NTF-IT-P2/deliveries?page=0&size=2");
        List<String> second = deliveryIds("/v1/admin/notifications/NTF-IT-P2/deliveries?page=1&size=2");

        assertEquals(4, java.util.stream.Stream.concat(first.stream(), second.stream())
                .distinct().count(), "строка не имеет права попасть в обе страницы");
    }

    /** За последней страницей — пустой items и корректные итоги, а не 404. */
    @Test
    void deliveryPageBeyondTheLastOneIsEmptyButOk() throws Exception {
        createNotification("NTF-IT-P3", List.of("in_app"), List.of());
        send("NTF-IT-P3");

        JsonNode page = deliveryPage("/v1/admin/notifications/NTF-IT-P3/deliveries?page=50&size=10");

        assertTrue(page.path("items").isEmpty());
        assertEquals(50, page.path("page").asInt());
        assertEquals(1, page.path("totalElements").asInt());
    }

    /**
     * Потолок размера страницы обязан держаться: иначе ?size= возвращает
     * эндпоинт ровно к полной выдаче, ради ухода от которой пагинация и вводилась.
     */
    @Test
    void deliveryPageSizeIsClampedToTheCeilingInsteadOfFailing() throws Exception {
        createNotification("NTF-IT-P4", List.of("in_app"), List.of());
        send("NTF-IT-P4");

        JsonNode page = deliveryPage(
                "/v1/admin/notifications/NTF-IT-P4/deliveries?page=-1&size=100000");

        assertEquals(0, page.path("page").asInt(), "отрицательная страница зажимается в первую");
        assertEquals(200, page.path("size").asInt(), "размер страницы зажимается потолком");
    }

    @Test
    void problemDeliveryQueueIsPagedToo() throws Exception {
        JsonNode page = deliveryPage("/v1/admin/notifications/deliveries/problems?page=0&size=5");

        assertTrue(page.has("items"));
        assertEquals(0, page.path("page").asInt());
        assertEquals(5, page.path("size").asInt());
        assertTrue(page.has("totalElements"));
        assertTrue(page.has("totalPages"));
    }

    // --- Хелперы -------------------------------------------------------------

    /**
     * Сколько SQL-запросов стоит одна выдача ленты консоли. Статистика включена
     * в {@link #datasourceProperties}; счётчик сбрасывается прямо перед вызовом —
     * иначе в него попали бы запросы подготовки данных.
     */
    private long countAdminListQueries() throws Exception {
        return countAdminPageQueries("/v1/admin/notifications?size=200");
    }

    private long countAdminPageQueries(String url) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        adminPage(url);
        return statistics.getPrepareStatementCount();
    }

    private JsonNode adminListItem(String status, String code) throws Exception {
        for (JsonNode item : adminList(status)) {
            if (code.equals(item.path("code").asText())) {
                return item;
            }
        }
        throw new AssertionError("Рассылка " + code + " не найдена в ленте консоли");
    }

    /** Строки ленты. Лента постраничная — берём заведомо широкую страницу. */
    private JsonNode adminList(String status) throws Exception {
        String url = status == null
                ? "/v1/admin/notifications?size=200"
                : "/v1/admin/notifications?size=200&status=" + status;
        return adminPage(url).path("items");
    }

    private JsonNode adminPage(String url) throws Exception {
        ResponseEntity<String> response = rest.exchange(url, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), "не удалось прочитать " + url);
        return objectMapper.readTree(response.getBody());
    }

    private List<String> adminListCodes(String url) throws Exception {
        return java.util.stream.StreamSupport
                .stream(adminPage(url).path("items").spliterator(), false)
                .map(item -> item.path("code").asText())
                .toList();
    }

    private JsonNode deliveryPage(String url) throws Exception {
        ResponseEntity<String> response = rest.exchange(url, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), "не удалось прочитать " + url);
        return objectMapper.readTree(response.getBody());
    }

    private List<String> deliveryIds(String url) throws Exception {
        return java.util.stream.StreamSupport
                .stream(deliveryPage(url).path("items").spliterator(), false)
                .map(item -> item.path("id").asText())
                .toList();
    }

    private void createNotification(String code, List<String> channels,
                                    List<Map<String, String>> targets) {
        Map<String, Object> body = Map.of(
                "code", code,
                "type", "info",
                "title", I18N,
                "body", I18N,
                "channels", channels,
                "targets", targets);
        ResponseEntity<String> response = rest.exchange("/v1/admin/notifications", HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()), String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(), "не удалось создать " + code);
    }

    private void send(String code) {
        ResponseEntity<String> response = rest.exchange("/v1/admin/notifications/" + code + "/send",
                HttpMethod.POST, new HttpEntity<>(adminHeaders()), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), "не удалось отправить " + code);
    }

    private JsonNode feedItem(String code) throws Exception {
        for (JsonNode item : feed(null, null)) {
            if (code.equals(item.path("code").asText())) {
                return item;
            }
        }
        throw new AssertionError("Рассылка " + code + " не найдена в фиде");
    }

    private List<String> feedCodes(String lineCode, String stationCode) throws Exception {
        return java.util.stream.StreamSupport.stream(feed(lineCode, stationCode).spliterator(), false)
                .map(item -> item.path("code").asText())
                .toList();
    }

    private JsonNode feed(String lineCode, String stationCode) throws Exception {
        StringBuilder url = new StringBuilder("/v1/notifications?");
        if (lineCode != null) {
            url.append("lineCode=").append(lineCode).append('&');
        }
        if (stationCode != null) {
            url.append("stationCode=").append(stationCode);
        }
        ResponseEntity<String> response = rest.getForEntity(url.toString(), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return objectMapper.readTree(response.getBody());
    }

    private static HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Admin-Key", ADMIN_KEY);
        headers.set("X-Admin-Actor", ACTOR);
        return headers;
    }
}
