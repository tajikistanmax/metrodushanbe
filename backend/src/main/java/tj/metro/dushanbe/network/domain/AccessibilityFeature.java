package tj.metro.dushanbe.network.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Объект доступности станции (таблица {@code accessibility_feature}, ТЗ §5.1, NET-03).
 * Конкретный элемент безбарьерной среды станции с текущим статусом работоспособности.
 * {@code type} — elevator|escalator|ramp|tactile|audio_assist|accessible_toilet;
 * описание — JSONB {@code description_i18n} вида {"tg","ru","en"};
 * {@code status} — available|out_of_service|planned.
 */
@Entity
@Table(name = "accessibility_feature")
public class AccessibilityFeature {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Станция, к которой относится объект доступности. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private MetroStation station;

    /** elevator|escalator|ramp|tactile|audio_assist|accessible_toilet. */
    @Column(name = "type", nullable = false, length = 32)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "description_i18n", nullable = false)
    private Map<String, String> descriptionI18n;

    /** available|out_of_service|planned. */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Конструктор для JPA. */
    protected AccessibilityFeature() {
    }

    public AccessibilityFeature(UUID id, MetroStation station, String type,
                                Map<String, String> descriptionI18n, String status) {
        this.id = id;
        this.station = station;
        this.type = type;
        this.descriptionI18n = descriptionI18n;
        this.status = status;
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

    public MetroStation getStation() {
        return station;
    }

    public String getType() {
        return type;
    }

    public Map<String, String> getDescriptionI18n() {
        return descriptionI18n;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
