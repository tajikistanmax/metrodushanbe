package tj.metro.dushanbe.admin.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import tj.metro.dushanbe.citizen.service.CitizenRequestService;
import tj.metro.dushanbe.citizen.web.dto.CitizenRequestAdminDto;
import tj.metro.dushanbe.citizen.web.dto.CitizenRequestUpdateRequest;
import tj.metro.dushanbe.config.RateLimitProperties;

@WebMvcTest(AdminCitizenRequestController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminCitizenRequestControllerTest {

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

    @Test
    void listPassesOptionalStatusFilter() throws Exception {
        when(service.list("new")).thenReturn(List.of(dto("new")));

        mockMvc.perform(get("/v1/admin/requests").param("status", "new"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("REQ-2026-A1B2C3D4"))
                .andExpect(jsonPath("$[0].priority").value("normal"));
    }

    @Test
    void updateUsesActorHeaderAndReturnsWorkflowState() throws Exception {
        var request = new CitizenRequestUpdateRequest("in_progress", null, "operator-1");
        when(service.update("REQ-2026-A1B2C3D4", request, "supervisor"))
                .thenReturn(dto("in_progress"));

        mockMvc.perform(put("/v1/admin/requests/REQ-2026-A1B2C3D4")
                        .header("X-Admin-Actor", "supervisor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("in_progress"));

        verify(service).update("REQ-2026-A1B2C3D4", request, "supervisor");
    }

    @Test
    void updateRejectsUnknownStatusBeforeService() throws Exception {
        mockMvc.perform(put("/v1/admin/requests/REQ-2026-A1B2C3D4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"unknown","assignedTo":"operator-1"}
                                """))
                .andExpect(status().isBadRequest());

        verify(service, org.mockito.Mockito.never()).update(any(), any(), any());
    }

    private static CitizenRequestAdminDto dto(String status) {
        Instant now = Instant.parse("2026-07-16T08:00:00Z");
        return new CitizenRequestAdminDto(
                "REQ-2026-A1B2C3D4", "complaint", "normal", status,
                "Тема", "Текст", "Али", "ali@example.com", null, "L1", "ST-L1-01",
                null, "operator-1", now.plusSeconds(3600), now.plusSeconds(86400),
                false, false, now, now, null);
    }
}
