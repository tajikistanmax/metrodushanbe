package tj.metro.dushanbe.network.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * Станция метрополитена (таблица {@code metro_station}, ТЗ §6.3.1).
 * Названия — JSONB {@code name_i18n}; геометрия входа — Point в EPSG:4326;
 * {@code accessibility} — JSONB-массив признаков доступности
 * (elevator|escalator|ramp|tactile|audio_assist).
 */
@Entity
@Table(name = "metro_station")
public class MetroStation {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Стабильный внешний идентификатор, например "ST-L1-01". */
    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    /** planned|under_construction|testing|active|temporarily_closed|decommissioned. */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name_i18n", nullable = false)
    private Map<String, String> nameI18n;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "description_i18n")
    private Map<String, String> descriptionI18n;

    @Column(name = "point_geom", nullable = false)
    private Point pointGeom;

    @Column(name = "area_geom")
    private Polygon areaGeom;

    @Column(name = "is_transfer", nullable = false)
    private boolean isTransfer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "accessibility", nullable = false)
    private List<String> accessibility;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attrs", nullable = false)
    private Map<String, Object> attrs;

    @Column(name = "effective_from", nullable = false)
    private OffsetDateTime effectiveFrom;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Конструктор для JPA. */
    protected MetroStation() {
    }

    public MetroStation(UUID id, String code, String status, Map<String, String> nameI18n,
                        Point pointGeom, boolean isTransfer, List<String> accessibility) {
        this.id = id;
        this.code = code;
        this.status = status;
        this.nameI18n = nameI18n;
        this.pointGeom = pointGeom;
        this.isTransfer = isTransfer;
        this.accessibility = accessibility;
        this.attrs = Map.of();
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (effectiveFrom == null) {
            effectiveFrom = now;
        }
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

    public String getStatus() {
        return status;
    }

    public Map<String, String> getNameI18n() {
        return nameI18n;
    }

    public Map<String, String> getDescriptionI18n() {
        return descriptionI18n;
    }

    public Point getPointGeom() {
        return pointGeom;
    }

    public Polygon getAreaGeom() {
        return areaGeom;
    }

    public boolean isTransfer() {
        return isTransfer;
    }

    public List<String> getAccessibility() {
        return accessibility;
    }

    public Map<String, Object> getAttrs() {
        return attrs;
    }

    public OffsetDateTime getEffectiveFrom() {
        return effectiveFrom;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
