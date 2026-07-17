package tj.metro.dushanbe.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tj.metro.dushanbe.ai.config.AiProperties;
import tj.metro.dushanbe.alert.service.AlertService;
import tj.metro.dushanbe.alert.web.dto.AlertDto;
import tj.metro.dushanbe.content.repository.NewsArticleRepository;
import tj.metro.dushanbe.network.repository.MetroLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationRepository;
import tj.metro.dushanbe.schedule.repository.LineScheduleRepository;

class AiAgentReadinessServiceTest {

    private static final List<String> LANGUAGES = List.of("tg", "ru", "en");

    private AiAgentReadinessService serviceWith(long schedules) {
        MetroLineRepository lines = mock(MetroLineRepository.class);
        MetroStationRepository stations = mock(MetroStationRepository.class);
        AlertService alerts = mock(AlertService.class);
        NewsArticleRepository news = mock(NewsArticleRepository.class);
        LineScheduleRepository scheduleRepository = mock(LineScheduleRepository.class);

        when(lines.findByDeletedAtIsNullOrderBySortOrderAscCodeAsc()).thenReturn(List.of());
        when(stations.findByDeletedAtIsNullOrderByCodeAsc()).thenReturn(List.of());
        when(alerts.activeAlerts(null, null, null)).thenReturn(List.<AlertDto>of());
        when(news.findByStatusOrderByPublishedAtDesc("published")).thenReturn(List.of());
        when(scheduleRepository.count()).thenReturn(schedules);

        AiProperties aiProperties = mock(AiProperties.class);
        var llmProps = mock(AiProperties.Llm.class);
        when(aiProperties.getLlm()).thenReturn(llmProps);
        when(llmProps.getProvider()).thenReturn("disabled");

        return new AiAgentReadinessService(
                lines,
                stations,
                alerts,
                news,
                scheduleRepository,
                Clock.fixed(Instant.parse("2026-07-05T12:00:00Z"), ZoneOffset.UTC),
                aiProperties,
                mock(LlmClient.class)
        );
    }

    private static void assertFullI18n(Map<String, String> text, String what) {
        for (String language : LANGUAGES) {
            assertNotNull(text.get(language), what + ": нет языка " + language);
            assertFalse(text.get(language).isBlank(), what + ": пустой текст на " + language);
        }
    }

    /**
     * Панель показывает свободные тексты AI-контура через pickName, поэтому каждый из них обязан
     * приходить полным i18n-объектом — иначе оператор с tg увидит пустоту (dev-conventions.md, §6).
     */
    @Test
    void briefingFreeTextsCarryAllThreeLanguages() {
        var briefing = serviceWith(2L).briefing();

        for (Map<String, String> recommendation : briefing.recommendations()) {
            assertFullI18n(recommendation, "recommendation");
        }
        for (var agent : briefing.agents()) {
            assertFullI18n(agent.name(), agent.code() + ".name");
            assertFullI18n(agent.role(), agent.code() + ".role");
            assertFullI18n(agent.nextAction(), agent.code() + ".nextAction");
            for (Map<String, String> capability : agent.capabilities()) {
                assertFullI18n(capability, agent.code() + ".capability");
            }
            for (Map<String, String> signal : agent.signals()) {
                assertFullI18n(signal, agent.code() + ".signal");
            }
        }
    }

    /**
     * Ветки nextAction/сигналов зависят от наличия данных, а каталог переводов падает на неизвестном
     * коде — прогоняем обе конфигурации, иначе «пустая» ветка останется непокрытой.
     */
    @Test
    void briefingFreeTextsCarryAllThreeLanguagesWithoutSchedules() {
        var briefing = serviceWith(0L).briefing();

        for (var agent : briefing.agents()) {
            assertFullI18n(agent.role(), agent.code() + ".role");
            assertFullI18n(agent.nextAction(), agent.code() + ".nextAction");
        }
    }

    /** Число подставляется через «: N», чтобы не согласовывать множественное число в ru/tg. */
    @Test
    void countSignalsAppendValueAfterLocalizedLabel() {
        var briefing = serviceWith(7L).briefing();

        var scheduleAgent = briefing.agents().stream()
                .filter(a -> "schedule-agent".equals(a.code()))
                .findFirst()
                .orElseThrow();

        assertEquals("Записей расписания: 7", scheduleAgent.signals().get(0).get("ru"));
        assertEquals("Schedule records: 7", scheduleAgent.signals().get(0).get("en"));
        assertEquals("Сабтҳои ҷадвал: 7", scheduleAgent.signals().get(0).get("tg"));
    }

    @Test
    void briefingExposesAgentRegistryAndRecommendations() {
        MetroLineRepository lines = mock(MetroLineRepository.class);
        MetroStationRepository stations = mock(MetroStationRepository.class);
        AlertService alerts = mock(AlertService.class);
        NewsArticleRepository news = mock(NewsArticleRepository.class);
        LineScheduleRepository schedules = mock(LineScheduleRepository.class);

        when(lines.findByDeletedAtIsNullOrderBySortOrderAscCodeAsc()).thenReturn(List.of());
        when(stations.findByDeletedAtIsNullOrderByCodeAsc()).thenReturn(List.of());
        when(alerts.activeAlerts(null, null, null)).thenReturn(List.<AlertDto>of());
        when(news.findByStatusOrderByPublishedAtDesc("published")).thenReturn(List.of());
        when(schedules.count()).thenReturn(2L);

        AiProperties aiProperties = mock(AiProperties.class);
        var llmProps = mock(AiProperties.Llm.class);
        when(aiProperties.getLlm()).thenReturn(llmProps);
        when(llmProps.getProvider()).thenReturn("disabled");
        LlmClient llmClient = mock(LlmClient.class);

        AiAgentReadinessService service = new AiAgentReadinessService(
                lines,
                stations,
                alerts,
                news,
                schedules,
                Clock.fixed(Instant.parse("2026-07-05T12:00:00Z"), ZoneOffset.UTC),
                aiProperties,
                llmClient
        );

        var briefing = service.briefing();

        assertEquals("pilot_ready_with_security_gap", briefing.posture());
        assertEquals(12, briefing.agents().size());
        assertTrue(briefing.agents().stream().anyMatch(a -> "security-agent".equals(a.code())));
        assertTrue(briefing.recommendations().stream().anyMatch(r -> r.get("en").contains("admin auth")));
        assertTrue(briefing.recommendations().stream()
                .anyMatch(r -> r.get("ru").contains("продакшен-аутентификацию админа")));
    }
}
