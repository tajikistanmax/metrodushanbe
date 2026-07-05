package tj.metro.dushanbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Интеграционный тест API сервисных уведомлений поверх реального PostGIS
 * (Testcontainers): применяются миграции Flyway (V003 — схема alerts,
 * V004 — демо-сиды) и проверяется публичный контракт GET /v1/alerts.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AlertApiIntegrationTest {

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
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void alertsReturnsOnlyActivePublishedInSeverityOrder() throws Exception {
        // TestRestTemplate сам добавляет context-path /api к базовому URL — пути без него
        ResponseEntity<String> response = rest.getForEntity("/v1/alerts", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode alerts = objectMapper.readTree(response.getBody());
        assertTrue(alerts.isArray(), "ответ должен быть JSON-массивом");
        // из пяти сидов V004 активны (a), (b) и (e): draft и истёкший не отдаются
        assertEquals(3, alerts.size(), "должны вернуться ровно три активных published-уведомления");

        // порядок: warning раньше info; внутри info — starts_at по убыванию: (e) новее (b)
        JsonNode warning = alerts.get(0);
        assertEquals("ALERT-2026-001", warning.path("code").asText());
        assertEquals("warning", warning.path("severity").asText());
        for (String lang : List.of("tg", "ru", "en")) {
            assertTrue(warning.path("title").hasNonNull(lang), "title должен содержать язык " + lang);
            assertTrue(warning.path("body").hasNonNull(lang), "body должен содержать язык " + lang);
        }
        assertEquals(2, warning.path("targets").size(), "у (a) два таргета: линия L2 и её станция");
        assertNotNull(warning.path("startsAt").asText());
        assertTrue(warning.path("endsAt").asText().endsWith("Z"), "endsAt — ISO-8601 UTC");

        JsonNode lineOnly = alerts.get(1);
        assertEquals("ALERT-2026-005", lineOnly.path("code").asText());
        assertEquals("info", lineOnly.path("severity").asText());
        assertEquals(1, lineOnly.path("targets").size(), "у (e) единственный таргет — линия L1");

        JsonNode info = alerts.get(2);
        assertEquals("ALERT-2026-002", info.path("code").asText());
        assertEquals("info", info.path("severity").asText());
        assertTrue(info.path("targets").isEmpty(), "network-wide уведомление — без таргетов");
        assertTrue(info.path("endsAt").isNull(), "бессрочное уведомление — endsAt null");
    }

    @Test
    void lineCodeFilterIncludesTargetedAndNetworkWide() throws Exception {
        ResponseEntity<String> l2 = rest.getForEntity("/v1/alerts?lineCode=L2", String.class);
        assertEquals(HttpStatus.OK, l2.getStatusCode());
        JsonNode l2Alerts = objectMapper.readTree(l2.getBody());
        assertEquals(2, l2Alerts.size(), "для L2 — таргетированный (a) + network-wide");
        assertEquals("ALERT-2026-001", l2Alerts.get(0).path("code").asText());
        assertEquals("ALERT-2026-002", l2Alerts.get(1).path("code").asText());

        ResponseEntity<String> l1 = rest.getForEntity("/v1/alerts?lineCode=L1", String.class);
        assertEquals(HttpStatus.OK, l1.getStatusCode());
        JsonNode l1Alerts = objectMapper.readTree(l1.getBody());
        assertEquals(2, l1Alerts.size(), "для L1 — таргетированный (e) + network-wide");
        assertEquals("ALERT-2026-005", l1Alerts.get(0).path("code").asText());
        assertEquals("ALERT-2026-002", l1Alerts.get(1).path("code").asText());
    }

    @Test
    void stationCodeFilterIncludesAlertsTargetedAtStationsLines() throws Exception {
        // ST-L2-04 принадлежит L2 и НЕ таргетирована напрямую ни одним alert:
        // (a) попадает через таргет line=L2 (связь станция-линия), (e) с line=L1 — нет
        ResponseEntity<String> l2Station = rest.getForEntity("/v1/alerts?stationCode=ST-L2-04", String.class);
        assertEquals(HttpStatus.OK, l2Station.getStatusCode());
        JsonNode l2StationAlerts = objectMapper.readTree(l2Station.getBody());
        assertEquals(2, l2StationAlerts.size(), "для станции L2 — alert линии L2 + network-wide");
        assertEquals("ALERT-2026-001", l2StationAlerts.get(0).path("code").asText());
        assertEquals("ALERT-2026-002", l2StationAlerts.get(1).path("code").asText());

        // станция линии L1: (e) таргетирован ТОЛЬКО линией L1 и должен попасть в выдачу
        ResponseEntity<String> l1Station = rest.getForEntity("/v1/alerts?stationCode=ST-L1-03", String.class);
        assertEquals(HttpStatus.OK, l1Station.getStatusCode());
        JsonNode l1StationAlerts = objectMapper.readTree(l1Station.getBody());
        assertEquals(2, l1StationAlerts.size(), "для станции L1 — alert линии L1 + network-wide");
        assertEquals("ALERT-2026-005", l1StationAlerts.get(0).path("code").asText());
        assertEquals("ALERT-2026-002", l1StationAlerts.get(1).path("code").asText());
    }

    @Test
    void combinedLineAndStationFilterIsUnion() throws Exception {
        // объединение фильтров: lineCode=L1 даёт (e), stationCode=ST-L2-02 даёт (a),
        // network-wide (b) попадает всегда
        ResponseEntity<String> response =
                rest.getForEntity("/v1/alerts?lineCode=L1&stationCode=ST-L2-02", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode alerts = objectMapper.readTree(response.getBody());
        assertEquals(3, alerts.size(), "объединение фильтров — (a) + (e) + network-wide");
        assertEquals("ALERT-2026-001", alerts.get(0).path("code").asText());
        assertEquals("ALERT-2026-005", alerts.get(1).path("code").asText());
        assertEquals("ALERT-2026-002", alerts.get(2).path("code").asText());
    }

    @Test
    void invalidSeverityReturnsEnvelope400() throws Exception {
        ResponseEntity<String> response = rest.getForEntity("/v1/alerts?severity=bogus", String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        JsonNode envelope = objectMapper.readTree(response.getBody());
        assertTrue(envelope.hasNonNull("timestamp"), "envelope должен содержать timestamp");
        assertTrue(envelope.hasNonNull("requestId"), "envelope должен содержать requestId");
        assertEquals("alert.severity_invalid", envelope.path("error").path("code").asText());
        assertTrue(envelope.path("error").hasNonNull("message"));
        assertTrue(envelope.path("error").hasNonNull("details"));
    }
}
