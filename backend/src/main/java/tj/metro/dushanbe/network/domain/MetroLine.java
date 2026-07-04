package tj.metro.dushanbe.network.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.MultiLineString;

/**
 * Линия метрополитена (таблица {@code metro_line}, ТЗ §6.3.1).
 * Названия — JSONB {@code name_i18n} вида {"tg","ru","en"};
 * геометрия — MultiLineString в EPSG:4326 (PostGIS, JTS).
 */
@Entity
@Table(name = "metro_line")
public class MetroLine {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Стабильный внешний идентификатор (никогда не меняется), например "L1". */
    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "color_hex", nullable = false, length = 7)
    private String colorHex;

    /** planned|under_construction|testing|active|suspended|decommissioned. */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name_i18n", nullable = false)
    private Map<String, String> nameI18n;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "geom")
    private MultiLineString geom;

    @Column(name = "effective_from", nullable = false)
    private OffsetDateTime effectiveFrom;

    @Column(name = "effective_to")
    private OffsetDateTime effectiveTo;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Конструктор для JPA. */
    protected MetroLine() {
    }

    public MetroLine(UUID id, String code, String colorHex, String status,
                     Map<String, String> nameI18n, int sortOrder, MultiLineString geom) {
        this.id = id;
        this.code = code;
        this.colorHex = colorHex;
        this.status = status;
        this.nameI18n = nameI18n;
        this.sortOrder = sortOrder;
        this.geom = geom;
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

    public String getColorHex() {
        return colorHex;
    }

    public String getStatus() {
        return status;
    }

    public Map<String, String> getNameI18n() {
        return nameI18n;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public MultiLineString getGeom() {
        return geom;
    }

    public OffsetDateTime getEffectiveFrom() {
        return effectiveFrom;
    }

    public OffsetDateTime getEffectiveTo() {
        return effectiveTo;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
