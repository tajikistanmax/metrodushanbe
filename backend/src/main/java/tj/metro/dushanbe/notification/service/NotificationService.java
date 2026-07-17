package tj.metro.dushanbe.notification.service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.network.repository.MetroStationLineRepository;
import tj.metro.dushanbe.notification.domain.NotificationChannel;
import tj.metro.dushanbe.notification.domain.NotificationDelivery;
import tj.metro.dushanbe.notification.domain.NotificationMessage;
import tj.metro.dushanbe.notification.domain.NotificationStatus;
import tj.metro.dushanbe.notification.domain.NotificationTarget;
import tj.metro.dushanbe.notification.domain.NotificationTemplate;
import tj.metro.dushanbe.notification.domain.TargetType;
import tj.metro.dushanbe.notification.repository.NotificationMessageRepository;
import tj.metro.dushanbe.notification.web.dto.NotificationDeliveryDto;
import tj.metro.dushanbe.notification.web.dto.NotificationDto;
import tj.metro.dushanbe.notification.web.dto.NotificationTargetDto;
import tj.metro.dushanbe.notification.web.dto.NotificationTemplateDto;

/**
 * Публичный контур уведомлений (NTF-01…04): in-app-фид.
 *
 * <p>В фид попадают только ОТПРАВЛЕННЫЕ рассылки ({@code sent}) с каналом
 * {@code in_app}: черновик и запланированная получателю не видны, а рассылка без
 * in_app ушла в push/email/SMS и в ленте приложения ей делать нечего (NTF-01).
 *
 * <p>Семантика таргет-фильтров намеренно совпадает с {@code AlertService}
 * (docs/dev-conventions.md §3) — оператор адресует рассылку теми же правилами,
 * что и алерт, и не должен учить второй набор. Принадлежность станции линиям
 * резолвится через {@link MetroStationLineRepository} модуля network: read-only
 * использование репозитория соседнего модуля — то же осознанное допущение
 * модульного монолита, что и в alert.
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    /** Свежие сверху: для ленты важен момент отправки, а не создания черновика. */
    private static final Comparator<NotificationMessage> FEED_ORDER = Comparator
            .comparing(NotificationMessage::getSentAt,
                    Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(NotificationMessage::getCode);

    private final NotificationMessageRepository repository;
    private final MetroStationLineRepository stationLineRepository;

    public NotificationService(NotificationMessageRepository repository,
                               MetroStationLineRepository stationLineRepository) {
        this.repository = repository;
        this.stationLineRepository = stationLineRepository;
    }

    /**
     * In-app-фид с опциональными фильтрами. Семантика таргет-фильтров:
     * <ul>
     *   <li>рассылки без таргетов (вся сеть) попадают в выдачу всегда, при любом
     *       lineCode/stationCode;</li>
     *   <li>{@code lineCode}: рассылка попадает, если таргетирована этой линией
     *       (станционные таргеты линию не расширяют);</li>
     *   <li>{@code stationCode}: рассылка попадает, если таргетирована этой станцией
     *       ИЛИ любой линией, которой станция принадлежит;</li>
     *   <li>оба фильтра сразу — ОБЪЕДИНЕНИЕ: рассылка попадает, если проходит хотя
     *       бы один из фильтров («не потерять уведомление» важнее строгости).</li>
     * </ul>
     * Таргеты segment/role географическими фильтрами не выбираются — они адресуют
     * аудиторию, а не объект сети (см. {@link TargetType#geographic()}).
     */
    @Cacheable("notifications")
    public List<NotificationDto> feed(String lineCode, String stationCode) {
        Set<String> stationLineCodes = isBlank(stationCode)
                ? Set.of()
                : Set.copyOf(stationLineRepository.findLineCodesByStationCode(stationCode));
        return repository.findByStatusWithTargets(NotificationStatus.SENT).stream()
                .filter(message -> message.hasChannel(NotificationChannel.IN_APP))
                .filter(message -> matchesTargetFilters(message, lineCode, stationCode, stationLineCodes))
                .sorted(FEED_ORDER)
                .map(message -> toDto(message))
                .toList();
    }

    /**
     * Комбинированный таргет-фильтр (семантика — javadoc {@link #feed}):
     * без фильтров и для рассылок на всю сеть — проходит всегда; lineCode —
     * прямой line-таргет; stationCode — прямой station-таргет ИЛИ line-таргет
     * любой линии станции; оба фильтра — объединение.
     */
    private static boolean matchesTargetFilters(NotificationMessage message, String lineCode,
                                                String stationCode, Set<String> stationLineCodes) {
        boolean byLine = !isBlank(lineCode);
        boolean byStation = !isBlank(stationCode);
        if ((!byLine && !byStation) || message.networkWide()) {
            return true;
        }
        if (byLine && hasTarget(message, TargetType.LINE, lineCode)) {
            return true;
        }
        return byStation
                && (hasTarget(message, TargetType.STATION, stationCode)
                        || message.getTargets().stream().anyMatch(
                                t -> t.getTargetType() == TargetType.LINE
                                        && stationLineCodes.contains(t.getTargetCode())));
    }

    private static boolean hasTarget(NotificationMessage message, TargetType type, String code) {
        return message.getTargets().stream().anyMatch(target -> target.matches(type, code));
    }

    public static NotificationDto toDto(NotificationMessage message) {
        List<NotificationTargetDto> targets = message.getTargets().stream()
                .sorted(Comparator.comparing((NotificationTarget t) -> t.getTargetType().code())
                        .thenComparing(NotificationTarget::getTargetCode))
                .map(t -> new NotificationTargetDto(t.getTargetType().code(), t.getTargetCode()))
                .toList();
        return new NotificationDto(
                message.getCode(),
                message.getTemplateCode(),
                message.getAlertCode(),
                message.getType().code(),
                message.getTitleI18n(),
                message.getBodyI18n(),
                message.getChannels(),
                message.getStatus().code(),
                targets,
                instant(message.getScheduledAt()),
                instant(message.getSentAt()),
                instant(message.getUpdatedAt()),
                message.getStatus().allowedTransitions().stream()
                        .map(NotificationStatus::code)
                        .sorted()
                        .toList(),
                message.getStatus().frozen());
    }

    public static NotificationDeliveryDto toDto(NotificationDelivery delivery) {
        return new NotificationDeliveryDto(
                delivery.getId().toString(),
                delivery.getMessage().getCode(),
                delivery.getChannel(),
                delivery.getRecipient(),
                delivery.getStatus().code(),
                delivery.getAttempts(),
                delivery.getLastError(),
                delivery.simulated(),
                instant(delivery.getSentAt()),
                instant(delivery.getDeliveredAt()),
                instant(delivery.getUpdatedAt()));
    }

    public static NotificationTemplateDto toDto(NotificationTemplate template) {
        return new NotificationTemplateDto(
                template.getCode(),
                template.getName(),
                template.getType().code(),
                template.getTitleI18n(),
                template.getBodyI18n(),
                template.getChannels(),
                template.isActive(),
                instant(template.getUpdatedAt()));
    }

    private static java.time.Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
