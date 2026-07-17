package tj.metro.dushanbe.content.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tj.metro.dushanbe.identity.repository.AdminUserRepository;
import tj.metro.dushanbe.admin.security.AdminAuthProperties;
import tj.metro.dushanbe.config.RateLimitProperties;
import tj.metro.dushanbe.content.service.ContentService;
import tj.metro.dushanbe.content.web.dto.NewsArticleDto;

@WebMvcTest(NewsController.class)
@AutoConfigureMockMvc(addFilters = false)
class NewsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ContentService contentService;

    @MockitoBean
    private AdminAuthProperties adminAuthProperties;

    @MockitoBean
    private RateLimitProperties rateLimitProperties;

/**
     * AdminKeyAuthFilter — бин типа Filter, поэтому @WebMvcTest создаёт его даже
     * при addFilters = false. Фильтр резолвит актора через репозиторий, которого
     * в web-срезе нет, — без этой заглушки контекст не поднимется.
     */
    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @Test
    void listReturnsNews() throws Exception {
        var article = new NewsArticleDto("test-article",
                Map.of("tg", "Мақолаи тест", "ru", "Тестовая статья", "en", "Test article"),
                Map.of("tg", "Матн", "ru", "Текст", "en", "Body"),
                "https://example.com/image.jpg", Instant.parse("2025-01-01T12:00:00Z"));
        when(contentService.publishedNews()).thenReturn(List.of(article));

        mockMvc.perform(get("/v1/news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("test-article"))
                .andExpect(jsonPath("$[0].title.tg").value("Мақолаи тест"))
                .andExpect(jsonPath("$[0].title.ru").value("Тестовая статья"));
    }

    @Test
    void bySlugReturnsArticle() throws Exception {
        var article = new NewsArticleDto("test-article",
                Map.of("tg", "Мақолаи тест", "ru", "Тестовая статья", "en", "Test article"),
                Map.of("tg", "Матн", "ru", "Текст", "en", "Body"),
                null, Instant.parse("2025-01-01T12:00:00Z"));
        when(contentService.publishedBySlug("test-article")).thenReturn(article);

        mockMvc.perform(get("/v1/news/test-article"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("test-article"))
                .andExpect(jsonPath("$.coverMediaUrl").doesNotExist());
    }

    @Test
    void listReturnsEmptyWhenNoNews() throws Exception {
        when(contentService.publishedNews()).thenReturn(List.of());

        mockMvc.perform(get("/v1/news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
