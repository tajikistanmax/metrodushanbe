package tj.metro.dushanbe.ticketing.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Возврат REST API (TKT-03).
 *
 * @param reason        основание возврата от пассажира/оператора
 * @param failureReason техническая причина отказа провайдера (только при status=failed)
 * @param demo          реального перевода средств не было
 */
public record RefundDto(
        String code,
        String paymentCode,
        BigDecimal amount,
        String currency,
        String status,
        String reason,
        String failureReason,
        String createdBy,
        boolean demo,
        Instant createdAt) {
}
