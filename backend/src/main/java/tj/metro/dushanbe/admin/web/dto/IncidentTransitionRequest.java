package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Переход инцидента в новое состояние.
 *
 * <p>{@code resolution} обязателен при переходе в {@code resolved} — проверяется
 * в сервисе, а не аннотацией: требование зависит от целевого статуса.
 */
public record IncidentTransitionRequest(
        @NotBlank @Pattern(regexp = "open|acknowledged|in_progress|resolved|closed") String status,
        @Size(max = 8000) String resolution) {
}
