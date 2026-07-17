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
 * Платёж за билет или пополнение (TKT-05).
 *
 * <p><b>ДЕМО. Карточных данных здесь нет.</b> Ни PAN, ни CVV, ни срока действия,
 * ни имени держателя — полей под них не существует, и добавлять их до
 * сертификации PCI DSS нельзя (см. шапку V023 и javadoc {@code PaymentGateway}).
 * Всё, что известно о карте, остаётся у провайдера; у нас — только его
 * {@code providerRef}.
 *
 * <p>{@code ticketCode} равен null ровно в одном случае: платёж отклонён и билет
 * не выпускался. Отклонённая попытка обязана сохраниться — она нужна антифроду
 * (TKT-06) и разбору жалоб, — но права выпустить под неё билет не даёт.
 *
 * <p>Переходы — только через {@link #authorize}, {@link #capture}, {@link #fail},
 * {@link #markRefunded}: сеттера статуса нет, иначе failed легко оставить без
 * причины (chk_payment_error) или captured — без ссылки провайдера
 * (chk_payment_settled).
 */
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "ticket_code", length = 64)
    private String ticketCode;

    @Convert(converter = PaymentKind.Persistence.class)
    @Column(name = "kind", nullable = false, length = 16)
    private PaymentKind kind;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Convert(converter = PaymentStatus.Persistence.class)
    @Column(name = "status", nullable = false, length = 16)
    private PaymentStatus status;

    @Column(name = "provider", nullable = false, length = 24)
    private String provider;

    @Column(name = "provider_ref", length = 128)
    private String providerRef;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Payment() {
    }

    public Payment(UUID id, String code, PaymentKind kind, BigDecimal amount,
                   String currency, String provider, boolean demo) {
        this.id = id;
        this.code = code;
        this.kind = kind;
        this.amount = amount;
        this.currency = currency;
        this.status = PaymentStatus.PENDING;
        this.provider = provider;
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

    /** Провайдер подтвердил средства. */
    public void authorize(String reference) {
        requireTransition(PaymentStatus.AUTHORIZED);
        requireText(reference, "Provider reference is required for authorization");
        status = PaymentStatus.AUTHORIZED;
        providerRef = reference;
    }

    /**
     * Средства списаны; платёж привязывается к выпущенному билету.
     *
     * <p>Привязка происходит именно здесь, а не при создании: до capture билета
     * ещё нет, и chk_payment_settled требует ticket_code ровно с этого момента.
     */
    public void capture(String issuedTicketCode) {
        requireTransition(PaymentStatus.CAPTURED);
        requireText(issuedTicketCode, "Issued ticket code is required for capture");
        status = PaymentStatus.CAPTURED;
        ticketCode = issuedTicketCode;
    }

    /** Отказ провайдера. Причина обязательна — см. chk_payment_error. */
    public void fail(String reason) {
        requireTransition(PaymentStatus.FAILED);
        requireText(reason, "Payment failure reason is required");
        status = PaymentStatus.FAILED;
        failureReason = reason;
    }

    /** Возврат проведён (TKT-03). */
    public void markRefunded() {
        requireTransition(PaymentStatus.REFUNDED);
        status = PaymentStatus.REFUNDED;
    }

    private void requireTransition(PaymentStatus target) {
        if (!status.canMoveTo(target)) {
            throw new IllegalStateException("Payment cannot move from " + status + " to " + target);
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getTicketCode() { return ticketCode; }
    public PaymentKind getKind() { return kind; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public String getProvider() { return provider; }
    public String getProviderRef() { return providerRef; }
    public String getFailureReason() { return failureReason; }
    public boolean isDemo() { return demo; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
