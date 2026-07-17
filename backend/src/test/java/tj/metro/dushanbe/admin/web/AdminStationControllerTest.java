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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tj.metro.dushanbe.identity.repository.AdminUserRepository;
import tj.metro.dushanbe.admin.security.AdminAuthProperties;
import tj.metro.dushanbe.admin.service.AdminStationService;
import tj.metro.dushanbe.admin.web.dto.AccessibilityFeatureRequest;
import tj.metro.dushanbe.admin.web.dto.StationCreateRequest;
import tj.metro.dushanbe.admin.web.dto.StationExitRequest;
import tj.metro.dushanbe.admin.web.dto.StationUpdateRequest;
import tj.metro.dushanbe.config.RateLimitProperties;
import tj.metro.dushanbe.network.web.dto.AccessibilityFeatureDto;
import tj.metro.dushanbe.network.web.dto.StationDto;
import tj.metro.dushanbe.network.web.dto.StationExitDto;

@WebMvcTest(AdminStationController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminStationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminStationService adminStationService;

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
        var request = new StationCreateRequest("S1", Map.of("tg", "Ист", "ru", "Вокзал", "en", "Station"),
                "active", List.of(68.0, 38.0), false, null, null);
        var dto = new StationDto("S1", Map.of("tg", "Ист", "ru", "Вокзал", "en", "Station"),
                "active", List.of(), false, List.of(), List.of(68.0, 38.0));
        when(adminStationService.create(any(StationCreateRequest.class), eq("dev-admin"))).thenReturn(dto);

        mockMvc.perform(post("/v1/admin/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("S1"));
    }

    @Test
    void updateReturnsDto() throws Exception {
        var request = new StationUpdateRequest(Map.of("tg", "Ист", "ru", "Вокзал", "en", "Station"),
                "inactive", null, false, null, null);
        var dto = new StationDto("S1", Map.of("tg", "Ист", "ru", "Вокзал", "en", "Station"),
                "inactive", List.of(), false, List.of(), List.of(68.0, 38.0));
        when(adminStationService.update(eq("S1"), any(StationUpdateRequest.class), eq("dev-admin"))).thenReturn(dto);

        mockMvc.perform(put("/v1/admin/stations/S1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("inactive"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/v1/admin/stations/S1"))
                .andExpect(status().isNoContent());

        verify(adminStationService).softDelete("S1", "dev-admin");
    }

    @Test
    void createExitReturnsCreated() throws Exception {
        var request = new StationExitRequest("EX1", Map.of("tg", "Бар", "ru", "Выход", "en", "Exit"),
                List.of(68.0, 38.5), true, 1);
        var dto = new StationExitDto("EX1", Map.of("tg", "Бар", "ru", "Выход", "en", "Exit"),
                true, List.of(68.0, 38.5));
        when(adminStationService.createExit(eq("S1"), any(StationExitRequest.class), eq("dev-admin"))).thenReturn(dto);

        mockMvc.perform(post("/v1/admin/stations/S1/exits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("EX1"))
                .andExpect(jsonPath("$.isAccessible").value(true));
    }

    @Test
    void deleteExitReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/v1/admin/stations/exits/EX1"))
                .andExpect(status().isNoContent());

        verify(adminStationService).deleteExit("EX1", "dev-admin");
    }

    @Test
    void createFeatureReturnsCreated() throws Exception {
        var request = new AccessibilityFeatureRequest("elevator",
                Map.of("tg", "Так", "ru", "Тактильный", "en", "Tactile"), "available");
        var dto = new AccessibilityFeatureDto("elevator",
                Map.of("tg", "Так", "ru", "Тактильный", "en", "Tactile"), "available");
        when(adminStationService.createFeature(eq("S1"), any(AccessibilityFeatureRequest.class), eq("dev-admin")))
                .thenReturn(dto);

        mockMvc.perform(post("/v1/admin/stations/S1/accessibility-features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("elevator"))
                .andExpect(jsonPath("$.status").value("available"));
    }

    @Test
    void deleteFeatureReturnsNoContent() throws Exception {
        var featureId = UUID.randomUUID();

        mockMvc.perform(delete("/v1/admin/stations/accessibility-features/" + featureId))
                .andExpect(status().isNoContent());

        verify(adminStationService).deleteFeature(eq(featureId), eq("dev-admin"));
    }
}
