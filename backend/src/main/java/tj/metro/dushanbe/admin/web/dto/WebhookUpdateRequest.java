package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Правка карточки подписчика (ADM-06). Секрет меняется только ротацией —
 * отдельной операцией с отдельной записью в аудите.
 */
public record WebhookUpdateRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 2048) @Pattern(regexp = "https://.+") String targetUrl,
        @NotEmpty List<String> eventTypes,
        boolean active,
        @Positive Integer rateLimitPerMinute) {
}
