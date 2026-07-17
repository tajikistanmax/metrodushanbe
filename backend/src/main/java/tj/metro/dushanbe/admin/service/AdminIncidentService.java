package tj.metro.dushanbe.admin.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.admin.web.dto.IncidentCreateRequest;
import tj.metro.dushanbe.admin.web.dto.IncidentTransitionRequest;
import tj.metro.dushanbe.admin.web.dto.IncidentUpdateRequest;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.identity.domain.AdminUser;
import tj.metro.dushanbe.identity.repository.AdminUserRepository;
import tj.metro.dushanbe.incident.domain.Incident;
import tj.metro.dushanbe.incident.domain.IncidentCategory;
import tj.metro.dushanbe.incident.domain.IncidentSeverity;
import tj.metro.dushanbe.incident.domain.IncidentStatus;
import tj.metro.dushanbe.incident.repository.IncidentRepository;
import tj.metro.dushanbe.incident.web.dto.IncidentDto;
import tj.metro.dushanbe.incident.web.dto.IncidentStatsDto;

/**
 * Операционный учёт инцидентов (INC-01).
 *
 * <p>Состояние меняется только переходами из {@link IncidentStatus}: карта
 * допустимых переходов одна и та же для валидации и для подсказок в UI.
 */
@Service
public class AdminIncidentService {

    /**
     * Что считается «требует внимания» на плитке дашборда: всё, что ещё не
     * устранено и не закрыто. Это бизнес-правило, поэтому живёт здесь, а не в
     * репозитории.
     */
    private static final List<IncidentStatus> UNRESOLVED = List.of(
            IncidentStatus.OPEN, IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS);

    private final IncidentRepository repository;
    private final AdminUserRepository userRepository;
    private final AuditService auditService;
    private final Clock clock;

