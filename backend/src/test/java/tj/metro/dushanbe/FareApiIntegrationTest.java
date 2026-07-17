package tj.metro.dushanbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

/** Проверяет V018 и публичный контракт тарифов на реальном PostgreSQL/PostGIS. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FareApiIntegrationTest {

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
    void publicFaresReturnsOnlyActiveProductsWithFullI18n() throws Exception {
        ResponseEntity<String> response = rest.getForEntity("/v1/fares", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode fares = objectMapper.readTree(response.getBody());
        assertEquals(2, fares.size());
        assertEquals("DEMO-SINGLE", fares.get(0).path("code").asText());
        assertEquals(3.0, fares.get(0).path("amount").asDouble());
        assertEquals("TJS", fares.get(0).path("currency").asText());
        assertTrue(fares.get(0).path("active").asBoolean());
        for (String language : List.of("tg", "ru", "en")) {
            assertFalse(fares.get(0).path("name").path(language).asText().isBlank());
            assertFalse(fares.get(0).path("description").path(language).asText().isBlank());
        }
    }
}
