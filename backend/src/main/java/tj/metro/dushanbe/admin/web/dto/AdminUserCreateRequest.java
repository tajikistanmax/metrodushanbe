package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Создание оператора консоли. */
public record AdminUserCreateRequest(
        @NotBlank @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]{2,63}$") String username,
        @NotBlank @Size(max = 128) String displayName,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Pattern(regexp = "superadmin|editor|operator|viewer") String role,
        @NotNull Boolean active) {
}
