package tj.metro.dushanbe.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import tj.metro.dushanbe.ai.config.AiProperties;
import tj.metro.dushanbe.alert.service.AlertService;
import tj.metro.dushanbe.alert.web.dto.AlertDto;
import tj.metro.dushanbe.content.repository.NewsArticleRepository;
import tj.metro.dushanbe.network.repository.MetroLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationRepository;
import tj.metro.dushanbe.schedule.repository.LineScheduleRepository;

class AiAgentReadinessServiceTest {

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
        assertTrue(briefing.recommendations().stream().anyMatch(r -> r.contains("admin auth")));
    }
}
