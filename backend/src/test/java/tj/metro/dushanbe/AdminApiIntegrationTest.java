package tj.metro.dushanbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
 * Интеграционный тест admin-write контура поверх реального PostGIS (Testcontainers):
 * проверяет dev-авторизацию (401 без ключа X-Admin-Key в едином envelope),
 * запись изменения в аудит (создание линии) и жизненный цикл публикации уведомления
 * (draft → published → появление в публичной выдаче GET /v1/alerts).
 *
 * <p>Пути — без префикса /api: TestRestTemplate сам добавляет context-path.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminApiIntegrationTest {

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
    void adminEndpointWithoutKeyReturnsEnvelope401() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "code", "L-NOKEY",
                "name", Map.of("tg", "Хат", "ru", "Линия", "en", "Line"),
                "colorHex", "#123456",
                "status", "planned");

        ResponseEntity<String> response = rest.exchange("/v1/admin/lines", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        JsonNode envelope = objectMapper.readTree(response.getBody());
        assertTrue(envelope.hasNonNull("requestId"), "envelope должен содержать requestId");
        assertEquals("admin.unauthorized", envelope.path("error").path("code").asText());
    }

    @Test
    void createLineWithKeyPersistsAndRecordsAudit() throws Exception {
        Map<String, Object> body = Map.of(
                "code", "L-ADM-1",
                "name", Map.of("tg", "Хати нав", "ru", "Новая линия", "en", "New Line"),
                "colorHex", "#0055AA",
                "status", "planned",
                "sortOrder", 9);

        ResponseEntity<String> created = rest.exchange("/v1/admin/lines", HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()), String.class);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        JsonNode line = objectMapper.readTree(created.getBody());
        assertEquals("L-ADM-1", line.path("code").asText());
        assertEquals("Новая линия", line.path("name").path("ru").asText());

        // аудит: запись line.create по этой линии видна в журнале
        ResponseEntity<String> audit = rest.exchange("/v1/admin/audit?size=100", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);
        assertEquals(HttpStatus.OK, audit.getStatusCode());
        JsonNode items = objectMapper.readTree(audit.getBody()).path("items");
        boolean found = false;
        for (JsonNode item : items) {
            if ("line.create".equals(item.path("action").asText())
                    && "L-ADM-1".equals(item.path("entityId").asText())) {
                found = true;
                assertEquals("line", item.path("entityType").asText());
                assertTrue(item.path("before").isNull(), "before для создания — null");
                assertEquals("planned", item.path("after").path("status").asText());
            }
        }
        assertTrue(found, "в журнале аудита должно быть событие line.create для L-ADM-1");
    }

    @Test
    void alertDraftPublishAppearsInPublicFeed() throws Exception {
        Map<String, String> i18n = Map.of("tg", "Огоҳӣ", "ru", "Уведомление", "en", "Notice");
        Map<String, Object> body = Map.of(
                "code", "ALERT-ADM-1",
                "severity", "info",
                "title", i18n,
                "body", i18n,
                "startsAt", "2020-01-01T00:00:00Z");

        // 1) создать черновик
        ResponseEntity<String> created = rest.exchange("/v1/admin/alerts", HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()), String.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());

        // до публикации в публичной выдаче его нет
        assertTrue(publicAlertCodes().stream().noneMatch("ALERT-ADM-1"::equals),
                "черновик не должен попадать в публичную выдачу");

        // 2) опубликовать
        ResponseEntity<String> published = rest.exchange("/v1/admin/alerts/ALERT-ADM-1/publish",
                HttpMethod.POST, new HttpEntity<>(adminHeaders()), String.class);
        assertEquals(HttpStatus.OK, published.getStatusCode());

        // 3) теперь уведомление активно и видно публично (окно открыто: startsAt в прошлом, endsAt null)
        assertTrue(publicAlertCodes().contains("ALERT-ADM-1"),
                "опубликованное активное уведомление должно попасть в GET /v1/alerts");
    }

    private List<String> publicAlertCodes() throws Exception {
        ResponseEntity<String> response = rest.getForEntity("/v1/alerts", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode alerts = objectMapper.readTree(response.getBody());
        return java.util.stream.StreamSupport.stream(alerts.spliterator(), false)
                .map(a -> a.path("code").asText())
                .toList();
    }

    private static HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Admin-Key", ADMIN_KEY);
        headers.set("X-Admin-Actor", "admin");
        headers.set("X-Admin-Actor", "it-admin");
        return headers;
    }
}
