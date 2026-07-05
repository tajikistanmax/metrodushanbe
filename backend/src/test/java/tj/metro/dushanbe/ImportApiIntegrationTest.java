package tj.metro.dushanbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.StreamSupport;
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
 * Интеграционный тест конвейера импорта сети поверх реального PostGIS (Testcontainers):
 * применяются миграции Flyway (включая V010 — схема import_job/import_error) и
 * проверяется admin-контур /v1/admin/imports: dev-авторизация (401 без ключа),
 * применение валидного мини-GeoJSON (сущности видны в публичной выдаче) и построчная
 * фиксация ошибок для битого входа.
 *
 * <p>Пути — без префикса /api: TestRestTemplate сам добавляет context-path.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ImportApiIntegrationTest {

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

    private static final String VALID_GEOJSON = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"feature_type":"line","code":"IMP-L1",
                "name":{"tg":"Хати импорт","ru":"Импорт-линия","en":"Import Line"},
                "color_hex":"#0055AA","status":"planned","sort_order":42},
                "geometry":{"type":"LineString","coordinates":[[68.80,38.52],[68.79,38.56]]}},
              {"type":"Feature","properties":{"feature_type":"station","code":"IMP-ST-1",
                "name":{"tg":"Ист1","ru":"Импорт1","en":"Import1"},"status":"planned",
                "lines":["IMP-L1"],"is_transfer":false,"accessibility":["elevator"]},
                "geometry":{"type":"Point","coordinates":[68.80,38.52]}},
              {"type":"Feature","properties":{"feature_type":"station","code":"IMP-ST-2",
                "name":{"tg":"Ист2","ru":"Импорт2","en":"Import2"},"status":"planned",
                "lines":["IMP-L1"]},
                "geometry":{"type":"Point","coordinates":[68.79,38.56]}}]}""";

    @Test
    void importWithoutKeyReturnsEnvelope401() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.exchange("/v1/admin/imports", HttpMethod.POST,
                new HttpEntity<>(VALID_GEOJSON, headers), String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        JsonNode envelope = objectMapper.readTree(response.getBody());
        assertEquals("admin.unauthorized", envelope.path("error").path("code").asText());
    }

    @Test
    void validImportAppliesEntitiesAndTheyAreVisiblePublicly() throws Exception {
        ResponseEntity<String> posted = rest.exchange("/v1/admin/imports", HttpMethod.POST,
                new HttpEntity<>(VALID_GEOJSON, adminHeaders()), String.class);

        assertEquals(HttpStatus.OK, posted.getStatusCode());
        JsonNode summary = objectMapper.readTree(posted.getBody());
        assertEquals("success", summary.path("status").asText());
        assertEquals(3, summary.path("featureCount").asInt());
        assertEquals(3, summary.path("createdCount").asInt());
        assertEquals(0, summary.path("failedCount").asInt());
        assertEquals("network_geojson", summary.path("type").asText());
        String jobId = summary.path("id").asText();

        // GET /{id} — та же сводка
        ResponseEntity<String> job = rest.exchange("/v1/admin/imports/" + jobId, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);
        assertEquals(HttpStatus.OK, job.getStatusCode());
        assertEquals("success", objectMapper.readTree(job.getBody()).path("status").asText());

        // GET /{id}/errors — пусто для чистого импорта
        ResponseEntity<String> errors = rest.exchange("/v1/admin/imports/" + jobId + "/errors", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);
        assertEquals(HttpStatus.OK, errors.getStatusCode());
        assertTrue(objectMapper.readTree(errors.getBody()).isEmpty(), "у чистого импорта не должно быть ошибок");

        // GET список — джоб виден в ленте
        ResponseEntity<String> list = rest.exchange("/v1/admin/imports?size=100", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        JsonNode items = objectMapper.readTree(list.getBody()).path("items");
        assertTrue(StreamSupport.stream(items.spliterator(), false)
                .anyMatch(i -> jobId.equals(i.path("id").asText())), "джоб должен быть в ленте");

        // импортированная линия видна публично
        ResponseEntity<String> lines = rest.getForEntity("/v1/lines", String.class);
        assertTrue(codes(lines.getBody()).contains("IMP-L1"), "импортированная линия видна в GET /v1/lines");

        // станции видны и привязаны к линии (связь станция-линия создана импортом)
        ResponseEntity<String> stations = rest.getForEntity("/v1/stations?lineCode=IMP-L1", String.class);
        assertEquals(HttpStatus.OK, stations.getStatusCode());
        JsonNode stationArr = objectMapper.readTree(stations.getBody());
        assertEquals(2, stationArr.size(), "к IMP-L1 привязаны две импортированные станции");
        assertTrue(codes(stations.getBody()).containsAll(java.util.List.of("IMP-ST-1", "IMP-ST-2")));
    }

    @Test
    void reimportIsIdempotentByCode() throws Exception {
        rest.exchange("/v1/admin/imports", HttpMethod.POST,
                new HttpEntity<>(VALID_GEOJSON, adminHeaders()), String.class);
        // повторный импорт того же тела — те же коды апсертятся (updated), новые сущности не плодятся
        ResponseEntity<String> second = rest.exchange("/v1/admin/imports", HttpMethod.POST,
                new HttpEntity<>(VALID_GEOJSON, adminHeaders()), String.class);
        assertEquals(HttpStatus.OK, second.getStatusCode());
        JsonNode summary = objectMapper.readTree(second.getBody());
        assertEquals("success", summary.path("status").asText());
        assertEquals(3, summary.path("updatedCount").asInt(), "повторный импорт обновляет, а не создаёт");
        assertEquals(0, summary.path("createdCount").asInt());

        // станций у линии по-прежнему две (перепривязка идемпотентна)
        ResponseEntity<String> stations = rest.getForEntity("/v1/stations?lineCode=IMP-L1", String.class);
        assertEquals(2, objectMapper.readTree(stations.getBody()).size());
    }

    @Test
    void brokenFeatureIsRecordedAsError() throws Exception {
        String broken = """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":{"feature_type":"station","code":"IMP-BAD",
                    "name":{"tg":"Плох","ru":"Плохо"},"status":"nonsense"},
                    "geometry":{"type":"LineString","coordinates":[]}}]}""";

        ResponseEntity<String> posted = rest.exchange("/v1/admin/imports", HttpMethod.POST,
                new HttpEntity<>(broken, adminHeaders()), String.class);
        assertEquals(HttpStatus.OK, posted.getStatusCode());
        JsonNode summary = objectMapper.readTree(posted.getBody());
        assertEquals("failed", summary.path("status").asText());
        assertEquals(1, summary.path("failedCount").asInt());
        assertEquals(0, summary.path("createdCount").asInt());
        String jobId = summary.path("id").asText();

        ResponseEntity<String> errors = rest.exchange("/v1/admin/imports/" + jobId + "/errors", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);
        JsonNode errorArr = objectMapper.readTree(errors.getBody());
        assertTrue(errorArr.size() >= 1, "должна быть хотя бы одна построчная ошибка");
        assertEquals("IMP-BAD", errorArr.get(0).path("featureRef").asText());
        assertEquals("error", errorArr.get(0).path("severity").asText());

        // битая станция не попала в публичную выдачу
        ResponseEntity<String> stations = rest.getForEntity("/v1/stations", String.class);
        assertTrue(codes(stations.getBody()).stream().noneMatch("IMP-BAD"::equals),
                "битая станция не должна быть создана");
    }

    private java.util.List<String> codes(String body) throws Exception {
        JsonNode arr = objectMapper.readTree(body);
        return StreamSupport.stream(arr.spliterator(), false)
                .map(n -> n.path("code").asText())
                .toList();
    }

    private static HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Admin-Key", ADMIN_KEY);
        headers.set("X-Admin-Actor", "it-admin");
        return headers;
    }
}
