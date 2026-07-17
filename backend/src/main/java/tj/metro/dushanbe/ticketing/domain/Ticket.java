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
 * Выпущенный билет (TKT-04).
 *
 * <p><b>ДЕМО.</b> {@code demo == true} означает, что за билетом не стоит реального
 * платежа: эквайринга в проекте нет (см. шапку V023). Признак обязан доезжать до
 * API-ответа — молча показать пассажиру «билет куплен» там, где платежа не было,
 * недопустимо.
 *
 * <p><b>Цена зафиксирована.</b> {@code priceAmount}/{@code priceCurrency}/
 * {@code riderCategory} — копия тарифа на момент покупки, а не ссылка на него.
 * {@code fareProductCode} хранится только как след происхождения: тариф могут
 * подорожать или снять с продажи, билет обязан это пережить неизменным.
 *
 * <p>Токен QR в объекте не живёт — только его SHA-256. Сам токен существует
 * ровно один раз: в ответе на покупку.
 *
 * <p>Переходы состояния выполняются только через доменные методы
 * ({@link #markValidated}, {@link #expire}, {@link #refund}, {@link #block}):
 * они же проставляют отметки времени. Прямого сеттера статуса нет намеренно —
 * иначе билет легко оставить в состоянии {@code used} без {@code usedAt}, и
 * chk_ticket_used_at отвергнет запись уже на уровне БД.
 */
@Entity
@Table(name = "ticket")
public class Ticket {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "fare_product_code", nullable = false, length = 64)
    private String fareProductCode;

    @Convert(converter = TicketKind.Persistence.class)
    @Column(name = "kind", nullable = false, length = 16)
    private TicketKind kind;

    @Column(name = "rider_category", nullable = false, length = 24)
    private String riderCategory;

    @Column(name = "rider_ref", length = 64)
    private String riderRef;

    @Convert(converter = TicketStatus.Persistence.class)
    @Column(name = "status", nullable = false, length = 16)
    private TicketStatus status;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "valid_from", nullable = false)
    private OffsetDateTime validFrom;

    @Column(name = "valid_until", nullable = false)
    private OffsetDateTime validUntil;

    @Column(name = "price_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAmount;

    @Column(name = "price_currency", nullable = false, length = 3)
    private String priceCurrency;

    @Column(name = "balance_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal balanceAmount;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "validation_count", nullable = false)
    private int validationCount;

    @Column(name = "last_validated_at")
    private OffsetDateTime lastValidatedAt;

    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Ticket() {
    }

    public Ticket(UUID id, String code, String fareProductCode, TicketKind kind,
                  String riderCategory, String riderRef, String tokenHash,
                  OffsetDateTime validFrom, OffsetDateTime validUntil,
                  BigDecimal priceAmount, String priceCurrency, boolean demo) {
        this.id = id;
        this.code = code;
        this.fareProductCode = fareProductCode;
        this.kind = kind;
        this.riderCategory = riderCategory;
        this.riderRef = riderRef;
        this.status = TicketStatus.ISSUED;
        this.tokenHash = tokenHash;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.priceAmount = priceAmount;
        this.priceCurrency = priceCurrency;
        this.balanceAmount = BigDecimal.ZERO;
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

    /**
     * Отмечает успешный проход через турникет.
     *
     * <p>Разовый билет гасится (used), проездной остаётся действующим (active).
     * Счётчик и время последней валидации нужны антифроду (TKT-06) — по ним
     * работает правило anti-passback. Допустимость валидации (статус, срок,
     * чёрный список) проверяет сервис: сюда попадают только разрешённые проходы.
     */
    public void markValidated(OffsetDateTime at) {
        if (!status.validatable()) {
            throw new IllegalStateException("Ticket cannot be validated from status " + status);
        }
        if (at == null) {
            throw new IllegalArgumentException("Validation time is required");
        }
        validationCount++;
        lastValidatedAt = at;
        if (kind.consumedOnValidation()) {
            status = TicketStatus.USED;
            usedAt = at;
        } else {
            status = TicketStatus.ACTIVE;
        }
    }

    /**
     * Пополняет проездной: продлевает окно действия и учитывает внесённую сумму.
     *
     * <p>{@code balanceAmount} — учётный итог внесённого, а не расходуемый
     * кошелёк: поездки его не уменьшают (см. V023). Действие проездного даёт
     * именно {@code validUntil}.
     *
     * <p>Окно продлевается от {@code max(now, validUntil)}, а не от «сейчас»:
     * пассажир, пополнивший проездной за день до конца срока, не должен терять
     * этот день. Допустимость пополнения проверяет сервис.
     */
    public void applyTopUp(BigDecimal amount, OffsetDateTime newValidUntil) {
        if (kind != TicketKind.PASS || !status.validatable()) {
            throw new IllegalStateException("Only an issued or active pass can be topped up");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Top-up amount must be positive");
        }
        if (newValidUntil == null || !newValidUntil.isAfter(validUntil)) {
            throw new IllegalArgumentException("Top-up must extend the validity window");
        }
        balanceAmount = balanceAmount.add(amount);
        validUntil = newValidUntil;
    }

    /** Срок действия вышел. */
    public void expire() {
        requireTransition(TicketStatus.EXPIRED);
        status = TicketStatus.EXPIRED;
    }

    /** Деньги возвращены (TKT-03); билет больше не действует. */
    public void refund() {
        requireTransition(TicketStatus.REFUNDED);
        status = TicketStatus.REFUNDED;
    }

    /** Блокировка антифродом или оператором (TKT-06). Необратима — см. TicketStatus. */
    public void block() {
        requireTransition(TicketStatus.BLOCKED);
        status = TicketStatus.BLOCKED;
    }

    private void requireTransition(TicketStatus target) {
        if (!status.canMoveTo(target)) {
            throw new IllegalStateException("Ticket cannot move from " + status + " to " + target);
        }
    }

    /** Истёк ли срок действия на момент {@code at} (независимо от статуса). */
    public boolean expiredAt(OffsetDateTime at) {
        return !at.isBefore(validUntil);
    }

    /** Наступило ли начало действия на момент {@code at}. */
    public boolean startedAt(OffsetDateTime at) {
        return !at.isBefore(validFrom);
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getFareProductCode() { return fareProductCode; }
    public TicketKind getKind() { return kind; }
    public String getRiderCategory() { return riderCategory; }
    public String getRiderRef() { return riderRef; }
    public TicketStatus getStatus() { return status; }
    public String getTokenHash() { return tokenHash; }
    public OffsetDateTime getValidFrom() { return validFrom; }
    public OffsetDateTime getValidUntil() { return validUntil; }
    public BigDecimal getPriceAmount() { return priceAmount; }
    public String getPriceCurrency() { return priceCurrency; }
    public BigDecimal getBalanceAmount() { return balanceAmount; }
    public OffsetDateTime getUsedAt() { return usedAt; }
    public int getValidationCount() { return validationCount; }
    public OffsetDateTime getLastValidatedAt() { return lastValidatedAt; }
    public boolean isDemo() { return demo; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
