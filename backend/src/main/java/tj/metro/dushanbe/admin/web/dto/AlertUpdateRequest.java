package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Запрос обновления уведомления (ADM-02). Стабильный код неизменен — из пути.
 * Статус меняется отдельным вызовом публикации; здесь правится содержание.
 */
public record AlertUpdateRequest(
        @NotBlank String severity,
        @NotNull Map<String, String> title,
        @NotNull Map<String, String> body,
        @NotNull Instant startsAt,
        Instant endsAt,
        List<AlertTargetRequest> targets) {
}
