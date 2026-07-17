package tj.metro.dushanbe.notification.web;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import tj.metro.dushanbe.identity.repository.AdminUserRepository;
import tj.metro.dushanbe.notification.service.NotificationService;
import tj.metro.dushanbe.notification.web.dto.NotificationDto;
import tj.metro.dushanbe.notification.web.dto.NotificationTargetDto;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

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
    void listReturnsFeedWithFullI18nObjects() throws Exception {
        when(notificationService.feed(any(), any())).thenReturn(List.of(notification()));

        mockMvc.perform(get("/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("NTF-2026-001"))
                .andExpect(jsonPath("$[0].type").value("warning"))
                .andExpect(jsonPath("$[0].status").value("sent"))
                // Резолвинг ?lang= в backend не реализован — отдаём весь i18n-объект.
                .andExpect(jsonPath("$[0].title.tg").value("Огоҳӣ"))
                .andExpect(jsonPath("$[0].title.ru").value("Внимание"))
                .andExpect(jsonPath("$[0].title.en").value("Warning"))
                .andExpect(jsonPath("$[0].body.ru").value("Текст"))
                .andExpect(jsonPath("$[0].channels[0]").value("in_app"))
                .andExpect(jsonPath("$[0].targets[0].type").value("line"))
                .andExpect(jsonPath("$[0].targets[0].code").value("L1"));
    }

    @Test
    void listPassesBothTargetFiltersToService() throws Exception {
        when(notificationService.feed(eq("L1"), eq("ST-L1-01"))).thenReturn(List.of());

        mockMvc.perform(get("/v1/notifications")
                        .param("lineCode", "L1")
                        .param("stationCode", "ST-L1-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(notificationService).feed("L1", "ST-L1-01");
    }

    @Test
    void listWithoutFiltersPassesNulls() throws Exception {
        when(notificationService.feed(isNull(), isNull())).thenReturn(List.of());

        mockMvc.perform(get("/v1/notifications")).andExpect(status().isOk());

        verify(notificationService).feed(null, null);
    }

    @Test
    void networkWideNotificationIsReturnedWithEmptyTargets() throws Exception {
        var networkWide = new NotificationDto("NTF-ALL", null, null, "info",
                Map.of("tg", "Хабар", "ru", "Новость", "en", "News"),
                Map.of("tg", "Матн", "ru", "Текст", "en", "Body"),
                List.of("in_app"), "sent", List.of(),
                null, Instant.parse("2026-07-17T10:00:00Z"), null,
                // sent — терминальное состояние: переходов нет, тексты заморожены.
                List.of(), true);
        when(notificationService.feed(any(), any())).thenReturn(List.of(networkWide));

        mockMvc.perform(get("/v1/notifications"))
                .andExpect(status().isOk())
                // Пустой массив targets = рассылка на всю сеть.
                .andExpect(jsonPath("$[0].targets").isEmpty())
                // Не запланированная заранее рассылка отдаёт scheduledAt = null.
                .andExpect(jsonPath("$[0].scheduledAt").value(nullValue()))
                // Карта переходов и признак заморозки отдаются вычисленными, чтобы
                // консоли не приходилось держать копию NotificationStatus.TRANSITIONS
                // (та же причина, что и у IncidentDto.allowedTransitions).
                .andExpect(jsonPath("$[0].allowedTransitions").isEmpty())
                .andExpect(jsonPath("$[0].frozen").value(true));
    }

    private static NotificationDto notification() {
        return new NotificationDto("NTF-2026-001", "TPL-1", "ALERT-7", "warning",
                Map.of("tg", "Огоҳӣ", "ru", "Внимание", "en", "Warning"),
                Map.of("tg", "Матн", "ru", "Текст", "en", "Body"),
                List.of("in_app", "push"), "sent",
                List.of(new NotificationTargetDto("line", "L1")),
                null, Instant.parse("2026-07-17T10:00:00Z"),
                Instant.parse("2026-07-17T10:00:00Z"),
                // sent — терминальное состояние: переходов нет, тексты заморожены.
                List.of(), true);
    }
}
