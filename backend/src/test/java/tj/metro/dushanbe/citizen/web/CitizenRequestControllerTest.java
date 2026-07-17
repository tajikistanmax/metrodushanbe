package tj.metro.dushanbe.citizen.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tj.metro.dushanbe.identity.repository.AdminUserRepository;
import tj.metro.dushanbe.admin.security.AdminAuthProperties;
import tj.metro.dushanbe.citizen.service.CitizenRequestService;
import tj.metro.dushanbe.citizen.web.dto.CitizenRequestCreateRequest;
import tj.metro.dushanbe.citizen.web.dto.CitizenRequestCreateResponse;
import tj.metro.dushanbe.citizen.web.dto.CitizenRequestPublicDto;
import tj.metro.dushanbe.config.RateLimitProperties;

@WebMvcTest(CitizenRequestController.class)
@AutoConfigureMockMvc(addFilters = false)
class CitizenRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CitizenRequestService service;

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
    void createReturnsTrackingSecretOnce() throws Exception {
        var publicDto = dto("new", null);
        when(service.create(any(CitizenRequestCreateRequest.class)))
                .thenReturn(new CitizenRequestCreateResponse(publicDto, "secret-token"));
        var request = new CitizenRequestCreateRequest(
                "complaint", "Тема", "Подробный текст", null, "user@example.com",
                null, null, null, true);

        mockMvc.perform(post("/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.request.code").value("REQ-2026-A1B2C3D4"))
                .andExpect(jsonPath("$.request.status").value("new"))
                .andExpect(jsonPath("$.trackingToken").value("secret-token"));
    }

    @Test
    void createRejectsMissingConsent() throws Exception {
        var request = new CitizenRequestCreateRequest(
                "complaint", "Тема", "Текст", null, null, null, null, null, false);

        mockMvc.perform(post("/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trackReturnsPublicFields() throws Exception {
        when(service.track("REQ-2026-A1B2C3D4", "secret-token"))
                .thenReturn(dto("in_progress", null));

        mockMvc.perform(post("/v1/requests/track")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"REQ-2026-A1B2C3D4","trackingToken":"secret-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("in_progress"))
                .andExpect(jsonPath("$.contactEmail").doesNotExist());
    }

    private static CitizenRequestPublicDto dto(String status, String response) {
        Instant now = Instant.parse("2026-07-16T08:00:00Z");
        return new CitizenRequestPublicDto("REQ-2026-A1B2C3D4", "complaint", status,
                "Тема", response, now, now);
    }
}
