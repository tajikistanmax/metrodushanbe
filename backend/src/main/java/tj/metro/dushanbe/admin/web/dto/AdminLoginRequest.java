package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Проверка учётных данных оператора при входе в консоль. */
public record AdminLoginRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 72) String password) {
}
