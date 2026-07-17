package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Заведение подписчика вебхуков (ADM-06).
 *
 * <p>Секрета во входных данных нет намеренно: его генерирует сервер и показывает
 * один раз в ответе. Принимать секрет от оператора значило бы получать в
 * консоль (и в её логи, и в браузерную историю) ключи, которые оператор сам же
 * придумает — то есть слабые и переиспользованные.
 */
public record WebhookCreateRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 2048) @Pattern(regexp = "https://.+") String targetUrl,
        @NotEmpty List<String> eventTypes,
        boolean active,
        @Positive Integer rateLimitPerMinute) {
}
