package tj.metro.dushanbe.integration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Событие сети, записанное для последующей доставки подписчикам (INT-03).
 *
 * <p>Запись неизменяема после создания: методов мутации нет намеренно. Событие —
 * это ФАКТ («уведомление опубликовано в 10:15»), а факт не редактируется. Всё,
 * что меняется, — состояние его доставок ({@link WebhookDelivery}). Если бы
 * payload можно было поправить после публикации, подписчики, получившие событие
 * до и после правки, увидели бы разные данные под одним {@code eventId} — и
 * идемпотентность на их стороне превратилась бы из защиты в источник расхождения.
 *
 * <p>{@code updatedAt} по той же причине отсутствует: обновлять здесь нечего.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /**
     * Ключ идемпотентности для подписчика: уходит в {@code X-Metro-Event-Id} и
     * не меняется между повторами доставки.
     */
    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Convert(converter = WebhookEventType.Persistence.class)
    @Column(name = "event_type", nullable = false, length = 48)
    private WebhookEventType eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private Map<String, Object> payload;

    @Column(name = "aggregate_type", nullable = false, length = 32)
    private String aggregateType;

    @Column(name = "aggregate_code", nullable = false, length = 64)
    private String aggregateCode;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    /** requestId из MDC (см. RequestIdFilter) — сквозная трассировка INT-05. */
    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, UUID eventId, WebhookEventType eventType, Map<String, Object> payload,
                       String aggregateType, String aggregateCode, OffsetDateTime occurredAt,
                       String traceId) {
        this.id = id;
        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = payload;
        this.aggregateType = aggregateType;
        this.aggregateCode = aggregateCode;
        this.occurredAt = occurredAt;
        this.traceId = traceId;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public WebhookEventType getEventType() { return eventType; }
    public Map<String, Object> getPayload() { return payload; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateCode() { return aggregateCode; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public String getTraceId() { return traceId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
