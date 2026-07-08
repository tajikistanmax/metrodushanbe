package tj.metro.dushanbe.audit.web;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tj.metro.dushanbe.admin.security.AdminAuthProperties;
import tj.metro.dushanbe.audit.service.AuditQueryService;
import tj.metro.dushanbe.audit.web.dto.AuditEventDto;
import tj.metro.dushanbe.audit.web.dto.AuditPageDto;
import tj.metro.dushanbe.config.RateLimitProperties;

@WebMvcTest(AuditController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuditQueryService auditQueryService;

    @MockitoBean
    private AdminAuthProperties adminAuthProperties;

    @MockitoBean
    private RateLimitProperties rateLimitProperties;

    @Test
    void listReturnsPage() throws Exception {
        var event = new AuditEventDto(UUID.randomUUID(), "admin", "CREATE", "line", "L1",
                null, null, Instant.parse("2025-01-01T12:00:00Z"));
        var page = new AuditPageDto(List.of(event), 0, 50, 1, 1);
        when(auditQueryService.page(0, 50)).thenReturn(page);

        mockMvc.perform(get("/v1/admin/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].action").value("CREATE"))
                .andExpect(jsonPath("$.items[0].entityType").value("line"));
    }

    @Test
    void listWithCustomPagination() throws Exception {
        var page = new AuditPageDto(List.of(), 1, 10, 0, 0);
        when(auditQueryService.page(1, 10)).thenReturn(page);

        mockMvc.perform(get("/v1/admin/audit")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.items").isEmpty());
    }
}
