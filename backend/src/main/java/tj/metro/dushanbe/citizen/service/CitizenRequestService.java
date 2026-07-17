package tj.metro.dushanbe.citizen.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.citizen.domain.CitizenRequest;
import tj.metro.dushanbe.citizen.repository.CitizenRequestRepository;
import tj.metro.dushanbe.citizen.web.dto.CitizenRequestAdminDto;
import tj.metro.dushanbe.citizen.web.dto.CitizenRequestCreateRequest;
import tj.metro.dushanbe.citizen.web.dto.CitizenRequestCreateResponse;
import tj.metro.dushanbe.citizen.web.dto.CitizenRequestPublicDto;
import tj.metro.dushanbe.citizen.web.dto.CitizenRequestUpdateRequest;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.network.repository.MetroLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationRepository;

/** Сквозной сервис обращений: подача, защищённый трекинг и workflow оператора. */
@Service
public class CitizenRequestService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> TYPES = Set.of(
            "complaint", "suggestion", "incident", "question", "lost_item");
    private static final Set<String> STATUSES = Set.of(
            "new", "in_progress", "awaiting_info", "resolved", "closed", "reopened");
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "new", Set.of("in_progress"),
            "in_progress", Set.of("awaiting_info", "resolved"),
            "awaiting_info", Set.of("in_progress"),
            "resolved", Set.of("closed", "reopened"),
            "reopened", Set.of("in_progress", "resolved"),
            "closed", Set.of());

    private final CitizenRequestRepository repository;
    private final MetroLineRepository lineRepository;
    private final MetroStationRepository stationRepository;
    private final AuditService auditService;
    private final Clock clock;

    public CitizenRequestService(CitizenRequestRepository repository,
                                 MetroLineRepository lineRepository,
                                 MetroStationRepository stationRepository,
                                 AuditService auditService,
                                 Clock clock) {
        this.repository = repository;
        this.lineRepository = lineRepository;
        this.stationRepository = stationRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** Создаёт обращение и возвращает одноразово показываемый секрет трекинга. */
    @Transactional
    public CitizenRequestCreateResponse create(CitizenRequestCreateRequest request) {
        requireType(request.type());
        String lineCode = normalize(request.lineCode());
        String stationCode = normalize(request.stationCode());
        validateReferences(lineCode, stationCode);

        OffsetDateTime now = OffsetDateTime.now(clock);
        String token = newTrackingToken();
        String publicCode = newPublicCode();
        Sla sla = slaFor(request.type());
        CitizenRequest entity = new CitizenRequest(
                UUID.randomUUID(), publicCode, hash(token), request.type(), priorityFor(request.type()),
                request.subject().trim(), request.message().trim(), normalize(request.contactName()),
                normalize(request.contactEmail()), normalize(request.contactPhone()), lineCode, stationCode,
                now.plus(sla.response()), now.plus(sla.resolution()), now);
        CitizenRequest saved = repository.save(entity);
        auditService.record("public", "request.create", "citizen_request", publicCode,
                null, auditSnapshot(saved));
        return new CitizenRequestCreateResponse(toPublic(saved), token);
    }

    /** Возвращает публичный статус только при корректной паре code+token. */
    @Transactional(readOnly = true)
    public CitizenRequestPublicDto track(String code, String trackingToken) {
        CitizenRequest entity = repository.findByPublicCode(code.trim())
                .orElseThrow(CitizenRequestService::notFound);
        if (!constantTimeEquals(entity.getTrackingTokenHash(), hash(trackingToken))) {
            throw notFound();
        }
        return toPublic(entity);
    }

    /** Операторская очередь, новые сверху; опционально фильтруется по статусу. */
    @Transactional(readOnly = true)
    public List<CitizenRequestAdminDto> list(String status) {
        String normalized = normalize(status);
        if (normalized != null && !STATUSES.contains(normalized)) {
            throw new BadRequestException("request.status_invalid", "Недопустимый статус обращения",
                    Map.of("status", normalized));
        }
        List<CitizenRequest> items = normalized == null
                ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findByStatusOrderByCreatedAtDesc(normalized);
        OffsetDateTime now = OffsetDateTime.now(clock);
        return items.stream().map(item -> toAdmin(item, now)).toList();
    }

    /** Применяет допустимый переход workflow и фиксирует его в аудите. */
    @Transactional
    public CitizenRequestAdminDto update(String code, CitizenRequestUpdateRequest request,
                                         String actor) {
        CitizenRequest entity = repository.findByPublicCode(code)
                .orElseThrow(CitizenRequestService::notFound);
        requireTransition(entity.getStatus(), request.status());
        String response = normalize(request.response());
        if (("awaiting_info".equals(request.status()) || "resolved".equals(request.status()))
                && response == null) {
            throw new BadRequestException("request.response_required",
                    "Для выбранного статуса требуется ответ гражданину",
                    Map.of("status", request.status()));
        }

        Map<String, Object> before = auditSnapshot(entity);
        entity.updateWorkflow(request.status(), response, normalize(request.assignedTo()),
                OffsetDateTime.now(clock));
        CitizenRequest saved = repository.save(entity);
        auditService.record(actor, "request.status_update", "citizen_request", code,
                before, auditSnapshot(saved));
        return toAdmin(saved, OffsetDateTime.now(clock));
    }

    private void validateReferences(String lineCode, String stationCode) {
        if (lineCode != null && lineRepository.findByCodeAndDeletedAtIsNull(lineCode).isEmpty()) {
            throw NotFoundException.line(lineCode);
        }
        if (stationCode != null && stationRepository.findByCodeAndDeletedAtIsNull(stationCode).isEmpty()) {
            throw NotFoundException.station(stationCode);
        }
    }

    private static void requireType(String type) {
        if (!TYPES.contains(type)) {
            throw new BadRequestException("request.type_invalid", "Недопустимый тип обращения",
                    Map.of("type", type));
        }
    }

    private static void requireTransition(String current, String next) {
        if (current.equals(next)) {
            return;
        }
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw new BadRequestException("request.transition_invalid",
                    "Недопустимый переход статуса обращения",
                    Map.of("from", current, "to", next));
        }
    }

    private String newPublicCode() {
        String year = String.valueOf(Year.now(clock).getValue());
        for (int attempt = 0; attempt < 20; attempt++) {
            String suffix = HexFormat.of().toHexDigits(RANDOM.nextInt()).toUpperCase(Locale.ROOT);
            String code = "REQ-" + year + "-" + suffix;
            if (!repository.existsByPublicCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Не удалось сгенерировать уникальный номер обращения");
    }

    private static String newTrackingToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String priorityFor(String type) {
        return switch (type) {
            case "incident" -> "high";
            case "suggestion", "question" -> "low";
            default -> "normal";
        };
    }

    private static Sla slaFor(String type) {
        return switch (type) {
            case "incident" -> new Sla(Duration.ofHours(1), Duration.ofHours(24));
            case "suggestion" -> new Sla(Duration.ofHours(72), Duration.ofDays(14));
            case "question" -> new Sla(Duration.ofHours(48), Duration.ofDays(7));
            case "lost_item" -> new Sla(Duration.ofHours(24), Duration.ofDays(14));
            default -> new Sla(Duration.ofHours(24), Duration.ofDays(7));
        };
    }

    private static CitizenRequestPublicDto toPublic(CitizenRequest entity) {
        return new CitizenRequestPublicDto(entity.getPublicCode(), entity.getType(),
                entity.getStatus(), entity.getSubject(), entity.getResponse(),
                entity.getCreatedAt().toInstant(), entity.getUpdatedAt().toInstant());
    }

    private static CitizenRequestAdminDto toAdmin(CitizenRequest entity, OffsetDateTime now) {
        boolean responseBreached = "new".equals(entity.getStatus())
                && now.isAfter(entity.getResponseDueAt());
        boolean resolutionBreached = !Set.of("resolved", "closed").contains(entity.getStatus())
                && now.isAfter(entity.getResolutionDueAt());
        return new CitizenRequestAdminDto(
                entity.getPublicCode(), entity.getType(), entity.getPriority(), entity.getStatus(),
                entity.getSubject(), entity.getMessage(), entity.getContactName(),
                entity.getContactEmail(), entity.getContactPhone(), entity.getLineCode(),
                entity.getStationCode(), entity.getResponse(), entity.getAssignedTo(),
                entity.getResponseDueAt().toInstant(), entity.getResolutionDueAt().toInstant(),
                responseBreached, resolutionBreached, entity.getCreatedAt().toInstant(),
                entity.getUpdatedAt().toInstant(),
                entity.getResolvedAt() == null ? null : entity.getResolvedAt().toInstant());
    }

    private static Map<String, Object> auditSnapshot(CitizenRequest entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", entity.getPublicCode());
        snapshot.put("type", entity.getType());
        snapshot.put("priority", entity.getPriority());
        snapshot.put("status", entity.getStatus());
        snapshot.put("assignedTo", entity.getAssignedTo());
        snapshot.put("updatedAt", entity.getUpdatedAt().toInstant().toString());
        return snapshot;
    }

    private static NotFoundException notFound() {
        return new NotFoundException("request.not_found", "Обращение не найдено");
    }

    private record Sla(Duration response, Duration resolution) {
    }
}
