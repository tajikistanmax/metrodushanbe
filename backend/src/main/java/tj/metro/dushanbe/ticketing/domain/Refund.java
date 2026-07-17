package tj.metro.dushanbe.ticketing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Возврат платежа (TKT-03).
 *
 * <p><b>ДЕМО:</b> реального перевода средств не происходит — эквайринга нет
 * (см. шапку V023). Запись фиксирует намерение и его исход, не деньги.
 *
 * <p>Отдельная сущность, а не поле в {@link Payment}, потому что возврат — это
 * самостоятельное СОБЫТИЕ со своим основанием, инициатором, временем и исходом.
 * В payment.status можно записать только «возвращён», но не «кем, когда и
 * почему», а именно это спрашивают при разборе финансовой претензии.
 */
@Entity
@Table(name = "refund")
public class Refund {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "payment_code", nullable = false, length = 64)
    private String paymentCode;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Convert(converter = RefundStatus.Persistence.class)
    @Column(name = "status", nullable = false, length = 16)
    private RefundStatus status;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "provider_ref", length = 128)
    private String providerRef;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Refund() {
    }

    public Refund(UUID id, String code, String paymentCode, BigDecimal amount,
                  String currency, String reason, String createdBy, boolean demo) {
        this.id = id;
        this.code = code;
        this.paymentCode = paymentCode;
        this.amount = amount;
        this.currency = currency;
        this.status = RefundStatus.PENDING;
        this.reason = reason;
        this.createdBy = createdBy;
        this.demo = demo;
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

    /** Провайдер подтвердил возврат. */
    public void complete(String reference) {
        requirePending();
        requireText(reference, "Provider reference is required for a completed refund");
        status = RefundStatus.COMPLETED;
        providerRef = reference;
    }

    /**
     * Провайдер отклонил возврат. Причина обязательна — см. chk_refund_error.
     *
     * <p>Параметр назван {@code failure}, а не {@code reason}: {@code reason} —
     * это основание возврата от пассажира, {@code failureReason} — техническая
     * причина отказа провайдера. Смешивать их нельзя.
     */
    public void fail(String failure) {
        requirePending();
        requireText(failure, "Refund failure reason is required");
        status = RefundStatus.FAILED;
        failureReason = failure;
    }

    private void requirePending() {
        if (status != RefundStatus.PENDING) {
            throw new IllegalStateException("Refund is already terminal: " + status);
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getPaymentCode() { return paymentCode; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public RefundStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public String getProviderRef() { return providerRef; }
    public String getFailureReason() { return failureReason; }
    public String getCreatedBy() { return createdBy; }
    public boolean isDemo() { return demo; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
