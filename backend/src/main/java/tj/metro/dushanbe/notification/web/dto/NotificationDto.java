package tj.metro.dushanbe.notification.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Рассылка в ответах API (NTF-01…05).
 * {@code title}/{@code body} — полные i18n-объекты {"tg","ru","en"} (резолвинг
 * {@code ?lang=} в backend не реализован — см. docs/dev-conventions.md);
 * {@code channels} — коды каналов; {@code targets} — адресация, пустой массив = вся сеть;
 * {@code scheduledAt}/{@code sentAt} — ISO-8601 UTC, null = не запланирована / не отправлена.
 *
 * <p>{@code allowedTransitions} и {@code frozen} отдаются вместе с карточкой по той
 * же причине, что и в {@code IncidentDto}: иначе консоль вынуждена держать копию
 * {@code NotificationStatus.TRANSITIONS} и правила заморозки у себя, а две копии
 * одного правила неизбежно разъезжаются — UI начнёт предлагать действие, которое
 * backend отклонит. Правило живёт в enum-е, наружу выдаётся вычисленным.
 */
public record NotificationDto(String code,
                              String templateCode,
                              String alertCode,
                              String type,
                              Map<String, String> title,
                              Map<String, String> body,
                              List<String> channels,
                              String status,
                              List<NotificationTargetDto> targets,
                              Instant scheduledAt,
                              Instant sentAt,
                              Instant updatedAt,
                              List<String> allowedTransitions,
                              boolean frozen) {
}
