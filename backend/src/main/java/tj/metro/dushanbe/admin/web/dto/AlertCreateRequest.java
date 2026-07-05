package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Запрос создания уведомления (ADM-02, ТЗ §6.2.6). Создаётся в статусе {@code draft};
 * публикация — отдельным вызовом. {@code severity} — info|warning|critical;
 * {@code title}/{@code body} — i18n-объекты; окно действия — [{@code startsAt},
 * {@code endsAt}); {@code endsAt} null = бессрочно; {@code targets} — линии/станции
 * (пусто = вся сеть).
 */
public record AlertCreateRequest(
        @NotBlank String code,
        @NotBlank String severity,
        @NotNull Map<String, String> title,
        @NotNull Map<String, String> body,
        @NotNull Instant startsAt,
        Instant endsAt,
        List<AlertTargetRequest> targets) {
}
