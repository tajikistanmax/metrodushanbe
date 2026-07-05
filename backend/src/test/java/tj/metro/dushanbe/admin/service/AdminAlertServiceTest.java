package tj.metro.dushanbe.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tj.metro.dushanbe.admin.web.dto.AlertCreateRequest;
import tj.metro.dushanbe.admin.web.dto.AlertTargetRequest;
import tj.metro.dushanbe.alert.domain.ServiceAlert;
import tj.metro.dushanbe.alert.repository.ServiceAlertRepository;
import tj.metro.dushanbe.alert.web.dto.AlertDto;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.network.repository.MetroLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationRepository;

/**
 * Юнит-тесты AdminAlertService без БД: репозитории и AuditService — mock,
 * «сейчас» — Clock.fixed. Проверяются валидация входа, обязательный вызов аудита
 * при мутациях, жизненный цикл публикации и гейт полноты языков.
 */
class AdminAlertServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-05T10:00:00Z");
    private static final String ACTOR = "auditor-1";
    private static final Map<String, String> FULL_I18N =
            Map.of("tg", "Сарлавҳа", "ru", "Заголовок", "en", "Title");

    private final ServiceAlertRepository alertRepository = mock(ServiceAlertRepository.class);
    private final MetroLineRepository lineRepository = mock(MetroLineRepository.class);
    private final MetroStationRepository stationRepository = mock(MetroStationRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AdminAlertService service = new AdminAlertService(
            alertRepository, lineRepository, stationRepository, auditService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createPersistsDraftAndRecordsAudit() {
        when(alertRepository.existsByCode("ALERT-1")).thenReturn(false);
        when(alertRepository.save(any(ServiceAlert.class))).thenAnswer(inv -> inv.getArgument(0));
        when(lineRepository.existsByCode("L1")).thenReturn(true);

        AlertCreateRequest request = new AlertCreateRequest("ALERT-1", "info", FULL_I18N, FULL_I18N,
                NOW.minusSeconds(3600), null, List.of(new AlertTargetRequest("line", "L1")));

        AlertDto dto = service.create(request, ACTOR);

        assertEquals("ALERT-1", dto.code());
        ArgumentCaptor<ServiceAlert> saved = ArgumentCaptor.forClass(ServiceAlert.class);
        verify(alertRepository).save(saved.capture());
        assertEquals("draft", saved.getValue().getStatus());
        verify(auditService).record(eq(ACTOR), eq("alert.create"), eq("alert"), eq("ALERT-1"), isNull(), any());
    }

    @Test
    void createRejectsInvalidSeverityWithoutAudit() {
        AlertCreateRequest request = new AlertCreateRequest("ALERT-2", "bogus", FULL_I18N, FULL_I18N,
                NOW.minusSeconds(3600), null, null);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.create(request, ACTOR));

        assertEquals("alert.severity_invalid", ex.getCode());
        verify(auditService, never()).record(anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void createRejectsUnknownTarget() {
        when(alertRepository.existsByCode("ALERT-3")).thenReturn(false);
        when(stationRepository.existsByCode("ST-NOPE")).thenReturn(false);
        AlertCreateRequest request = new AlertCreateRequest("ALERT-3", "info", FULL_I18N, FULL_I18N,
                NOW.minusSeconds(3600), null, List.of(new AlertTargetRequest("station", "ST-NOPE")));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.create(request, ACTOR));

        assertEquals("alert.target_unknown", ex.getCode());
        verify(auditService, never()).record(anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void createRejectsWindowWithEndsBeforeStarts() {
        AlertCreateRequest request = new AlertCreateRequest("ALERT-4", "info", FULL_I18N, FULL_I18N,
                NOW, NOW.minusSeconds(60), null);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.create(request, ACTOR));

        assertEquals("alert.window_invalid", ex.getCode());
    }

    @Test
    void publishTransitionsDraftToPublishedAndAudits() {
        ServiceAlert draft = draft("ALERT-5", FULL_I18N, FULL_I18N);
        when(alertRepository.findByCode("ALERT-5")).thenReturn(Optional.of(draft));
        when(alertRepository.save(any(ServiceAlert.class))).thenAnswer(inv -> inv.getArgument(0));

        AlertDto dto = service.publish("ALERT-5", ACTOR);

        assertEquals("ALERT-5", dto.code());
        assertEquals("published", draft.getStatus());
        assertEquals(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), draft.getPublishedAt());
        verify(auditService).record(eq(ACTOR), eq("alert.publish"), eq("alert"), eq("ALERT-5"), any(), any());
    }

    @Test
    void publishBlockedFromNonPublishableStatus() {
        ServiceAlert published = new ServiceAlert(UUID.randomUUID(), "ALERT-6", "info", "published",
                FULL_I18N, FULL_I18N, OffsetDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC), null, List.of());
        when(alertRepository.findByCode("ALERT-6")).thenReturn(Optional.of(published));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.publish("ALERT-6", ACTOR));

        assertEquals("alert.status_invalid", ex.getCode());
        verify(auditService, never()).record(anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void publishBlockedWhenLanguageMissing() {
        Map<String, String> bodyMissingEn = new HashMap<>();
        bodyMissingEn.put("tg", "Матн");
        bodyMissingEn.put("ru", "Текст");
        ServiceAlert draft = draft("ALERT-7", FULL_I18N, bodyMissingEn);
        when(alertRepository.findByCode("ALERT-7")).thenReturn(Optional.of(draft));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.publish("ALERT-7", ACTOR));

        assertEquals("validation.i18n_incomplete", ex.getCode());
        assertEquals("draft", draft.getStatus());
        verify(auditService, never()).record(anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    private static ServiceAlert draft(String code, Map<String, String> title, Map<String, String> body) {
        return new ServiceAlert(UUID.randomUUID(), code, "info", "draft", title, body,
                OffsetDateTime.ofInstant(NOW.minusSeconds(3600), ZoneOffset.UTC), null, List.of());
    }
}
