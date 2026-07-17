package tj.metro.dushanbe.integration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Доставка события конкретному подписчику (INT-05).
 *
 * <p>Строка создаётся в момент публикации события (фан-аут по активным подпискам)
 * и живёт до успеха либо до исчерпания retry. Состояние меняется только через
 * доменные методы {@link #markSent}/{@link #markFailed}/{@link #markDead}/
 * {@link #requeue}: сеттера статуса нет намеренно — иначе доставку легко оставить
 * в {@code failed} без {@code lastError}, что запрещено chk_webhook_delivery_error
 * и делает бесполезной операторскую очередь ошибок (U-OPS-04).
 *
 * <p>Счётчик {@code attempts} наращивают {@link #markFailed} и {@link #markDead},
 * а не {@link #requeue}: ручной повтор оператора — не попытка доставки, попытка
 * будет позже, когда диспетчер реально сходит к подписчику. Иначе оператор,
 * дважды нажавший «повторить», исчерпал бы лимит попыток, ни разу не отправив.
 */
@Entity
@Table(name = "webhook_delivery")
public class WebhookDelivery {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    /** {@code OutboxEvent.eventId} — то значение, которое видит подписчик. */
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "subscription_code", nullable = false, length = 64)
    private String subscriptionCode;

    @Column(name = "event_type_snapshot", nullable = false, length = 48)
    private String eventTypeSnapshot;

    @Column(name = "aggregate_type_snapshot", nullable = false, length = 32)
    private String aggregateTypeSnapshot;

    @Column(name = "aggregate_code_snapshot", nullable = false, length = 64)
    private String aggregateCodeSnapshot;

    @Column(name = "trace_id_snapshot", length = 64)
    private String traceIdSnapshot;

    @Column(name = "body_snapshot", nullable = false)
    private String bodySnapshot;

    @Convert(converter = WebhookDeliveryStatus.Persistence.class)
    @Column(name = "status", nullable = false, length = 16)
    private WebhookDeliveryStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;

    @Column(name = "claim_token")
    private UUID claimToken;

    @Column(name = "claim_until")
    private OffsetDateTime claimUntil;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected WebhookDelivery() {
    }

    public WebhookDelivery(UUID id, String code, UUID eventId, String subscriptionCode,
                           String eventTypeSnapshot, String aggregateTypeSnapshot,
                           String aggregateCodeSnapshot, String traceIdSnapshot,
                           String bodySnapshot, OffsetDateTime nextAttemptAt) {
        this.id = id;
        this.code = code;
        this.eventId = eventId;
        this.subscriptionCode = subscriptionCode;
        this.eventTypeSnapshot = requireText(eventTypeSnapshot, "eventTypeSnapshot");
        this.aggregateTypeSnapshot = requireText(aggregateTypeSnapshot, "aggregateTypeSnapshot");
        this.aggregateCodeSnapshot = requireText(aggregateCodeSnapshot, "aggregateCodeSnapshot");
        this.traceIdSnapshot = traceIdSnapshot;
        this.bodySnapshot = requireText(bodySnapshot, "bodySnapshot");
        this.status = WebhookDeliveryStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = nextAttemptAt;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /** Подписчик ответил 2xx. Повторять больше нечего — снимаем расписание. */
    public void markSent(Integer httpStatus) {
        requireProcessing();
        if (httpStatus == null || httpStatus < 200 || httpStatus > 299) {
            throw new IllegalArgumentException("Для sent требуется HTTP-статус 2xx");
        }
        attempts++;
        status = WebhookDeliveryStatus.SENT;
        responseStatus = httpStatus;
        nextAttemptAt = null;
        clearClaim();
        // lastError сохраняем: «дошло с третьей попытки, до этого 502» — это то,
        // ради чего оператор вообще открывает карточку доставки.
    }

    /** Попытка провалилась, retry ещё остался: ждём {@code retryAt}. */
    public void markFailed(String error, Integer httpStatus, OffsetDateTime retryAt) {
        requireProcessing();
        requireError(error);
        if (retryAt == null) {
            throw new IllegalArgumentException("Время следующей попытки обязательно");
        }
        attempts++;
        status = WebhookDeliveryStatus.FAILED;
        lastError = error;
        responseStatus = httpStatus;
        nextAttemptAt = retryAt;
        clearClaim();
    }

    /** Retry исчерпан — доставка уходит в DLQ (INT-05). */
    public void markDead(String error, Integer httpStatus) {
        requireProcessing();
        requireError(error);
        attempts++;
        status = WebhookDeliveryStatus.DEAD;
        lastError = error;
        responseStatus = httpStatus;
        // Расписание снимаем: диспетчер больше не должен её видеть, иначе
        // сломанный подписчик будет вечно занимать батч живыми доставками.
        nextAttemptAt = null;
        clearClaim();
    }

    /**
     * Ручной повтор оператора (U-OPS-04): возвращает failed/dead в очередь.
     * {@code lastError} остаётся — это причина, по которой повтор понадобился.
     */
    public void requeue(OffsetDateTime at) {
        if (!status.retryable()) {
            throw new IllegalStateException("Доставку в статусе " + status.code() + " нельзя повторить");
        }
        if (at == null) {
            throw new IllegalArgumentException("Время повторной постановки обязательно");
        }
        status = WebhookDeliveryStatus.PENDING;
        nextAttemptAt = at;
        clearClaim();
    }

    /**
     * Отложить попытку, не тратя её: подписчик упёрся в свой rate_limit_per_minute
     * (ADM-06). Статус не меняется — это не провал доставки, а наша выдержка.
     */
    public void deferTo(OffsetDateTime at) {
        requireProcessing();
        if (at == null) {
            throw new IllegalArgumentException("Время отсрочки обязательно");
        }
        status = WebhookDeliveryStatus.PENDING;
        nextAttemptAt = at;
        clearClaim();
    }

    /** Доменный эквивалент атомарного SQL-claim; используется также unit-тестами. */
    public void claim(UUID token, OffsetDateTime until) {
        if (!WebhookDeliveryStatus.DUE.contains(status)) {
            throw new IllegalStateException("Доставку в статусе " + status.code() + " нельзя забрать");
        }
        if (token == null || until == null) {
            throw new IllegalArgumentException("Claim token и срок lease обязательны");
        }
        status = WebhookDeliveryStatus.PROCESSING;
        claimToken = token;
        claimUntil = until;
        nextAttemptAt = null;
    }

    private void requireProcessing() {
        if (status != WebhookDeliveryStatus.PROCESSING || claimToken == null || claimUntil == null) {
            throw new IllegalStateException("Доставка не принадлежит worker");
        }
    }

    private static void requireError(String error) {
        if (error == null || error.isBlank()) {
            throw new IllegalArgumentException("Причина ошибки доставки обязательна");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private void clearClaim() {
        claimToken = null;
        claimUntil = null;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public UUID getEventId() { return eventId; }
    public String getSubscriptionCode() { return subscriptionCode; }
    public String getEventTypeSnapshot() { return eventTypeSnapshot; }
    public String getAggregateTypeSnapshot() { return aggregateTypeSnapshot; }
    public String getAggregateCodeSnapshot() { return aggregateCodeSnapshot; }
    public String getTraceIdSnapshot() { return traceIdSnapshot; }
    public String getBodySnapshot() { return bodySnapshot; }
    public WebhookDeliveryStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public UUID getClaimToken() { return claimToken; }
    public OffsetDateTime getClaimUntil() { return claimUntil; }
    public long getVersion() { return version; }
    public String getLastError() { return lastError; }
    public Integer getResponseStatus() { return responseStatus; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
