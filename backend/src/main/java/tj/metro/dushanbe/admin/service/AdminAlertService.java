package tj.metro.dushanbe.admin.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.admin.web.dto.AlertCreateRequest;
import tj.metro.dushanbe.admin.web.dto.AlertTargetRequest;
import tj.metro.dushanbe.admin.web.dto.AlertUpdateRequest;
import tj.metro.dushanbe.alert.domain.AlertTarget;
import tj.metro.dushanbe.alert.domain.ServiceAlert;
import tj.metro.dushanbe.alert.repository.ServiceAlertRepository;
import tj.metro.dushanbe.alert.service.AlertService;
import tj.metro.dushanbe.alert.web.dto.AlertDto;
import tj.metro.dushanbe.alert.web.dto.AlertTargetDto;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.common.i18n.I18nValidator;
import tj.metro.dushanbe.network.repository.MetroLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationRepository;

/**
 * Admin-write контур сервисных уведомлений (ADM-02, ТЗ §6.2.6). Создание черновиков,
 * редактирование и публикация (draft/review/approved → published) с учётом жизненного
 * цикла и гейта полноты языков (публиковать без tg/ru/en в title/body нельзя).
 * Все переходы фиксируются в аудите (BR-ADM-1, BR-ALT-4).
 */
@Service
public class AdminAlertService {

    private static final String TARGET_LINE = "line";
    private static final String TARGET_STATION = "station";

    /** Статусы, из которых допустима публикация (жизненный цикл ТЗ §6.2.6). */
    private static final Set<String> PUBLISHABLE_FROM = Set.of("draft", "review", "approved");

    private final ServiceAlertRepository alertRepository;
    private final MetroLineRepository lineRepository;
    private final MetroStationRepository stationRepository;
    private final AuditService auditService;
    private final Clock clock;

