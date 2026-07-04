package tj.metro.dushanbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * Интеграционный тест API поверх реального PostGIS (Testcontainers):
 * поднимает контейнер postgis/postgis:16-3.4, применяет миграции Flyway
 * (V001 — схема, V002 — демо-сиды) и проверяет контракт API.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NetworkApiIntegrationTest {

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

    @Test
    void geojsonReturnsSeededNetwork() {
        // TestRestTemplate сам добавляет context-path /api к базовому URL — пути без него
        ResponseEntity<String> response = rest.getForEntity("/v1/network/geojson", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("\"FeatureCollection\""), "должен вернуться FeatureCollection");
        assertTrue(body.contains("\"ST-HUB-CENTER\""), "должна присутствовать пересадочная станция");
        assertTrue(body.contains("\"#E21B2D\""), "должен присутствовать цвет линии L1");
    }

    @Test
    void linesEndpointReturnsTwoSeededLines() {
        ResponseEntity<String> response = rest.getForEntity("/v1/lines", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("\"L1\"") && body.contains("\"L2\""), "должны вернуться обе демо-линии");
    }

    @Test
    void unknownStationReturnsEnvelope404() {
        ResponseEntity<String> response = rest.getForEntity("/v1/stations/ST-NOPE", String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("station.not_found"), "ошибка должна быть в едином envelope с кодом station.not_found");
        assertTrue(body.contains("requestId"), "envelope должен содержать requestId");
    }
}
