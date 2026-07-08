package tj.metro.dushanbe.alert.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.alert.domain.AlertTarget;
import tj.metro.dushanbe.alert.domain.ServiceAlert;
import tj.metro.dushanbe.alert.repository.ServiceAlertRepository;
import tj.metro.dushanbe.alert.web.dto.AlertDto;
import tj.metro.dushanbe.alert.web.dto.AlertTargetDto;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.network.repository.MetroStationLineRepository;

/**
 * Публичный контур сервисных уведомлений (ТЗ §6.2.6): отдаёт только
 * активные published-уведомления в окне действия
 * {@code starts_at <= now < ends_at} ({@code ends_at} NULL = бессрочно).
 * Предикаты окна вычисляются в БД ({@link ServiceAlertRepository#findActivePublished});
 * «сейчас» берётся из инжектируемого {@link Clock} — для тестируемости.
 *
 * <p>Для фильтра по станции принадлежность станции линиям резолвится через
 * {@link MetroStationLineRepository} модуля network: read-only использование
 * репозитория соседнего модуля — осознанное допущение модульного монолита
 * (alert зависит от network, но не изменяет его данные).
 */
@Service
@Transactional(readOnly = true)
public class AlertService {

    /** Уровни важности в порядке убывания приоритета (порядок сортировки выдачи). */
    public static final List<String> SEVERITY_ORDER = List.of("critical", "warning", "info");

    private static final String TARGET_LINE = "line";
    private static final String TARGET_STATION = "station";

    /** Сначала по severity (critical -> warning -> info), внутри — starts_at по убыванию. */
    private static final Comparator<ServiceAlert> ALERT_ORDER = Comparator
            .comparingInt((ServiceAlert a) -> severityRank(a.getSeverity()))
            .thenComparing(ServiceAlert::getStartsAt, Comparator.reverseOrder())
            .thenComparing(ServiceAlert::getCode);

    private final ServiceAlertRepository repository;
    private final MetroStationLineRepository stationLineRepository;
    private final Clock clock;

    public AlertService(ServiceAlertRepository repository,
                        MetroStationLineRepository stationLineRepository,
                        Clock clock) {
        this.repository = repository;
        this.stationLineRepository = stationLineRepository;
        this.clock = clock;
    }

    /**
     * Активные уведомления с опциональными фильтрами. Семантика таргет-фильтров:
     * <ul>
     *   <li>network-wide уведомления (без таргетов) затрагивают всю сеть
     *       и попадают в выдачу при любом lineCode/stationCode;</li>
     *   <li>{@code lineCode}: уведомление попадает, если таргетировано этой линией
     *       (станционные таргеты линию не расширяют);</li>
     *   <li>{@code stationCode}: уведомление попадает, если таргетировано этой станцией
     *       ИЛИ любой линией, которой станция принадлежит (связь станция-линия
     *       модуля network);</li>
     *   <li>оба фильтра сразу — ОБЪЕДИНЕНИЕ: уведомление попадает, если проходит
     *       хотя бы один из фильтров («не потерять уведомление» важнее строгости).</li>
     * </ul>
     */
    @Cacheable(value = "alerts", unless = "#result.isEmpty()")
    public List<AlertDto> activeAlerts(String lineCode, String stationCode, String severity) {
        if (!isBlank(severity)) {
            requireValidSeverity(severity);
        }
        Set<String> stationLineCodes = isBlank(stationCode)
                ? Set.of()
                : Set.copyOf(stationLineRepository.findLineCodesByStationCode(stationCode));
        OffsetDateTime now = OffsetDateTime.now(clock);
        return repository.findActivePublished(now).stream()
                .filter(alert -> isBlank(severity) || severity.equals(alert.getSeverity()))
                .filter(alert -> matchesTargetFilters(alert, lineCode, stationCode, stationLineCodes))
                .sorted(ALERT_ORDER)
                .map(this::toDto)
                .toList();
    }

    /**
     * Комбинированный таргет-фильтр (семантика — javadoc {@link #activeAlerts}):
     * без фильтров и для network-wide уведомлений — проходит всегда;
     * lineCode — прямой line-таргет; stationCode — прямой station-таргет
     * ИЛИ line-таргет любой линии станции; оба фильтра — объединение.
     */
    private static boolean matchesTargetFilters(ServiceAlert alert, String lineCode,
                                                String stationCode, Set<String> stationLineCodes) {
        boolean byLine = !isBlank(lineCode);
        boolean byStation = !isBlank(stationCode);
        if ((!byLine && !byStation) || alert.getTargets().isEmpty()) {
            return true;
        }
        if (byLine && hasTarget(alert, TARGET_LINE, lineCode)) {
            return true;
        }
        return byStation
                && (hasTarget(alert, TARGET_STATION, stationCode)
                        || alert.getTargets().stream().anyMatch(
                                t -> TARGET_LINE.equals(t.getType()) && stationLineCodes.contains(t.getCode())));
    }

    private static boolean hasTarget(ServiceAlert alert, String targetType, String targetCode) {
        return alert.getTargets().stream()
                .anyMatch(t -> targetType.equals(t.getType()) && targetCode.equals(t.getCode()));
    }

    private static int severityRank(String severity) {
        int rank = SEVERITY_ORDER.indexOf(severity);
        return rank >= 0 ? rank : SEVERITY_ORDER.size(); // неизвестное — в конец
    }

    private AlertDto toDto(ServiceAlert alert) {
        List<AlertTargetDto> targets = alert.getTargets().stream()
                .sorted(Comparator.comparing(AlertTarget::getType).thenComparing(AlertTarget::getCode))
                .map(t -> new AlertTargetDto(t.getType(), t.getCode()))
                .toList();
        return new AlertDto(
                alert.getCode(),
                alert.getSeverity(),
                alert.getTitleI18n(),
                alert.getBodyI18n(),
                alert.getStartsAt().toInstant(),
                alert.getEndsAt() != null ? alert.getEndsAt().toInstant() : null,
                targets);
    }

    private void requireValidSeverity(String severity) {
        if (!SEVERITY_ORDER.contains(severity)) {
            throw new BadRequestException("alert.severity_invalid",
                    "Недопустимое значение параметра 'severity': " + severity,
                    Map.of("parameter", "severity", "value", severity, "allowed", SEVERITY_ORDER));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
