package tj.metro.dushanbe.citizen.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.citizen.domain.CitizenRequest;
import tj.metro.dushanbe.citizen.repository.CitizenRequestRepository;
import tj.metro.dushanbe.citizen.web.dto.CitizenRequestCreateRequest;
import tj.metro.dushanbe.citizen.web.dto.CitizenRequestUpdateRequest;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.network.repository.MetroLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationRepository;

class CitizenRequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-16T08:00:00Z");

    private final CitizenRequestRepository repository = mock(CitizenRequestRepository.class);
    private final MetroLineRepository lineRepository = mock(MetroLineRepository.class);
    private final MetroStationRepository stationRepository = mock(MetroStationRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final CitizenRequestService service = new CitizenRequestService(
            repository, lineRepository, stationRepository, auditService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void saveReturnsEntity() {
        when(repository.save(any(CitizenRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.existsByPublicCode(anyString())).thenReturn(false);
    }

    @Test
    void createReturnsSecretButStoresOnlyHashAndIncidentSla() {
        var response = service.create(new CitizenRequestCreateRequest(
                "incident", "  Эскалатор остановился  ", "  Подробности  ",
                " Али ", " ali@example.com ", "+992 900 00 00 00", null, null, true));

        assertTrue(response.request().code().matches("REQ-2026-[0-9A-F]{8}"));
        assertEquals("new", response.request().status());
        assertFalse(response.trackingToken().isBlank());

        ArgumentCaptor<CitizenRequest> saved = ArgumentCaptor.forClass(CitizenRequest.class);
        verify(repository).save(saved.capture());
        assertEquals("high", saved.getValue().getPriority());
        assertEquals("Эскалатор остановился", saved.getValue().getSubject());
        assertEquals(64, saved.getValue().getTrackingTokenHash().length());
        assertFalse(saved.getValue().getTrackingTokenHash().contains(response.trackingToken()));
        assertEquals(NOW.plusSeconds(3600), saved.getValue().getResponseDueAt().toInstant());
        assertEquals(NOW.plusSeconds(86400), saved.getValue().getResolutionDueAt().toInstant());
        verify(auditService).record(anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void trackAcceptsIssuedSecretAndRejectsWrongSecretWithGenericNotFound() {
        var created = service.create(new CitizenRequestCreateRequest(
                "question", "Когда откроется?", "Сообщите дату", null, null, null,
                null, null, true));
        ArgumentCaptor<CitizenRequest> saved = ArgumentCaptor.forClass(CitizenRequest.class);
        verify(repository).save(saved.capture());
        when(repository.findByPublicCode(created.request().code())).thenReturn(Optional.of(saved.getValue()));

        assertEquals(created.request(), service.track(created.request().code(), created.trackingToken()));
        NotFoundException error = assertThrows(NotFoundException.class,
                () -> service.track(created.request().code(), "wrong-secret"));
        assertEquals("request.not_found", error.getCode());
    }

    @Test
    void updateEnforcesWorkflowAndRequiresPublishedResponse() {
        CitizenRequest request = requestEntity();
        when(repository.findByPublicCode(request.getPublicCode())).thenReturn(Optional.of(request));

        BadRequestException invalid = assertThrows(BadRequestException.class,
                () -> service.update(request.getPublicCode(),
                        new CitizenRequestUpdateRequest("resolved", "Готово", "operator-1"),
                        "admin"));
        assertEquals("request.transition_invalid", invalid.getCode());

        service.update(request.getPublicCode(),
                new CitizenRequestUpdateRequest("in_progress", null, "operator-1"), "admin");
        BadRequestException missingResponse = assertThrows(BadRequestException.class,
                () -> service.update(request.getPublicCode(),
                        new CitizenRequestUpdateRequest("resolved", " ", "operator-1"),
                        "admin"));
        assertEquals("request.response_required", missingResponse.getCode());

        var resolved = service.update(request.getPublicCode(),
                new CitizenRequestUpdateRequest("resolved", "Вопрос решён", "operator-1"),
                "admin");
        assertEquals("resolved", resolved.status());
        assertEquals("Вопрос решён", resolved.response());
        assertEquals("operator-1", resolved.assignedTo());
        assertNotNull(resolved.resolvedAt());

        var closed = service.update(request.getPublicCode(),
                new CitizenRequestUpdateRequest("closed", null, "operator-1"), "admin");
        assertEquals("closed", closed.status());
        assertEquals("Вопрос решён", closed.response());
    }

    private static CitizenRequest requestEntity() {
        var now = java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        return new CitizenRequest(java.util.UUID.randomUUID(), "REQ-2026-A1B2C3D4", "a".repeat(64),
                "complaint", "normal", "Тема", "Текст", null, null, null,
                null, null, now.plusHours(24), now.plusDays(7), now);
    }
}
