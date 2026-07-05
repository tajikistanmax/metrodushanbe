package tj.metro.dushanbe.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tj.metro.dushanbe.admin.web.dto.LineCreateRequest;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.network.domain.MetroLine;
import tj.metro.dushanbe.network.repository.MetroLineRepository;
import tj.metro.dushanbe.network.web.dto.LineDto;

/**
 * Юнит-тесты AdminLineService без БД: репозиторий и AuditService — mock,
 * «сейчас» — Clock.fixed. Проверяются валидация (уникальность кода, статус,
 * полнота языков), обязательный аудит и soft-delete.
 */
class AdminLineServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-05T10:00:00Z");
    private static final String ACTOR = "admin-1";
    private static final Map<String, String> FULL_I18N =
            Map.of("tg", "Хат", "ru", "Линия", "en", "Line");

    private final MetroLineRepository lineRepository = mock(MetroLineRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AdminLineService service =
            new AdminLineService(lineRepository, auditService, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createPersistsAndRecordsAudit() {
        when(lineRepository.existsByCode("L9")).thenReturn(false);
        when(lineRepository.save(any(MetroLine.class))).thenAnswer(inv -> inv.getArgument(0));
        LineCreateRequest request = new LineCreateRequest("L9", FULL_I18N, "#123456", "planned", 9, null);

        LineDto dto = service.create(request, ACTOR);

        assertEquals("L9", dto.code());
        verify(lineRepository).save(any(MetroLine.class));
        verify(auditService).record(eq(ACTOR), eq("line.create"), eq("line"), eq("L9"), isNull(), any());
    }

    @Test
    void createRejectsDuplicateCodeWithoutAudit() {
        when(lineRepository.existsByCode("L1")).thenReturn(true);
        LineCreateRequest request = new LineCreateRequest("L1", FULL_I18N, "#123456", "planned", 1, null);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.create(request, ACTOR));

        assertEquals("line.code_exists", ex.getCode());
        verify(auditService, never()).record(anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void createRejectsInvalidStatus() {
        when(lineRepository.existsByCode("L8")).thenReturn(false);
        LineCreateRequest request = new LineCreateRequest("L8", FULL_I18N, "#123456", "bogus", 8, null);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.create(request, ACTOR));

        assertEquals("line.status_invalid", ex.getCode());
        verify(auditService, never()).record(anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void createRejectsIncompleteLanguages() {
        when(lineRepository.existsByCode("L7")).thenReturn(false);
        LineCreateRequest request = new LineCreateRequest("L7",
                Map.of("tg", "Хат", "ru", "Линия"), "#123456", "planned", 7, null);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.create(request, ACTOR));

        assertEquals("validation.i18n_incomplete", ex.getCode());
    }

    @Test
    void softDeleteMarksDeletedAndAudits() {
        MetroLine line = new MetroLine(UUID.randomUUID(), "L1", "#123456", "active", FULL_I18N, 1, null);
        when(lineRepository.findByCode("L1")).thenReturn(Optional.of(line));
        when(lineRepository.save(any(MetroLine.class))).thenAnswer(inv -> inv.getArgument(0));

        service.softDelete("L1", ACTOR);

        ArgumentCaptor<MetroLine> saved = ArgumentCaptor.forClass(MetroLine.class);
        verify(lineRepository).save(saved.capture());
        assertNotNull(saved.getValue().getDeletedAt());
        verify(auditService).record(eq(ACTOR), eq("line.delete"), eq("line"), eq("L1"), any(), any());
    }

    @Test
    void softDeleteUnknownCodeThrowsNotFound() {
        when(lineRepository.findByCode("LX")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.softDelete("LX", ACTOR));
    }
}
