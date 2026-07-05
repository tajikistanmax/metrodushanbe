package tj.metro.dushanbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * Интеграционный тест видимости soft-delete (BR-NET-2, ТЗ §6.2.2): после soft-delete
 * сущности через admin-контур публичные read-эндпоинты перестают её отдавать —
 * список без неё, карточка по коду → 404, GeoJSON без неё. Проверяется на реальном
 * PostGIS (Testcontainers) поверх демо-данных V002.
 *
 * <p>Пути — без префикса /api: TestRestTemplate сам добавляет context-path.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SoftDeleteVisibilityIntegrationTest {

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

    @Test
    void softDeletedStationDisappearsFromPublicReads() {
        // демо-станция L1 присутствует в публичной выдаче до удаления
        String station = "ST-L1-08";
        assertTrue(publicStationsBody().contains('"' + station + '"'),
                "до soft-delete станция должна быть в списке");
        assertEquals(HttpStatus.OK, rest.getForEntity("/v1/stations/" + station, String.class).getStatusCode());

        // soft-delete через admin-контур (BR-NET-2)
        ResponseEntity<Void> deleted = rest.exchange("/v1/admin/stations/" + station,
                HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), Void.class);
        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode());

        // список станций больше её не содержит
        assertFalse(publicStationsBody().contains('"' + station + '"'),
                "после soft-delete станция не должна отображаться в списке");

        // карточка станции → 404 station.not_found (как для несуществующей)
        ResponseEntity<String> byCode = rest.getForEntity("/v1/stations/" + station, String.class);
        assertEquals(HttpStatus.NOT_FOUND, byCode.getStatusCode());
        assertNotNull(byCode.getBody());
        assertTrue(byCode.getBody().contains("station.not_found"),
                "удалённая станция должна отдавать station.not_found");

        // GeoJSON сети также не содержит удалённую станцию
        ResponseEntity<String> geojson = rest.getForEntity("/v1/network/geojson", String.class);
        assertEquals(HttpStatus.OK, geojson.getStatusCode());
        assertNotNull(geojson.getBody());
        assertFalse(geojson.getBody().contains('"' + station + '"'),
                "удалённая станция не должна попадать в GeoJSON");
    }

    @Test
    void softDeletedLineDisappearsFromPublicReads() {
        // демо-линия L2 присутствует до удаления
        String line = "L2";
        assertTrue(publicLinesBody().contains('"' + line + '"'), "до soft-delete линия должна быть в списке");

        ResponseEntity<Void> deleted = rest.exchange("/v1/admin/lines/" + line,
                HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), Void.class);
        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode());

        // список линий больше её не содержит; карточка линии → 404
        assertFalse(publicLinesBody().contains('"' + line + '"'),
                "после soft-delete линия не должна отображаться в списке");
        ResponseEntity<String> byCode = rest.getForEntity("/v1/lines/" + line, String.class);
        assertEquals(HttpStatus.NOT_FOUND, byCode.getStatusCode());
        assertTrue(byCode.getBody() != null && byCode.getBody().contains("line.not_found"),
                "удалённая линия должна отдавать line.not_found");

        // фильтр станций по удалённой линии → 404 line.not_found
        ResponseEntity<String> stationsByLine = rest.getForEntity("/v1/stations?lineCode=" + line, String.class);
        assertEquals(HttpStatus.NOT_FOUND, stationsByLine.getStatusCode());
    }

    private String publicStationsBody() {
        ResponseEntity<String> response = rest.getForEntity("/v1/stations", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private String publicLinesBody() {
        ResponseEntity<String> response = rest.getForEntity("/v1/lines", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private static HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Admin-Key", ADMIN_KEY);
        headers.set("X-Admin-Actor", "it-admin");
        return headers;
    }
}
