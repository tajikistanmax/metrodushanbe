package tj.metro.dushanbe.ticketing.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Билет публичного и административного REST API (TKT-04).
 *
 * <p><b>Токена здесь нет и быть не может.</b> Токен QR отдаётся ровно один раз —
 * в {@link TicketPurchaseResponse}. Любой последующий просмотр билета
 * (GET /v1/tickets/&#123;code&#125;, консоль оператора) видит только этот DTO.
 * Добавить сюда поле токена невозможно: в системе хранится лишь его хеш.
 *
 * <p>{@code demo == true} — за билетом не стоит реального платежа. Поле
 * обязательное: см. javadoc {@code PaymentGateway}.
 *
 * @param priceAmount цена, зафиксированная при покупке, а не текущая цена тарифа
 */
public record TicketDto(
        String code,
        String fareProductCode,
        String kind,
        String riderCategory,
        String status,
        Instant validFrom,
        Instant validUntil,
        BigDecimal priceAmount,
        String priceCurrency,
        BigDecimal balanceAmount,
        Instant usedAt,
        boolean demo,
        Instant updatedAt,
        List<String> allowedTransitions) {
}
