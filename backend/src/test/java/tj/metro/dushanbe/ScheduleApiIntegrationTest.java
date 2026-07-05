package tj.metro.dushanbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Интеграционный тест API расписания поверх реального PostGIS (Testcontainers):
 * применяются миграции Flyway (V011 — схема line_schedule, V012 — демо-графики)
 * и проверяется публичный контракт GET /v1/lines/{code}/schedule и
 * GET /v1/stations/{code}/arrivals на демо-данных.
 *
 * <p>Clock зафиксирован (понедельник 2026-07-06 12:00 UTC, будни, середина часов
 * работы) — чтобы оценочные прибытия были детерминированными вне зависимости от
 * времени запуска теста.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScheduleApiIntegrationTest {

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-07-06T12:00:00Z"), ZoneOffset.UTC);
        }
    }

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
    void lineScheduleReturnsDemoWeekdaySchedule() throws Exception {
        // TestRestTemplate сам добавляет context-path /api к базовому URL — пути без него
        ResponseEntity<String> response = rest.getForEntity("/v1/lines/L1/schedule?dayType=weekday", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode dto = objectMapper.readTree(response.getBody());
        assertEquals("L1", dto.path("lineCode").asText());
        assertEquals("weekday", dto.path("dayType").asText());
        assertEquals("06:00", dto.path("firstDeparture").asText());
        assertEquals("23:00", dto.path("lastDeparture").asText());
        assertEquals(5, dto.path("headwayMinutes").asInt());
        assertTrue(dto.path("effectiveTo").isNull(), "бессрочный график — effectiveTo null");
    }

    @Test
    void lineScheduleDerivesDayTypeFromClock() throws Exception {
        // Clock зафиксирован на понедельник => weekday выводится без параметра
        ResponseEntity<String> response = rest.getForEntity("/v1/lines/L1/schedule", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode dto = objectMapper.readTree(response.getBody());
        assertEquals("weekday", dto.path("dayType").asText());
        assertEquals(5, dto.path("headwayMinutes").asInt());
    }

    @Test
    void lineScheduleUnknownLineIs404() throws Exception {
        ResponseEntity<String> response = rest.getForEntity("/v1/lines/NOPE/schedule?dayType=weekday", String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        JsonNode envelope = objectMapper.readTree(response.getBody());
        assertEquals("line.not_found", envelope.path("error").path("code").asText());
    }

    @Test
    void lineScheduleMissingForDayIs404() throws Exception {
        // L2 в демо-данных не имеет holiday-графика
        ResponseEntity<String> response = rest.getForEntity("/v1/lines/L2/schedule?dayType=holiday", String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        JsonNode envelope = objectMapper.readTree(response.getBody());
        assertEquals("schedule.not_found", envelope.path("error").path("code").asText());
    }

    @Test
    void lineScheduleInvalidDayTypeReturnsEnvelope400() throws Exception {
        ResponseEntity<String> response = rest.getForEntity("/v1/lines/L1/schedule?dayType=bogus", String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        JsonNode envelope = objectMapper.readTree(response.getBody());
        assertTrue(envelope.hasNonNull("timestamp"), "envelope должен содержать timestamp");
        assertTrue(envelope.hasNonNull("requestId"), "envelope должен содержать requestId");
        assertEquals("schedule.day_type_invalid", envelope.path("error").path("code").asText());
    }

    @Test
    void arrivalsInServiceHoursAreEstimatedAndSpacedByHeadway() throws Exception {
        // Clock 12:00 в будни -> сервис активен; L1 headway 5, сетка попадает ровно на 12:00
        ResponseEntity<String> response =
                rest.getForEntity("/v1/stations/ST-L1-01/arrivals?lineCode=L1&dayType=weekday&limit=4", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode dto = objectMapper.readTree(response.getBody());
        assertEquals("ST-L1-01", dto.path("stationCode").asText());
        assertEquals("L1", dto.path("lineCode").asText());
        assertTrue(dto.path("estimated").asBoolean(), "прибытия оценочные (headway-based)");
        assertTrue(dto.path("serviceActive").asBoolean(), "12:00 будни — в часах работы");
        assertEquals(5, dto.path("headwayMinutes").asInt());

        JsonNode arrivals = dto.path("arrivals");
        assertTrue(arrivals.isArray() && arrivals.size() == 4, "должно вернуться N=4 прибытия");
        assertEquals("12:00", arrivals.get(0).path("time").asText());
        assertEquals(0, arrivals.get(0).path("etaMinutes").asInt());
        // шаг между соседними прибытиями равен headway
        int prev = -5;
        for (JsonNode a : arrivals) {
            assertEquals(prev + 5, a.path("etaMinutes").asInt(), "eta растёт с шагом headway");
            prev = a.path("etaMinutes").asInt();
        }
    }

    @Test
    void arrivalsMissingLineCodeReturnsEnvelope400() throws Exception {
        ResponseEntity<String> response = rest.getForEntity("/v1/stations/ST-L1-01/arrivals", String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        JsonNode envelope = objectMapper.readTree(response.getBody());
        assertEquals("schedule.line_code_required", envelope.path("error").path("code").asText());
    }

    @Test
    void arrivalsStationNotOnLineReturns400() throws Exception {
        // ST-L1-01 принадлежит L1, но не L2
        ResponseEntity<String> response =
                rest.getForEntity("/v1/stations/ST-L1-01/arrivals?lineCode=L2", String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        JsonNode envelope = objectMapper.readTree(response.getBody());
        assertEquals("schedule.station_not_on_line", envelope.path("error").path("code").asText());
    }

    @Test
    void arrivalsUnknownStationIs404() throws Exception {
        ResponseEntity<String> response =
                rest.getForEntity("/v1/stations/NOPE/arrivals?lineCode=L1", String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        JsonNode envelope = objectMapper.readTree(response.getBody());
        assertEquals("station.not_found", envelope.path("error").path("code").asText());
        // sanity: тот же контейнер, невозможные часы обработаны корректно
        assertFalse(envelope.path("error").path("message").asText().isBlank());
    }
}
