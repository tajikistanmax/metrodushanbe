package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ручной возврат из консоли (TKT-03).
 *
 * <p>Отдельный тип от публичного {@code TicketRefundRequest}, хотя поле пока
 * одно: у операторского возврата другие права (можно вернуть погашенный билет) и
 * другой актор аудита. Общий DTO склеил бы два разных полномочия в одну форму —
 * и первое же расширение публичного запроса протекло бы в админский.
 */
public record TicketRefundCommand(

        @NotBlank
        @Size(max = 500)
        String reason) {
}
