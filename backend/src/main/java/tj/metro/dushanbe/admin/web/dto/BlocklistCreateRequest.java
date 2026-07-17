package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Добавление в чёрный список (TKT-06).
 *
 * @param subjectType ticket | token | rider
 * @param subjectValue значение субъекта. Для {@code token} сюда передаётся сам
 *                     токен (его прислал турникет) — сервис заменит его на
 *                     SHA-256 перед записью: открытый токен не должен появиться
 *                     ни в БД, ни в аудите.
 * @param reason       основание блокировки; обязательно — блокировка без причины
 *                     неоспорима и неснимаема по существу
 */
public record BlocklistCreateRequest(

        @NotBlank
        @Size(max = 16)
        String subjectType,

        @NotBlank
        @Size(max = 128)
        String subjectValue,

        @NotBlank
        @Size(max = 500)
        String reason) {
}
