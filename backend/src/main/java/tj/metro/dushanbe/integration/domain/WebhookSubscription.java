package tj.metro.dushanbe.integration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Внешний подписчик событий сети — городской портал и прочие потребители
 * (INT-02, U-INT-03, ADM-06).
 *
 * <p><b>Секрета в открытом виде здесь нет.</b> Поле {@code secretHash} хранит
 * SHA-256 от секрета — тем же приёмом, что {@code CitizenRequest.trackingTokenHash}.
 * Плейнтекст существует ровно в момент создания/ротации, отдаётся вызывающему
 * один раз и после этого невосстановим. Сеттера секрета нет: заменить его можно
 * только через {@link #rotateSecret}, чтобы «тихая» подмена ключа без отметки
 * времени была невозможна.
 */
@Entity
@Table(name = "webhook_subscription")
public class WebhookSubscription {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;

    @Column(name = "secret_hash", nullable = false, length = 64)
    private String secretHash;

    /** Коды {@link WebhookEventType}, на которые подписан потребитель. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_types", nullable = false)
    private List<String> eventTypes;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "rate_limit_per_minute", nullable = false)
    private int rateLimitPerMinute;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    protected WebhookSubscription() {
    }

    public WebhookSubscription(UUID id, String code, String name, String targetUrl, String secretHash,
                               List<String> eventTypes, boolean active, int rateLimitPerMinute,
                               String createdBy) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.targetUrl = targetUrl;
        this.secretHash = secretHash;
        this.eventTypes = eventTypes;
        this.active = active;
        this.rateLimitPerMinute = rateLimitPerMinute;
        this.createdBy = createdBy;
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

    /** Редактирование карточки подписчика; секрет сюда не входит осознанно. */
    public void update(String newName, String newTargetUrl, List<String> newEventTypes,
                       boolean newActive, int newRateLimitPerMinute) {
        name = newName;
        targetUrl = newTargetUrl;
        eventTypes = newEventTypes;
        active = newActive;
        rateLimitPerMinute = newRateLimitPerMinute;
    }

    /** Замена ключа подписи. На вход — уже хеш: плейнтекст в entity не попадает. */
    public void rotateSecret(String newSecretHash) {
        secretHash = newSecretHash;
    }

    /** Подписан ли потребитель на этот тип события — фильтр фан-аута в OutboxService. */
    public boolean subscribedTo(WebhookEventType type) {
        return eventTypes != null && eventTypes.contains(type.code());
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getTargetUrl() { return targetUrl; }
    public String getSecretHash() { return secretHash; }
    public List<String> getEventTypes() { return eventTypes; }
    public boolean isActive() { return active; }
    public int getRateLimitPerMinute() { return rateLimitPerMinute; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
}
