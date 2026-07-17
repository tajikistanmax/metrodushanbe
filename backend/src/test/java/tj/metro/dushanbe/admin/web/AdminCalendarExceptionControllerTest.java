package tj.metro.dushanbe.admin.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tj.metro.dushanbe.identity.repository.AdminUserRepository;
import tj.metro.dushanbe.admin.security.AdminAuthProperties;
import tj.metro.dushanbe.admin.web.dto.CalendarExceptionCreateRequest;
import tj.metro.dushanbe.admin.web.dto.CalendarExceptionUpdateRequest;
import tj.metro.dushanbe.config.RateLimitProperties;
import tj.metro.dushanbe.schedule.domain.CalendarException;
import tj.metro.dushanbe.schedule.repository.CalendarExceptionRepository;

@WebMvcTest(AdminCalendarExceptionController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminCalendarExceptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CalendarExceptionRepository repository;

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
    void listReturnsAllExceptions() throws Exception {
        var ex1 = new CalendarException(LocalDate.of(2026, 6, 1), "holiday", "tg1", "ru1", "en1", true);
        var ex2 = new CalendarException(LocalDate.of(2026, 7, 1), "weekend", "tg2", "ru2", "en2", false);
        when(repository.findAll()).thenReturn(List.of(ex1, ex2));

        mockMvc.perform(get("/v1/admin/calendar-exceptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].exceptionDate").value("2026-06-01"))
                .andExpect(jsonPath("$[0].dayType").value("holiday"))
                .andExpect(jsonPath("$[0].descriptionTg").value("tg1"))
                .andExpect(jsonPath("$[0].descriptionRu").value("ru1"))
                .andExpect(jsonPath("$[0].descriptionEn").value("en1"))
                .andExpect(jsonPath("$[0].isRecurring").value(true));
    }

    @Test
    void createReturnsCreated() throws Exception {
        var request = new CalendarExceptionCreateRequest(
                LocalDate.of(2026, 8, 1), "holiday", "tg", "ru", "en", true);
        var saved = new CalendarException(LocalDate.of(2026, 8, 1), "holiday", "tg", "ru", "en", true);
        when(repository.save(any(CalendarException.class))).thenReturn(saved);

        mockMvc.perform(post("/v1/admin/calendar-exceptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dayType").value("holiday"))
                .andExpect(jsonPath("$.isRecurring").value(true));
    }

    @Test
    void updateReturnsUpdatedDto() throws Exception {
        var existing = new CalendarException(LocalDate.of(2026, 1, 1), "weekday", null, null, null, false);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(CalendarException.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new CalendarExceptionUpdateRequest(
                LocalDate.of(2026, 6, 15), "holiday", "tg", "ru", "en", true);

        mockMvc.perform(put("/v1/admin/calendar-exceptions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exceptionDate").value("2026-06-15"))
                .andExpect(jsonPath("$.dayType").value("holiday"))
                .andExpect(jsonPath("$.isRecurring").value(true));
    }

    @Test
    void updateReturns404WhenNotFound() throws Exception {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        var request = new CalendarExceptionUpdateRequest(
                LocalDate.of(2026, 6, 15), "holiday", null, null, null, false);

        mockMvc.perform(put("/v1/admin/calendar-exceptions/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        when(repository.existsById(1L)).thenReturn(true);

        mockMvc.perform(delete("/v1/admin/calendar-exceptions/1"))
                .andExpect(status().isNoContent());

        verify(repository).deleteById(1L);
    }

    @Test
    void deleteReturns404WhenNotFound() throws Exception {
        when(repository.existsById(999L)).thenReturn(false);

        mockMvc.perform(delete("/v1/admin/calendar-exceptions/999"))
                .andExpect(status().isNotFound());
    }
}
