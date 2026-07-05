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
 * Интеграционный тест API маршрутизации поверх реального PostGIS (Testcontainers):
 * поднимает контейнер, применяет миграции Flyway (демо-сеть V002) и проверяет
 * контракт GET /api/v1/routes для пары станций и обработку неизвестного кода.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RouteApiIntegrationTest {

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
    void routeAcrossLinesReturnsTransferViaHub() {
        // TestRestTemplate сам добавляет context-path /api к базовому URL — пути без него
        ResponseEntity<String> response = rest.getForEntity(
                "/v1/routes?from=ST-L1-01&to=ST-L2-06", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("\"found\":true"), "маршрут должен быть построен");
        assertTrue(body.contains("\"transfers\":1"), "маршрут через ST-HUB-CENTER даёт одну пересадку");
        assertTrue(body.contains("\"estimatedMinutes\""), "должна присутствовать оценка времени");
        assertTrue(body.contains("\"ST-HUB-CENTER\""), "маршрут должен проходить через пересадочный узел");
        assertTrue(body.contains("\"L1\"") && body.contains("\"L2\""),
                "маршрут должен содержать участки по обеим линиям");
    }

    @Test
    void routeWithinLineHasNoTransfers() {
        ResponseEntity<String> response = rest.getForEntity(
                "/v1/routes?from=ST-L1-01&to=ST-L1-04", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("\"found\":true"), "маршрут должен быть построен");
        assertTrue(body.contains("\"transfers\":0"), "в пределах одной линии пересадок нет");
    }

    @Test
    void unknownStationReturnsEnvelope404() {
        ResponseEntity<String> response = rest.getForEntity(
                "/v1/routes?from=ST-NOPE&to=ST-L2-06", String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("route.station_not_found"),
                "ошибка должна быть в едином envelope с кодом route.station_not_found");
        assertTrue(body.contains("requestId"), "envelope должен содержать requestId");
    }
}
