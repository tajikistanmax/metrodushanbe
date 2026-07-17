package tj.metro.dushanbe.fare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Публичный тарифный продукт MVP (FAR-01/02). */
@Entity
@Table(name = "fare_product")
public class FareProduct {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name_i18n", nullable = false)
    private Map<String, String> nameI18n;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "description_i18n", nullable = false)
    private Map<String, String> descriptionI18n;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "rider_category", nullable = false, length = 24)
    private String riderCategory;

    @Column(name = "validity_minutes")
    private Integer validityMinutes;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected FareProduct() {
    }

    public FareProduct(UUID id, String code, Map<String, String> nameI18n,
                       Map<String, String> descriptionI18n, BigDecimal amount,
                       String currency, String riderCategory, Integer validityMinutes,
                       boolean active) {
        this.id = id;
        this.code = code;
        this.nameI18n = nameI18n;
        this.descriptionI18n = descriptionI18n;
        this.amount = amount;
        this.currency = currency;
        this.riderCategory = riderCategory;
        this.validityMinutes = validityMinutes;
        this.active = active;
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

    /** Обновляет редактируемые атрибуты; стабильный код не меняется. */
    public void update(Map<String, String> name, Map<String, String> description,
                       BigDecimal newAmount, String newCurrency, String category,
                       Integer validity, boolean enabled) {
        nameI18n = name;
        descriptionI18n = description;
        amount = newAmount;
        currency = newCurrency;
        riderCategory = category;
        validityMinutes = validity;
        active = enabled;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public Map<String, String> getNameI18n() { return nameI18n; }
    public Map<String, String> getDescriptionI18n() { return descriptionI18n; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getRiderCategory() { return riderCategory; }
    public Integer getValidityMinutes() { return validityMinutes; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
