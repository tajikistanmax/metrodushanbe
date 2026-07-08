package tj.metro.dushanbe.schedule.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tj.metro.dushanbe.admin.security.AdminAuthProperties;
import tj.metro.dushanbe.config.RateLimitProperties;
import tj.metro.dushanbe.schedule.service.ScheduleService;
import tj.metro.dushanbe.schedule.web.dto.ArrivalDto;
import tj.metro.dushanbe.schedule.web.dto.LineScheduleDto;
import tj.metro.dushanbe.schedule.web.dto.StationArrivalsDto;

@WebMvcTest(ScheduleController.class)
@AutoConfigureMockMvc(addFilters = false)
class ScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ScheduleService scheduleService;

    @MockitoBean
    private AdminAuthProperties adminAuthProperties;

    @MockitoBean
    private RateLimitProperties rateLimitProperties;

    @Test
    void scheduleReturnsLineSchedule() throws Exception {
        var dto = new LineScheduleDto("L1", "weekday", "05:00", "23:00", 10,
                LocalDate.of(2025, 1, 1), null);
        when(scheduleService.lineSchedule(eq("L1"), eq("weekday"))).thenReturn(dto);

        mockMvc.perform(get("/v1/lines/L1/schedule")
                        .param("dayType", "weekday"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineCode").value("L1"))
                .andExpect(jsonPath("$.dayType").value("weekday"))
                .andExpect(jsonPath("$.firstDeparture").value("05:00"))
                .andExpect(jsonPath("$.lastDeparture").value("23:00"))
                .andExpect(jsonPath("$.headwayMinutes").value(10));
    }

    @Test
    void arrivalsReturnsArrivals() throws Exception {
        var arrivals = List.of(new ArrivalDto("05:10", 5), new ArrivalDto("05:20", 15));
        var dto = new StationArrivalsDto("ST-L1-01", "L1", "weekday", true,
                10, true, null, arrivals);
        when(scheduleService.arrivals(eq("ST-L1-01"), eq("L1"), eq("weekday"), eq(5)))
                .thenReturn(dto);

        mockMvc.perform(get("/v1/stations/ST-L1-01/arrivals")
                        .param("lineCode", "L1")
                        .param("dayType", "weekday")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationCode").value("ST-L1-01"))
                .andExpect(jsonPath("$.lineCode").value("L1"))
                .andExpect(jsonPath("$.serviceActive").value(true))
                .andExpect(jsonPath("$.estimated").value(true))
                .andExpect(jsonPath("$.arrivals.length()").value(2));
    }

    @Test
    void arrivalsWithoutOptionalParams() throws Exception {
        var dto = new StationArrivalsDto("ST-L1-01", "L1", null, false,
                null, true, null, List.of());
        when(scheduleService.arrivals(eq("ST-L1-01"), eq("L1"), any(), any()))
                .thenReturn(dto);

        mockMvc.perform(get("/v1/stations/ST-L1-01/arrivals")
                        .param("lineCode", "L1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceActive").value(false))
                .andExpect(jsonPath("$.arrivals").isEmpty());
    }
}
