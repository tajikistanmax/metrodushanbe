package tj.metro.dushanbe.alert.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Сервисное уведомление в ответах API.
 * {@code title}/{@code body} — полные i18n-объекты {"tg","ru","en"};
 * {@code startsAt}/{@code endsAt} — ISO-8601 UTC, {@code endsAt} null = бессрочно;
 * {@code targets} — затронутые линии/станции, пустой массив = вся сеть.
 */
public record AlertDto(String code,
                       String severity,
                       Map<String, String> title,
                       Map<String, String> body,
                       Instant startsAt,
                       Instant endsAt,
                       List<AlertTargetDto> targets) {
}