    public AdminAlertService(ServiceAlertRepository alertRepository,
                             MetroLineRepository lineRepository,
                             MetroStationRepository stationRepository,
                             AuditService auditService, Clock clock) {
        this.alertRepository = alertRepository;
        this.lineRepository = lineRepository;
        this.stationRepository = stationRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** Создать уведомление в статусе draft (аудит alert.create). */
    @Transactional
    public AlertDto create(AlertCreateRequest request, String actor) {
        AdminSupport.requireUnique(alertRepository.existsByCode(request.code()),
                "alert.code_exists", "code", request.code());
        AdminSupport.requireIn(request.severity(), AlertService.SEVERITY_ORDER, "alert.severity_invalid", "severity");
        I18nValidator.requireAll(request.title(), "title");
        I18nValidator.requireAll(request.body(), "body");
        requireValidWindow(request.startsAt(), request.endsAt());
        List<AlertTarget> targets = validatedTargets(request.targets());

        ServiceAlert alert = new ServiceAlert(UUID.randomUUID(), request.code(), request.severity(), "draft",
                request.title(), request.body(),
                toOdt(request.startsAt()), toOdt(request.endsAt()), targets);
        ServiceAlert saved = alertRepository.save(alert);

        auditService.record(actor, "alert.create", "alert", saved.getCode(), null, snapshot(saved));
        return toDto(saved);
    }

    /** Обновить содержание уведомления по коду (аудит alert.update). */
    @Transactional
    public AlertDto update(String code, AlertUpdateRequest request, String actor) {
        ServiceAlert alert = alertRepository.findByCode(code).orElseThrow(() -> NotFoundException.alert(code));
        AdminSupport.requireIn(request.severity(), AlertService.SEVERITY_ORDER, "alert.severity_invalid", "severity");
        I18nValidator.requireAll(request.title(), "title");
        I18nValidator.requireAll(request.body(), "body");
        requireValidWindow(request.startsAt(), request.endsAt());
        List<AlertTarget> targets = validatedTargets(request.targets());

        Map<String, Object> before = snapshot(alert);
        alert.updateContent(request.severity(), request.title(), request.body(),
                toOdt(request.startsAt()), toOdt(request.endsAt()), targets);
        ServiceAlert saved = alertRepository.save(alert);

        auditService.record(actor, "alert.update", "alert", code, before, snapshot(saved));
        return toDto(saved);
    }

    /**
     * Публикация уведомления (draft/review/approved → published) с гейтом полноты
     * языков и фиксацией момента публикации. Аудит alert.publish (BR-ALT-4).
     */
    @Transactional
    public AlertDto publish(String code, String actor) {
        ServiceAlert alert = alertRepository.findByCode(code).orElseThrow(() -> NotFoundException.alert(code));
        if (!PUBLISHABLE_FROM.contains(alert.getStatus())) {
            throw new BadRequestException("alert.status_invalid",
                    "Публикация недопустима из статуса '" + alert.getStatus() + "'",
                    Map.of("code", code, "status", alert.getStatus(),
                            "allowedFrom", PUBLISHABLE_FROM.stream().sorted().toList()));
        }
        AdminSupport.requireLanguages(alert.getTitleI18n(), "title");
        AdminSupport.requireLanguages(alert.getBodyI18n(), "body");

        Map<String, Object> before = snapshot(alert);
        alert.markPublished(OffsetDateTime.now(clock));
        ServiceAlert saved = alertRepository.save(alert);

        auditService.record(actor, "alert.publish", "alert", code, before, snapshot(saved));
        return toDto(saved);
    }

    /** Проверка окна действия: endsAt (если задан) должен быть строго позже startsAt. */
    private static void requireValidWindow(Instant startsAt, Instant endsAt) {
        if (endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new BadRequestException("alert.window_invalid",
                    "Конец окна действия должен быть позже начала",
                    Map.of("startsAt", startsAt.toString(), "endsAt", endsAt.toString()));
        }
    }

    /** Валидация таргетов: тип line|station и существование кода в сети. */
    private List<AlertTarget> validatedTargets(List<AlertTargetRequest> requested) {
        List<AlertTarget> targets = new ArrayList<>();
        if (requested == null) {
            return targets;
        }
        for (AlertTargetRequest t : requested) {
            boolean known = switch (t.type()) {
                case TARGET_LINE -> lineRepository.existsByCode(t.code());
                case TARGET_STATION -> stationRepository.existsByCode(t.code());
                default -> throw new BadRequestException("alert.target_type_invalid",
                        "Тип таргета должен быть line|station: " + t.type(),
                        Map.of("type", t.type(), "code", t.code()));
            };
            if (!known) {
                throw new BadRequestException("alert.target_unknown",
                        "Таргет ссылается на несуществующий код: " + t.type() + "=" + t.code(),
                        Map.of("type", t.type(), "code", t.code()));
            }
            targets.add(new AlertTarget(t.type(), t.code()));
        }
        return targets;
    }

    private static OffsetDateTime toOdt(Instant instant) {
        return instant != null ? OffsetDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    private static AlertDto toDto(ServiceAlert alert) {
        List<AlertTargetDto> targets = alert.getTargets().stream()
                .sorted(Comparator.comparing(AlertTarget::getType).thenComparing(AlertTarget::getCode))
                .map(t -> new AlertTargetDto(t.getType(), t.getCode()))
                .toList();
        return new AlertDto(alert.getCode(), alert.getSeverity(), alert.getTitleI18n(), alert.getBodyI18n(),
                alert.getStartsAt().toInstant(),
                alert.getEndsAt() != null ? alert.getEndsAt().toInstant() : null, targets);
    }

    private static Map<String, Object> snapshot(ServiceAlert alert) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", alert.getCode());
        snapshot.put("severity", alert.getSeverity());
        snapshot.put("status", alert.getStatus());
        snapshot.put("title", alert.getTitleI18n());
        snapshot.put("body", alert.getBodyI18n());
        snapshot.put("startsAt", alert.getStartsAt() != null ? alert.getStartsAt().toInstant().toString() : null);
        snapshot.put("endsAt", alert.getEndsAt() != null ? alert.getEndsAt().toInstant().toString() : null);
        snapshot.put("publishedAt", alert.getPublishedAt() != null ? alert.getPublishedAt().toInstant().toString() : null);
        snapshot.put("targets", alert.getTargets().stream()
                .sorted(Comparator.comparing(AlertTarget::getType).thenComparing(AlertTarget::getCode))
                .map(t -> t.getType() + ":" + t.getCode()).toList());
        return snapshot;
    }
}
