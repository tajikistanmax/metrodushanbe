package tj.metro.dushanbe.integration.web.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Тело исходящего вебхука (INT-02, U-INT-03) — внешний контракт подписчиков.
 *
 * <p>Дублирование {@code eventId}/{@code eventType}/{@code traceId} в теле и в
 * заголовках намеренно: заголовки удобны прокси и роутингу подписчика, но тело —
 * это то, что попадает под подпись. Подписчик, доверяющий только заголовкам,
 * доверял бы неподписанным данным.
 *
 * @param eventId       ключ идемпотентности: повтор с тем же id — тот же факт
 * @param eventType     код {@code WebhookEventType}
 * @param aggregateType тип изменившегося объекта (alert|incident|station|schedule|train)
 * @param aggregateCode стабильный код объекта
 * @param occurredAt    когда факт произошёл (не когда доставлен)
 * @param traceId       сквозная трасса для разбора инцидентов (INT-05)
 * @param payload       данные события
 */
public record WebhookEventPayload(
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateCode,
        Instant occurredAt,
        String traceId,
        Map<String, Object> payload) {
}
