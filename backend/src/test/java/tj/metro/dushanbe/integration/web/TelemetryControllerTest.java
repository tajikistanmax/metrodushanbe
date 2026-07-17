package tj.metro.dushanbe.integration.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tj.metro.dushanbe.admin.security.AdminAuthProperties;
import tj.metro.dushanbe.config.RateLimitProperties;
import tj.metro.dushanbe.identity.repository.AdminUserRepository;
import tj.metro.dushanbe.integration.service.TelemetryService;
import tj.metro.dushanbe.integration.web.dto.TrainPositionDto;
import tj.metro.dushanbe.integration.web.dto.TrainPositionReportRequest;

@WebMvcTest(TelemetryController.class)
@AutoConfigureMockMvc(addFilters = false)
class TelemetryControllerTest {

    private static final String MACHINE_KEY = "gps-platform-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TelemetryService service;

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

    @BeforeEach
    void stubMachineKey() {
        when(adminAuthProperties.getDevKey()).thenReturn(MACHINE_KEY);
    }

    @Test
    void reportWithMachineKeyIsAccepted() throws Exception {
        when(service.report(any(TrainPositionReportRequest.class))).thenReturn(dto());

        mockMvc.perform(post("/v1/telemetry/positions")
                        .header("X-Admin-Key", MACHINE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trainCode").value("TR-01"))
                .andExpect(jsonPath("$.coordinates[0]").value(68.78))
                .andExpect(jsonPath("$.coordinates[1]").value(38.56));
    }

    @Test
    void reportWithoutMachineKeyIsRejectedBeforeTouchingTheService() throws Exception {
        mockMvc.perform(post("/v1/telemetry/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("admin.unauthorized"));

        verify(service, never()).report(any());
    }

    @Test
    void reportWithWrongMachineKeyIsRejected() throws Exception {
        mockMvc.perform(post("/v1/telemetry/positions")
                        .header("X-Admin-Key", "guessed-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isUnauthorized());

        verify(service, never()).report(any());
    }

    @Test
    void reportRejectsMalformedCoordinatesBeforeAuthorizedService() throws Exception {
        String body = """
                {"trainCode":"TR-01","lineCode":"L1","coordinates":[68.78],
                 "reportedAt":"2026-07-17T10:00:00Z"}
                """;

        mockMvc.perform(post("/v1/telemetry/positions")
                        .header("X-Admin-Key", MACHINE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void positionsAreReadableWithoutAnyKey() throws Exception {
        when(service.currentPositions("L1")).thenReturn(List.of(dto()));

        mockMvc.perform(get("/v1/telemetry/positions").param("lineCode", "L1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].trainCode").value("TR-01"))
                .andExpect(jsonPath("$[0].delaySeconds").value(45))
                .andExpect(jsonPath("$[0].lagSeconds").value(3));
    }

    @Test
    void positionsRequireLineCode() throws Exception {
        mockMvc.perform(get("/v1/telemetry/positions"))
                .andExpect(status().isBadRequest());
    }

    private static TrainPositionReportRequest request() {
        return new TrainPositionReportRequest("TR-01", "L1", "ST-L1-03", "ST-L1-04",
                List.of(68.78, 38.56), 90, new BigDecimal("42.5"), 45, "medium",
                Instant.parse("2026-07-17T10:00:00Z"));
    }

    private static TrainPositionDto dto() {
        return new TrainPositionDto("TR-01", "L1", "ST-L1-03", "ST-L1-04",
                List.of(68.78, 38.56), 90, new BigDecimal("42.5"), 45, "medium",
                Instant.parse("2026-07-17T10:00:00Z"), Instant.parse("2026-07-17T10:00:03Z"), 3);
    }
}
