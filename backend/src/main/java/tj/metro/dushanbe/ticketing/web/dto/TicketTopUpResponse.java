package tj.metro.dushanbe.ticketing.web.dto;

/**
 * Результат пополнения (U-CIT-08).
 *
 * <p>Токена здесь нет: пополнение не выпускает новый билет, а продлевает
 * существующий — токен у пассажира уже есть и не меняется.
 *
 * <p>Как и при покупке, отказ платежа — обычный ответ (HTTP 402) с
 * {@code payment.status = failed}, а не исключение.
 */
public record TicketTopUpResponse(
        TicketDto ticket,
        PaymentDto payment,
        boolean demo,
        String notice) {
}
