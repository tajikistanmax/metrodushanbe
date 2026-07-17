package tj.metro.dushanbe.alert.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import tj.metro.dushanbe.alert.service.AlertService;
import tj.metro.dushanbe.alert.web.dto.AlertDto;
import tj.metro.dushanbe.alert.web.dto.AlertTargetDto;
import tj.metro.dushanbe.config.RateLimitProperties;

@WebMvcTest(AlertController.class)
@AutoConfigureMockMvc(addFilters = false)
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AlertService alertService;

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
    void listReturnsActiveAlerts() throws Exception {
        var alert = new AlertDto("ALRT-001", "warning",
                Map.of("tg", "Огоҳӣ", "ru", "Внимание", "en", "Warning"),
                Map.of("tg", "Матн", "ru", "Текст", "en", "Body"),
                Instant.parse("2025-01-01T00:00:00Z"), null,
                List.of(new AlertTargetDto("line", "L1")));
        when(alertService.activeAlerts(any(), any(), any())).thenReturn(List.of(alert));

        mockMvc.perform(get("/v1/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("ALRT-001"))
                .andExpect(jsonPath("$[0].severity").value("warning"))
                .andExpect(jsonPath("$[0].targets[0].type").value("line"))
                .andExpect(jsonPath("$[0].targets[0].code").value("L1"));
    }

    @Test
    void listWithFilters() throws Exception {
        when(alertService.activeAlerts(eq("L1"), eq("ST-L1-01"), eq("critical")))
                .thenReturn(List.of());

        mockMvc.perform(get("/v1/alerts")
                        .param("lineCode", "L1")
                        .param("stationCode", "ST-L1-01")
                        .param("severity", "critical"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