    public AdminIncidentService(IncidentRepository repository, AdminUserRepository userRepository,
                                AuditService auditService, Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<IncidentDto> list(String statusFilter) {
        List<Incident> incidents = statusFilter == null || statusFilter.isBlank()
                ? repository.findAllByOrderByOccurredAtDesc()
                : repository.findByStatusOrderByOccurredAtDesc(status(statusFilter));
        return incidents.stream().map(AdminIncidentService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public IncidentDto get(String code) {
        return toDto(find(code));
    }

    /** Счётчики плиток дашборда за текущие сутки. */
    @Transactional(readOnly = true)
    public IncidentStatsDto stats() {
        OffsetDateTime startOfDay = OffsetDateTime.now(clock)
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDate()
                .atStartOfDay()
                .atOffset(ZoneOffset.UTC);

        Map<IncidentCategory, Long> counts = new EnumMap<>(IncidentCategory.class);
        for (Object[] row : repository.countByCategorySince(startOfDay)) {
            counts.put((IncidentCategory) row[0], (Long) row[1]);
        }

        // Категории без инцидентов в выдачу GROUP BY не попадают, но плитка
        // обязана показать 0, а не исчезнуть.
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (IncidentCategory category : IncidentCategory.values()) {
            byCategory.put(category.code(), counts.getOrDefault(category, 0L));
        }

        return new IncidentStatsDto(
                repository.countByOccurredAtGreaterThanEqual(startOfDay),
                repository.countByStatusIn(UNRESOLVED),
                byCategory);
    }

    @Transactional
    public IncidentDto create(IncidentCreateRequest request, String actor) {
        String assignee = normalizeAssignee(request.assignedTo());
        Incident incident = new Incident(UUID.randomUUID(), nextCode(), category(request.category()),
                severity(request.severity()), request.title(), request.description(),
                blankToNull(request.lineCode()), blankToNull(request.stationCode()),
                actor, assignee, request.occurredAt());
        Incident saved = repository.save(incident);
        auditService.record(actor, "incident.create", "incident", saved.getCode(),
                null, snapshot(saved));
        return toDto(saved);
    }

    @Transactional
    public IncidentDto update(String code, IncidentUpdateRequest request, String actor) {
        Incident incident = find(code);
        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw new BadRequestException("incident.closed",
                    "Закрытый инцидент не редактируется",
                    Map.of("code", code));
        }
        String assignee = normalizeAssignee(request.assignedTo());
        Map<String, Object> before = snapshot(incident);
        incident.updateDetails(category(request.category()), severity(request.severity()),
                request.title(), request.description(), blankToNull(request.lineCode()),
                blankToNull(request.stationCode()), assignee, request.occurredAt());
        Incident saved = repository.save(incident);
        auditService.record(actor, "incident.update", "incident", code, before, snapshot(saved));
        return toDto(saved);
    }

    /** Перевод в новое состояние с проверкой допустимости перехода. */
    @Transactional
    public IncidentDto transition(String code, IncidentTransitionRequest request, String actor) {
        Incident incident = find(code);
        IncidentStatus target = status(request.status());
        IncidentStatus current = incident.getStatus();

        if (!current.canMoveTo(target)) {
            throw new BadRequestException("incident.transition_invalid",
                    "Переход " + current.code() + " → " + target.code() + " недопустим",
                    Map.of("from", current.code(), "to", target.code(),
                            "allowed", current.allowedTransitions().stream()
                                    .map(IncidentStatus::code).sorted().toList()));
        }

        String resolution = blankToNull(request.resolution());
        // resolved обязан нести разбор: без него запись бесполезна для отчётности,
        // и БД всё равно не примет её (chk_incident_resolution).
        if (target == IncidentStatus.RESOLVED && resolution == null) {
            throw new BadRequestException("incident.resolution_required",
                    "Для перевода в 'resolved' нужен разбор",
                    Map.of("field", "resolution"));
        }

        Map<String, Object> before = snapshot(incident);
        incident.moveTo(target, resolution, OffsetDateTime.now(clock));
        Incident saved = repository.save(incident);
        auditService.record(actor, "incident.transition", "incident", code, before, snapshot(saved));
        return toDto(saved);
    }

    /**
     * Генерирует код вида INC-2026-0001.
     *
     * <p>Гонка при одновременной регистрации возможна, но безопасна: уникальный
     * индекс на incident.code отвергнет дубль, и операция будет повторена
     * клиентом. Секвенция на БД была бы надёжнее, но не даёт нумерации,
     * перезапускающейся с начала года.
     */
    private String nextCode() {
        int year = OffsetDateTime.now(clock).getYear();
        String prefix = "INC-" + year + "-";
        int next = repository.findMaxCodeWithPrefix(prefix + "%")
                .map(max -> parseSequence(max, prefix) + 1)
                .orElse(1);
        return prefix + String.format("%04d", next);
    }

    private static int parseSequence(String code, String prefix) {
        try {
            return Integer.parseInt(code.substring(prefix.length()));
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            // Код руками правили или формат менялся — начинаем новую нумерацию,
            // а не роняем регистрацию инцидента.
            return 0;
        }
    }

    /** Назначать можно только существующего оператора — иначе задача повиснет. */
    private String normalizeAssignee(String assignedTo) {
        String username = AdminUser.normalizeUsername(blankToNull(assignedTo));
        if (username == null) {
            return null;
        }
        if (!userRepository.existsByUsername(username)) {
            throw new BadRequestException("incident.assignee_unknown",
                    "Оператор '" + username + "' не найден",
                    Map.of("field", "assignedTo", "value", username));
        }
        return username;
    }

    private Incident find(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("incident.not_found", "Инцидент не найден"));
    }

    private static IncidentStatus status(String code) {
        return IncidentStatus.fromCode(code).orElseThrow(() ->
                new BadRequestException("incident.status_invalid", "Недопустимый статус: " + code,
                        Map.of("field", "status", "allowed", IncidentStatus.codes())));
    }

    private static IncidentSeverity severity(String code) {
        return IncidentSeverity.fromCode(code).orElseThrow(() ->
                new BadRequestException("incident.severity_invalid", "Недопустимая критичность: " + code,
                        Map.of("field", "severity", "allowed", IncidentSeverity.codes())));
    }

    private static IncidentCategory category(String code) {
        return IncidentCategory.fromCode(code).orElseThrow(() ->
                new BadRequestException("incident.category_invalid", "Недопустимая категория: " + code,
                        Map.of("field", "category", "allowed", IncidentCategory.codes())));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, Object> snapshot(Incident incident) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", incident.getCode());
        snapshot.put("category", incident.getCategory().code());
        snapshot.put("severity", incident.getSeverity().code());
        snapshot.put("status", incident.getStatus().code());
        snapshot.put("title", incident.getTitle());
        snapshot.put("lineCode", incident.getLineCode());
        snapshot.put("stationCode", incident.getStationCode());
        snapshot.put("assignedTo", incident.getAssignedTo());
        return snapshot;
    }

    public static IncidentDto toDto(Incident incident) {
        return new IncidentDto(
                incident.getCode(),
                incident.getCategory().code(),
                incident.getSeverity().code(),
                incident.getStatus().code(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getLineCode(),
                incident.getStationCode(),
                incident.getReportedBy(),
                incident.getAssignedTo(),
                incident.getResolution(),
                incident.getPublicAlertCode(),
                incident.getOccurredAt(),
                incident.getAcknowledgedAt(),
                incident.getResolvedAt(),
                incident.getClosedAt(),
                incident.getUpdatedAt(),
                incident.getStatus().allowedTransitions().stream()
                        .map(IncidentStatus::code).sorted().toList());
    }
}
