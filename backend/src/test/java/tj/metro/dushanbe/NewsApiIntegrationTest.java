package tj.metro.dushanbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Интеграционный тест публичного API новостей поверх реального PostGIS
 * (Testcontainers): применяются миграции Flyway (V005 — схема news_article,
 * V006 — демо-сиды) и проверяется контракт GET /v1/news и GET /v1/news/{slug}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NewsApiIntegrationTest {

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
    void newsReturnsOnlyPublishedNewestFirst() throws Exception {
        // TestRestTemplate сам добавляет context-path /api к базовому URL — путь без него
        ResponseEntity<String> response = rest.getForEntity("/v1/news", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode news = objectMapper.readTree(response.getBody());
        assertTrue(news.isArray(), "ответ должен быть JSON-массивом");
        // из четырёх сидов V006 отдаются только три published; черновик исключён
        assertEquals(3, news.size(), "должны вернуться ровно три опубликованные новости");

        // порядок — published_at по убыванию: (a) новее (b) новее (c)
        assertEquals("metro-construction-launch", news.get(0).path("slug").asText());
        assertEquals("line-1-tunnel-progress", news.get(1).path("slug").asText());
        assertEquals("accessibility-standards", news.get(2).path("slug").asText());

        JsonNode first = news.get(0);
        for (String lang : List.of("tg", "ru", "en")) {
            assertTrue(first.path("title").hasNonNull(lang), "title должен содержать язык " + lang);
            assertTrue(first.path("body").hasNonNull(lang), "body должен содержать язык " + lang);
        }
        assertTrue(first.path("publishedAt").asText().endsWith("Z"), "publishedAt — ISO-8601 UTC");
    }

    @Test
    void newsBySlugReturnsPublishedArticle() throws Exception {
        ResponseEntity<String> response = rest.getForEntity("/v1/news/accessibility-standards", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode article = objectMapper.readTree(response.getBody());
        assertEquals("accessibility-standards", article.path("slug").asText());
        assertTrue(article.path("title").hasNonNull("ru"));
    }

    @Test
    void draftSlugReturnsEnvelope404() throws Exception {
        // winter-schedule-draft — статус draft, публичным API не отдаётся
        ResponseEntity<String> response = rest.getForEntity("/v1/news/winter-schedule-draft", String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        JsonNode envelope = objectMapper.readTree(response.getBody());
        assertTrue(envelope.hasNonNull("requestId"), "envelope должен содержать requestId");
        assertEquals("news.not_found", envelope.path("error").path("code").asText());
    }
}
