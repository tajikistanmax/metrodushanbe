package tj.metro.dushanbe.admin.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tj.metro.dushanbe.identity.repository.AdminUserRepository;
import tj.metro.dushanbe.admin.security.AdminAuthProperties;
import tj.metro.dushanbe.admin.service.AdminNewsService;
import tj.metro.dushanbe.admin.web.dto.NewsCreateRequest;
import tj.metro.dushanbe.admin.web.dto.NewsUpdateRequest;
import tj.metro.dushanbe.config.RateLimitProperties;
import tj.metro.dushanbe.content.web.dto.NewsArticleDto;

@WebMvcTest(AdminNewsController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminNewsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminNewsService adminNewsService;

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
    void createReturnsCreated() throws Exception {
        var request = new NewsCreateRequest("new-line-open", Map.of("tg", "Салом"), Map.of("tg", "Матн"), null);
        var dto = new NewsArticleDto("new-line-open", Map.of("tg", "Салом"), Map.of("tg", "Матн"), null, null);
        when(adminNewsService.create(any(NewsCreateRequest.class), eq("dev-admin"))).thenReturn(dto);

        mockMvc.perform(post("/v1/admin/news")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("new-line-open"));
    }

    @Test
    void updateReturnsDto() throws Exception {
        var request = new NewsUpdateRequest(Map.of("tg", "Нав"), Map.of("tg", "Матни нав"), "https://pic.url");
        var dto = new NewsArticleDto("new-line-open", Map.of("tg", "Нав"), Map.of("tg", "Матни нав"), "https://pic.url", null);
        when(adminNewsService.update(eq("new-line-open"), any(NewsUpdateRequest.class), eq("dev-admin"))).thenReturn(dto);

        mockMvc.perform(put("/v1/admin/news/new-line-open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverMediaUrl").value("https://pic.url"));
    }

    @Test
    void publishReturnsDto() throws Exception {
        var dto = new NewsArticleDto("new-line-open", Map.of("tg", "Салом"), Map.of("tg", "Матн"), null, Instant.parse("2026-06-15T10:00:00Z"));
        when(adminNewsService.publish("new-line-open", "dev-admin")).thenReturn(dto);

        mockMvc.perform(post("/v1/admin/news/new-line-open/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedAt").value("2026-06-15T10:00:00Z"));
    }
}
