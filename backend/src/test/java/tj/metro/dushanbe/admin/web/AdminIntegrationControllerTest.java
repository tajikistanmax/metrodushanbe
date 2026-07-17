package tj.metro.dushanbe.admin.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tj.metro.dushanbe.admin.security.AdminAuthProperties;
import tj.metro.dushanbe.admin.service.AdminIntegrationService;
import tj.metro.dushanbe.admin.web.dto.WebhookCreateRequest;
import tj.metro.dushanbe.admin.web.dto.WebhookSecretDto;
import tj.metro.dushanbe.config.RateLimitProperties;
import tj.metro.dushanbe.identity.repository.AdminUserRepository;
import tj.metro.dushanbe.integration.web.dto.WebhookDeliveryDto;
import tj.metro.dushanbe.integration.web.dto.WebhookDeliveryPageDto;
import tj.metro.dushanbe.integration.web.dto.WebhookSubscriptionDto;

@WebMvcTest(AdminIntegrationController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminIntegrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminIntegrationService service;

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
    void createReturnsSecretExactlyOnceAlongsideTheSubscription() throws Exception {
        when(service.createSubscription(any(WebhookCreateRequest.class), eq("integration-admin")))
                .thenReturn(new WebhookSecretDto(subscription(), "one-time-secret"));

        mockMvc.perform(post("/v1/admin/webhooks")
                        .header("X-Admin-Actor", "integration-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WebhookCreateRequest(
                                "city-portal", "Городской портал", "https://portal.example/hook",
                                List.of("alert_published"), true, 60))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secret").value("one-time-secret"))
                .andExpect(jsonPath("$.subscription.code").value("city-portal"))
                .andExpect(jsonPath("$.subscription.secretFingerprint").value("a1b2c3d4"));
    }

    @Test
    void listNeverLeaksSecretOfAnySubscription() throws Exception {
        when(service.listSubscriptions()).thenReturn(List.of(subscription()));

        mockMvc.perform(get("/v1/admin/webhooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("city-portal"))
                .andExpect(jsonPath("$[0].secretFingerprint").value("a1b2c3d4"))
                .andExpect(jsonPath("$[0].secret").doesNotExist())
                .andExpect(jsonPath("$[0].secretHash").doesNotExist());
    }

    @Test
    void getSubscriptionNeverLeaksSecret() throws Exception {
        when(service.getSubscription("city-portal")).thenReturn(subscription());

        mockMvc.perform(get("/v1/admin/webhooks/city-portal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andExpect(jsonPath("$.secretHash").doesNotExist());
    }

    @Test
    void rotateSecretShowsTheNewKeyOnce() throws Exception {
        when(service.rotateSecret("city-portal", "integration-admin"))
                .thenReturn(new WebhookSecretDto(subscription(), "rotated-secret"));

        mockMvc.perform(post("/v1/admin/webhooks/city-portal/secret")
                        .header("X-Admin-Actor", "integration-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value("rotated-secret"));
    }

    @Test
    void deliveriesQueueShowsFailureReasonForOperator() throws Exception {
        when(service.listDeliveries(null, 0, 50)).thenReturn(page(List.of(delivery("dead")), 0, 50, 1));

        mockMvc.perform(get("/v1/admin/webhooks/deliveries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value("WHD-1"))
                .andExpect(jsonPath("$.items[0].status").value("dead"))
                .andExpect(jsonPath("$.items[0].lastError").value("connection refused"))
                .andExpect(jsonPath("$.items[0].attempts").value(6))
                .andExpect(jsonPath("$.items[0].retryable").value(true));
    }

    @Test
    void deliveriesQueuePassesStatusFilterThrough() throws Exception {
        when(service.listDeliveries("failed", 0, 50))
                .thenReturn(page(List.of(delivery("failed")), 0, 50, 1));

        mockMvc.perform(get("/v1/admin/webhooks/deliveries").param("status", "failed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("failed"));
    }

    /** Контракт пагинации — тот же, что у ленты импортов: page/size + items и метаданные. */
    @Test
    void deliveriesQueueDefaultsToFirstPageOfFifty() throws Exception {
        when(service.listDeliveries(null, 0, 50)).thenReturn(page(List.of(delivery("dead")), 0, 50, 1));

        mockMvc.perform(get("/v1/admin/webhooks/deliveries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(service).listDeliveries(null, 0, 50);
    }

    @Test
    void deliveriesQueuePassesPageAndSizeThrough() throws Exception {
        when(service.listDeliveries(null, 2, 10)).thenReturn(page(List.of(), 2, 10, 25));

        mockMvc.perform(get("/v1/admin/webhooks/deliveries")
                        .param("page", "2").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(25));

        verify(service).listDeliveries(null, 2, 10);
    }

    /** Пустая страница — это 200 с пустым items, а не 404: очередь просто кончилась. */
    @Test
    void deliveriesQueueBeyondLastPageIsEmptyButOk() throws Exception {
        when(service.listDeliveries(null, 99, 50)).thenReturn(page(List.of(), 99, 50, 1));

        mockMvc.perform(get("/v1/admin/webhooks/deliveries").param("page", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    private static WebhookDeliveryPageDto page(List<WebhookDeliveryDto> items, int page, int size,
                                               long total) {
        return new WebhookDeliveryPageDto(items, page, size, total,
                (int) Math.ceil((double) total / size));
    }

    @Test
    void manualRetryPutsDeliveryBackToPending() throws Exception {
        when(service.retryDelivery("WHD-1", "duty-operator")).thenReturn(delivery("pending"));

        mockMvc.perform(post("/v1/admin/webhooks/deliveries/WHD-1/retry")
                        .header("X-Admin-Actor", "duty-operator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"));
    }

    private static WebhookSubscriptionDto subscription() {
        return new WebhookSubscriptionDto("city-portal", "Городской портал",
                "https://portal.example/hook", List.of("alert_published"), true, 60,
                "a1b2c3d4", "integration-admin",
                Instant.parse("2026-07-17T10:00:00Z"), Instant.parse("2026-07-17T10:00:00Z"));
    }

    private static WebhookDeliveryDto delivery(String status) {
        return new WebhookDeliveryDto("WHD-1", "11111111-1111-1111-1111-111111111111",
                "alert_published", "alert", "ALERT-1", "city-portal", status, 6, null,
                "connection refused", "trace-1", null,
                Instant.parse("2026-07-17T10:00:00Z"), Instant.parse("2026-07-17T10:05:00Z"),
                true, List.of("pending"));
    }
}
