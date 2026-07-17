package tj.metro.dushanbe.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tj.metro.dushanbe.admin.web.dto.IncidentCreateRequest;
import tj.metro.dushanbe.admin.web.dto.IncidentTransitionRequest;
import tj.metro.dushanbe.admin.web.dto.IncidentUpdateRequest;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.identity.repository.AdminUserRepository;
import tj.metro.dushanbe.incident.domain.Incident;
import tj.metro.dushanbe.incident.domain.IncidentCategory;
import tj.metro.dushanbe.incident.domain.IncidentSeverity;
import tj.metro.dushanbe.incident.domain.IncidentStatus;
import tj.metro.dushanbe.incident.repository.IncidentRepository;

class AdminIncidentServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-17T10:15:30Z");
    private static final OffsetDateTime OCCURRED = OffsetDateTime.parse("2026-07-17T09:00:00Z");

    private final IncidentRepository repository = mock(IncidentRepository.class);
    private final AdminUserRepository userRepository = mock(AdminUserRepository.class);
    private final AuditService auditService = mock(AuditService.class);

    private final AdminIncidentService service = new AdminIncidentService(
            repository, userRepository, auditService, Clock.fixed(NOW, ZoneOffset.UTC));

    // --- Регистрация ---------------------------------------------------------

    @Test
    void createStartsOpenAndGeneratesFirstCodeOfYear() {
        when(repository.findMaxCodeWithPrefix(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));

        var result = service.create(request(null), "operator1");

        assertEquals("INC-2026-0001", result.code());
        assertEquals("open", result.status());
        assertEquals("operator1", result.reportedBy());
        verify(auditService).record(eq("operator1"), eq("incident.create"), eq("incident"),
                eq("INC-2026-0001"), isNull(), any());
    }

    @Test
    void createContinuesYearSequence() {
        when(repository.findMaxCodeWithPrefix("INC-2026-%")).thenReturn(Optional.of("INC-2026-0041"));
        when(repository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals("INC-2026-0042", service.create(request(null), "operator1").code());
    }

    @Test
    void createRecoversFromUnparseableExistingCode() {
        // Код правили руками — регистрацию инцидента это ронять не должно.
        when(repository.findMaxCodeWithPrefix("INC-2026-%")).thenReturn(Optional.of("INC-2026-BROKEN"));
        when(repository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals("INC-2026-0001", service.create(request(null), "operator1").code());
    }

    @Test
    void createRejectsUnknownAssignee() {
        when(repository.findMaxCodeWithPrefix(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("ghost")).thenReturn(false);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.create(request("ghost"), "operator1"));

        assertEquals("incident.assignee_unknown", error.getCode());
        verify(repository, never()).save(any());
    }

    @Test
    void createAcceptsKnownAssigneeCaseInsensitively() {
        when(repository.findMaxCodeWithPrefix(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("operator2")).thenReturn(true);
        when(repository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals("operator2", service.create(request("OPERATOR2"), "operator1").assignedTo());
    }

    // --- Переходы ------------------------------------------------------------

    @Test
    void acknowledgeSetsTimestamp() {
        Incident incident = incident(IncidentStatus.OPEN);
        stubFind(incident);

        var result = service.transition("INC-2026-0001", transition("acknowledged", null), "operator1");

        assertEquals("acknowledged", result.status());
        assertEquals(NOW.atOffset(ZoneOffset.UTC), incident.getAcknowledgedAt());
    }

    @Test
    void transitionRejectsIllegalJump() {
        Incident incident = incident(IncidentStatus.OPEN);
        stubFind(incident);

        BadRequestException error = assertThrows(BadRequestException.class, () ->
                service.transition("INC-2026-0001", transition("resolved", "готово"), "operator1"));

        assertEquals("incident.transition_invalid", error.getCode());
        assertEquals(IncidentStatus.OPEN, incident.getStatus());
    }

    @Test
    void resolveRequiresResolution() {
        Incident incident = incident(IncidentStatus.IN_PROGRESS);
        stubFind(incident);

        BadRequestException error = assertThrows(BadRequestException.class, () ->
                service.transition("INC-2026-0001", transition("resolved", "   "), "operator1"));

        assertEquals("incident.resolution_required", error.getCode());
        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
    }

    @Test
    void resolveStoresResolutionAndTimestamp() {
        Incident incident = incident(IncidentStatus.IN_PROGRESS);
        stubFind(incident);

        var result = service.transition("INC-2026-0001",
                transition("resolved", "Заменён блок питания"), "operator1");

        assertEquals("resolved", result.status());
        assertEquals("Заменён блок питания", result.resolution());
        assertEquals(NOW.atOffset(ZoneOffset.UTC), incident.getResolvedAt());
    }

    @Test
    void reopenClearsResolvedTimestamp() {
        Incident incident = incident(IncidentStatus.IN_PROGRESS);
        stubFind(incident);
        service.transition("INC-2026-0001", transition("resolved", "разбор"), "operator1");

        service.transition("INC-2026-0001", transition("in_progress", null), "operator1");

        // Инцидент не может числиться одновременно в работе и устранённым.
        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
        assertNull(incident.getResolvedAt());
    }

    @Test
    void reopenKeepsFirstAcknowledgementTime() {
        Incident incident = incident(IncidentStatus.OPEN);
        stubFind(incident);
        service.transition("INC-2026-0001", transition("acknowledged", null), "operator1");
        OffsetDateTime firstReaction = incident.getAcknowledgedAt();

        service.transition("INC-2026-0001", transition("in_progress", null), "operator1");
        service.transition("INC-2026-0001", transition("acknowledged", null), "operator1");

        // Время ПЕРВОЙ реакции — метрика SLA; повторный ack его не переписывает.
        assertEquals(firstReaction, incident.getAcknowledgedAt());
    }

    @Test
    void closedIsTerminal() {
        Incident incident = incident(IncidentStatus.CLOSED);
        stubFind(incident);

        BadRequestException error = assertThrows(BadRequestException.class, () ->
                service.transition("INC-2026-0001", transition("in_progress", null), "operator1"));

        assertEquals("incident.transition_invalid", error.getCode());
    }

    @Test
    void dtoExposesAllowedTransitionsForCurrentStatus() {
        Incident incident = incident(IncidentStatus.IN_PROGRESS);
        stubFind(incident);

        var result = service.get("INC-2026-0001");

        assertEquals(List.of("acknowledged", "resolved"), result.allowedTransitions());
    }

    @Test
    void closedIncidentExposesNoTransitions() {
        stubFind(incident(IncidentStatus.CLOSED));

        assertTrue(service.get("INC-2026-0001").allowedTransitions().isEmpty());
    }

    // --- Редактирование ------------------------------------------------------

    @Test
    void updateRejectsClosedIncident() {
        stubFind(incident(IncidentStatus.CLOSED));

        BadRequestException error = assertThrows(BadRequestException.class, () ->
                service.update("INC-2026-0001", updateRequest(), "operator1"));

        assertEquals("incident.closed", error.getCode());
    }

    @Test
    void updateChangesEditableFields() {
        Incident incident = incident(IncidentStatus.OPEN);
        stubFind(incident);

        var result = service.update("INC-2026-0001", updateRequest(), "operator1");

        assertEquals("critical", result.severity());
        assertEquals("safety", result.category());
        assertEquals("Новый заголовок", result.title());
    }

    @Test
    void getUnknownIncidentReturnsDomainNotFound() {
        when(repository.findByCode("INC-2026-9999")).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(NotFoundException.class,
                () -> service.get("INC-2026-9999"));

        assertEquals("incident.not_found", error.getCode());
    }

    // --- Счётчики ------------------------------------------------------------

    @Test
    void statsReportsZeroForCategoriesWithoutIncidents() {
        // List.<Object[]>of — иначе varargs даёт List<Object>, а не List<Object[]>.
        when(repository.countByCategorySince(any())).thenReturn(List.<Object[]>of(
                new Object[] {IncidentCategory.TECHNICAL, 3L}));
        when(repository.countByOccurredAtGreaterThanEqual(any())).thenReturn(3L);
        when(repository.countByStatusIn(List.of(IncidentStatus.OPEN, IncidentStatus.ACKNOWLEDGED,
                IncidentStatus.IN_PROGRESS))).thenReturn(2L);

        var stats = service.stats();

        assertEquals(3, stats.today());
        assertEquals(2, stats.open());
        assertEquals(3L, stats.byCategory().get("technical"));
        // Плитка категории без инцидентов обязана показать 0, а не пропасть.
        assertEquals(0L, stats.byCategory().get("safety"));
        assertEquals(IncidentCategory.values().length, stats.byCategory().size());
    }

    // --- Хелперы -------------------------------------------------------------

    private void stubFind(Incident incident) {
        when(repository.findByCode("INC-2026-0001")).thenReturn(Optional.of(incident));
        when(repository.save(incident)).thenReturn(incident);
    }

    private static Incident incident(IncidentStatus status) {
        Incident incident = new Incident(UUID.randomUUID(), "INC-2026-0001",
                IncidentCategory.TECHNICAL, IncidentSeverity.HIGH, "Отказ эскалатора",
                "Эскалатор №2 остановлен", "L1", "ST-CIRCUS", "operator1", null, OCCURRED);
        if (status != IncidentStatus.OPEN) {
            // Проводим через легальную цепочку, чтобы состояние было достижимым.
            incident.moveTo(IncidentStatus.ACKNOWLEDGED, null, OCCURRED);
            if (status != IncidentStatus.ACKNOWLEDGED) {
                incident.moveTo(IncidentStatus.IN_PROGRESS, null, OCCURRED);
                if (status == IncidentStatus.RESOLVED || status == IncidentStatus.CLOSED) {
                    incident.moveTo(IncidentStatus.RESOLVED, "разбор", OCCURRED);
                }
                if (status == IncidentStatus.CLOSED) {
                    incident.moveTo(IncidentStatus.CLOSED, null, OCCURRED);
                }
            }
        }
        return incident;
    }

    private static IncidentCreateRequest request(String assignedTo) {
        return new IncidentCreateRequest("technical", "high", "Отказ эскалатора",
                "Эскалатор №2 остановлен", "L1", "ST-CIRCUS", assignedTo, OCCURRED);
    }

    private static IncidentUpdateRequest updateRequest() {
        return new IncidentUpdateRequest("safety", "critical", "Новый заголовок",
                "Обновлённое описание", "L1", "ST-CIRCUS", null, OCCURRED);
    }

    private static IncidentTransitionRequest transition(String status, String resolution) {
        return new IncidentTransitionRequest(status, resolution);
    }
}
