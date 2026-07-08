package tj.metro.dushanbe.routing.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tj.metro.dushanbe.admin.security.AdminAuthProperties;
import tj.metro.dushanbe.config.RateLimitProperties;
import tj.metro.dushanbe.routing.service.RoutingService;
import tj.metro.dushanbe.routing.web.dto.RouteDto;
import tj.metro.dushanbe.routing.web.dto.RouteLegDto;
import tj.metro.dushanbe.routing.web.dto.RouteStopDto;

@WebMvcTest(RouteController.class)
@AutoConfigureMockMvc(addFilters = false)
class RouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RoutingService routingService;

    @MockitoBean
    private AdminAuthProperties adminAuthProperties;

    @MockitoBean
    private RateLimitProperties rateLimitProperties;

    @Test
    void routeReturnsPath() throws Exception {
        var leg = new RouteLegDto("L1", Map.of("tg", "Хат", "ru", "Линия", "en", "Line"),
                "#FF0000", List.of("ST-L1-01", "ST-L1-02"), 1, 5);
        var stop = new RouteStopDto("ST-L1-01", Map.of("tg", "Истгоҳ", "ru", "Станция", "en", "Station"),
                "L1", false);
        var dto = new RouteDto("ST-L1-01", "ST-L1-02", true, 5, 0, 1,
                List.of(leg), List.of(stop));
        when(routingService.route("ST-L1-01", "ST-L1-02")).thenReturn(dto);

        mockMvc.perform(get("/v1/routes")
                        .param("from", "ST-L1-01")
                        .param("to", "ST-L1-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.from").value("ST-L1-01"))
                .andExpect(jsonPath("$.to").value("ST-L1-02"))
                .andExpect(jsonPath("$.estimatedMinutes").value(5))
                .andExpect(jsonPath("$.legs.length()").value(1));
    }

    @Test
    void routeNotFound() throws Exception {
        var dto = new RouteDto("ST-L1-01", "ST-L2-99", false, 0, 0, 0,
                List.of(), List.of());
        when(routingService.route("ST-L1-01", "ST-L2-99")).thenReturn(dto);

        mockMvc.perform(get("/v1/routes")
                        .param("from", "ST-L1-01")
                        .param("to", "ST-L2-99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false))
                .andExpect(jsonPath("$.legs").isEmpty());
    }
}
