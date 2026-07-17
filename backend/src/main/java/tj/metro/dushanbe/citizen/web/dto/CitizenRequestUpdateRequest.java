package tj.metro.dushanbe.citizen.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Операторское обновление workflow обращения. */
public record CitizenRequestUpdateRequest(
        @NotBlank
        @Pattern(regexp = "new|in_progress|awaiting_info|resolved|closed|reopened")
        String status,
        @Size(max = 10_000) String response,
        @Size(max = 128) String assignedTo) {
}
