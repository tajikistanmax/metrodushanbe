package tj.metro.dushanbe.ticketing.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Платёж REST API (TKT-05).
 *
 * <p>Карточных данных нет — их нет и в модели, см. {@code Payment}. Всё, что
 * известно о карте, осталось у провайдера.
 *
 * @param ticketCode    null означает «платёж отклонён, билет не выпускался»
 * @param failureReason причина отказа; заполнена ровно при {@code status=failed}
 * @param demo          платежа в реальности не было
 */
public record PaymentDto(
        String code,
        String ticketCode,
        String kind,
        BigDecimal amount,
        String currency,
        String status,
        String provider,
        String providerRef,
        String failureReason,
        boolean demo,
        Instant createdAt,
        Instant updatedAt) {
}
