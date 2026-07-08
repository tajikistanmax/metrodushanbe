package tj.metro.dushanbe.admin.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tj.metro.dushanbe.admin.security.AdminAuthProperties;
import tj.metro.dushanbe.admin.service.AdminLineService;
import tj.metro.dushanbe.admin.web.dto.LineCreateRequest;
import tj.metro.dushanbe.admin.web.dto.LineUpdateRequest;
import tj.metro.dushanbe.config.RateLimitProperties;
import tj.metro.dushanbe.network.web.dto.LineDto;

@WebMvcTest(AdminLineController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminLineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminLineService adminLineService;

    @MockitoBean
    private AdminAuthProperties adminAuthProperties;

    @MockitoBean
    private RateLimitProperties rateLimitProperties;

    @Test
    void createReturnsCreatedWithDto() throws Exception {
        var request = new LineCreateRequest("L1", Map.of("tg", "Хат", "ru", "Линия", "en", "Line"),
                "#FF0000", "active", 1, null);
        var dto = new LineDto("L1", Map.of("tg", "Хат", "ru", "Линия", "en", "Line"), "#FF0000", "active", 1);
        when(adminLineService.create(any(LineCreateRequest.class), eq("dev-admin"))).thenReturn(dto);

        mockMvc.perform(post("/v1/admin/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("L1"))
                .andExpect(jsonPath("$.colorHex").value("#FF0000"))
                .andExpect(jsonPath("$.status").value("active"));
    }

    @Test
    void updateReturnsDto() throws Exception {
        var request = new LineUpdateRequest(Map.of("tg", "Хат", "ru", "Линия", "en", "Line"),
                "#0000FF", "inactive", 2, null);
        var dto = new LineDto("L1", Map.of("tg", "Хат", "ru", "Линия", "en", "Line"), "#0000FF", "inactive", 2);
        when(adminLineService.update(eq("L1"), any(LineUpdateRequest.class), eq("dev-admin"))).thenReturn(dto);

        mockMvc.perform(put("/v1/admin/lines/L1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("inactive"))
                .andExpect(jsonPath("$.colorHex").value("#0000FF"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/v1/admin/lines/L1"))
                .andExpect(status().isNoContent());

        verify(adminLineService).softDelete("L1", "dev-admin");
    }

    @Test
    void createWithCustomActor() throws Exception {
        var request = new LineCreateRequest("L2", Map.of("tg", "Хат 2", "ru", "Линия 2", "en", "Line 2"),
                "#00FF00", "active", 2, null);
        var dto = new LineDto("L2", Map.of("tg", "Хат 2", "ru", "Линия 2", "en", "Line 2"), "#00FF00", "active", 2);
        when(adminLineService.create(any(LineCreateRequest.class), eq("admin-user"))).thenReturn(dto);

        mockMvc.perform(post("/v1/admin/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-Admin-Actor", "admin-user"))
                .andExpect(status().isCreated());
    }
}
