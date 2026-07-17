package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Редактирование карточки инцидента. Статус здесь не меняется — только через
 * переход (см. {@link IncidentTransitionRequest}), иначе отметки времени
 * разъедутся с состоянием.
 */
public record IncidentUpdateRequest(
        @NotBlank @Pattern(regexp = "safety|technical|passenger|infrastructure|other") String category,
        @NotBlank @Pattern(regexp = "low|medium|high|critical") String severity,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 8000) String description,
        @Size(max = 64) String lineCode,
        @Size(max = 64) String stationCode,
        @Size(max = 64) String assignedTo,
        @NotNull OffsetDateTime occurredAt) {
}
