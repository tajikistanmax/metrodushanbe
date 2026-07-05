package tj.metro.dushanbe.alert.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Сервисное уведомление (таблица {@code service_alert}, ТЗ §6.2.6).
 * Тексты — JSONB {@code title_i18n}/{@code body_i18n} вида {"tg","ru","en"};
 * таргетинг — коллекция {@link AlertTarget} (пустая коллекция = вся сеть);
 * окно действия — [{@code startsAt}, {@code endsAt}), {@code endsAt} NULL = бессрочно.
 */
@Entity
@Table(name = "service_alert")
public class ServiceAlert {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Стабильный внешний идентификатор, например "ALERT-2026-001". */
    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    /** info|warning|critical. */
    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    /** draft|review|approved|published|superseded|expired (жизненный цикл ТЗ §6.2.6). */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "title_i18n", nullable = false)
    private Map<String, String> titleI18n;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "body_i18n", nullable = false)
    private Map<String, String> bodyI18n;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at")
    private OffsetDateTime endsAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Затронутые линии/станции; пустая коллекция = alert на всю сеть. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "service_alert_target", joinColumns = @JoinColumn(name = "alert_id"))
    private List<AlertTarget> targets = new ArrayList<>();

    /** Конструктор для JPA. */
    protected ServiceAlert() {
    }

    public ServiceAlert(UUID id, String code, String severity, String status,
                        Map<String, String> titleI18n, Map<String, String> bodyI18n,
                        OffsetDateTime startsAt, OffsetDateTime endsAt, List<AlertTarget> targets) {
        this.id = id;
        this.code = code;
        this.severity = severity;
        this.status = status;
        this.titleI18n = titleI18n;
        this.bodyI18n = bodyI18n;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.targets = new ArrayList<>(targets);
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

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getSeverity() {
        return severity;
    }

    public String getStatus() {
        return status;
    }

    public Map<String, String> getTitleI18n() {
        return titleI18n;
    }

    public Map<String, String> getBodyI18n() {
        return bodyI18n;
    }

    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    public OffsetDateTime getEndsAt() {
        return endsAt;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<AlertTarget> getTargets() {
        return targets;
    }
}
