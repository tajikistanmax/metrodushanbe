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
import org.locationtech.jts.geom.Point;

/**
 * Выход станции (таблица {@code station_exit}, ТЗ §5.1, NET-03).
 * Точка входа/выхода станции: названия — JSONB {@code name_i18n} вида {"tg","ru","en"};
 * геометрия — Point в EPSG:4326 (PostGIS, JTS); {@code isAccessible} — признак
 * безбарьерного выхода (лифт/пандус до улицы).
 */
@Entity
@Table(name = "station_exit")
public class StationExit {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Станция, которой принадлежит выход. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private MetroStation station;

    /** Стабильный внешний идентификатор, например "EX-ST-L1-01-A". */
    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name_i18n", nullable = false)
    private Map<String, String> nameI18n;

    @Column(name = "point_geom", nullable = false)
    private Point pointGeom;

    @Column(name = "is_accessible", nullable = false)
    private boolean isAccessible;

    /** Порядок вывода выходов в карточке станции (1..N). */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Конструктор для JPA. */
    protected StationExit() {
    }

    public StationExit(UUID id, MetroStation station, String code, Map<String, String> nameI18n,
                       Point pointGeom, boolean isAccessible, int sortOrder) {
        this.id = id;
        this.station = station;
        this.code = code;
        this.nameI18n = nameI18n;
        this.pointGeom = pointGeom;
        this.isAccessible = isAccessible;
        this.sortOrder = sortOrder;
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

    public String getCode() {
        return code;
    }

    public Map<String, String> getNameI18n() {
        return nameI18n;
    }

    public Point getPointGeom() {
        return pointGeom;
    }

    public boolean isAccessible() {
        return isAccessible;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
