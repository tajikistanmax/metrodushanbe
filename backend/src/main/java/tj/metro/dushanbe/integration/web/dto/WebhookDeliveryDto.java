package tj.metro.dushanbe.integration.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Строка операторской очереди доставок (U-OPS-04, INT-05).
 *
 * @param status          pending|processing|sent|failed|dead; dead — это DLQ
 * @param lastError       причина последнего провала; для failed/dead всегда заполнена
 * @param retryable       можно ли вернуть в очередь вручную — консоль по нему
 *                        рисует кнопку «Повторить», чтобы не показывать действие,
 *                        которое backend отклонит
 * @param eventType       тип события из immutable snapshot доставки
 */
public record WebhookDeliveryDto(
        String code,
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateCode,
        String subscriptionCode,
        String status,
        int attempts,
        Integer responseStatus,
        String lastError,
        String traceId,
        Instant nextAttemptAt,
        Instant createdAt,
        Instant updatedAt,
        boolean retryable,
        List<String> allowedTransitions) {
}
