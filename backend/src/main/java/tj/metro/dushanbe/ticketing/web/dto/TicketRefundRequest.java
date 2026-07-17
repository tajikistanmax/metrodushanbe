package tj.metro.dushanbe.ticketing.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Заявление на возврат (TKT-03).
 *
 * <p>Основание обязательно и не имеет значения по умолчанию: возврат без причины
 * неразбираем (refund.reason NOT NULL), а «reason = —» в истории финансовых
 * операций хуже, чем его отсутствие.
 */
public record TicketRefundRequest(

        @NotBlank
        @Size(max = 500)
        String reason) {
}
