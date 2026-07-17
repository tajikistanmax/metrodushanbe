package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Изменение оператора. Логин стабилен и не меняется.
 *
 * <p>{@code password} необязателен: {@code null} — пароль не трогаем, непустая
 * строка — задаём новый.
 */
public record AdminUserUpdateRequest(
        @NotBlank @Size(max = 128) String displayName,
        @Size(min = 12, max = 72) String password,
        @NotBlank @Pattern(regexp = "superadmin|editor|operator|viewer") String role,
        @NotNull Boolean active) {
}
