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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tj.metro.dushanbe.admin.security.AdminAuthProperties;
import tj.metro.dushanbe.admin.service.AdminAlertService;
import tj.metro.dushanbe.admin.web.dto.AlertCreateRequest;
import tj.metro.dushanbe.admin.web.dto.AlertTargetRequest;
import tj.metro.dushanbe.admin.web.dto.AlertUpdateRequest;
import tj.metro.dushanbe.alert.web.dto.AlertDto;
import tj.metro.dushanbe.alert.web.dto.AlertTargetDto;
import tj.metro.dushanbe.config.RateLimitProperties;

@WebMvcTest(AdminAlertController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminAlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminAlertService adminAlertService;

    @MockitoBean
    private AdminAuthProperties adminAuthProperties;

    @MockitoBean
    private RateLimitProperties rateLimitProperties;

    @Test
    void createReturnsCreated() throws Exception {
        var request = new AlertCreateRequest("ALERT-001", "info",
                Map.of("tg", "Огоҳӣ"), Map.of("tg", "Матн"),
                Instant.parse("2026-06-15T00:00:00Z"), null, null);
        var dto = new AlertDto("ALERT-001", "info",
                Map.of("tg", "Огоҳӣ"), Map.of("tg", "Матн"),
                Instant.parse("2026-06-15T00:00:00Z"), null, List.of());
        when(adminAlertService.create(any(AlertCreateRequest.class), eq("dev-admin"))).thenReturn(dto);

        mockMvc.perform(post("/v1/admin/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ALERT-001"))
                .andExpect(jsonPath("$.severity").value("info"));
    }

    @Test
    void updateReturnsDto() throws Exception {
        var request = new AlertUpdateRequest("warning",
                Map.of("tg", "Огоҳӣи нав"), Map.of("tg", "Матни нав"),
                Instant.parse("2026-07-01T00:00:00Z"), null, List.of(new AlertTargetRequest("line", "L1")));
        var dto = new AlertDto("ALERT-001", "warning",
                Map.of("tg", "Огоҳӣи нав"), Map.of("tg", "Матни нав"),
                Instant.parse("2026-07-01T00:00:00Z"), null,
                List.of(new AlertTargetDto("line", "L1")));
        when(adminAlertService.update(eq("ALERT-001"), any(AlertUpdateRequest.class), eq("dev-admin"))).thenReturn(dto);

        mockMvc.perform(put("/v1/admin/alerts/ALERT-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targets[0].type").value("line"))
                .andExpect(jsonPath("$.targets[0].code").value("L1"));
    }

    @Test
    void publishReturnsDto() throws Exception {
        var dto = new AlertDto("ALERT-001", "critical",
                Map.of("tg", "Огоҳӣ"), Map.of("tg", "Матн"),
                Instant.parse("2026-06-15T00:00:00Z"), null, List.of());
        when(adminAlertService.publish("ALERT-001", "dev-admin")).thenReturn(dto);

        mockMvc.perform(post("/v1/admin/alerts/ALERT-001/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity").value("critical"));
    }
}
