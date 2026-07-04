package tj.metro.dushanbe.network.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Принадлежность станции линии (таблица {@code metro_station_line}, M:N).
 * {@code positionIndex} — порядковый номер станции вдоль линии (1..N).
 */
@Entity
@Table(name = "metro_station_line")
public class MetroStationLine {

    @EmbeddedId
    private MetroStationLineId id;

    @MapsId("stationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private MetroStation station;

    @MapsId("lineId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "line_id", nullable = false)
    private MetroLine line;

    @Column(name = "position_index", nullable = false)
    private int positionIndex;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** Конструктор для JPA. */
    protected MetroStationLine() {
    }

    public MetroStationLine(MetroStation station, MetroLine line, int positionIndex) {
        this.station = station;
        this.line = line;
        this.id = new MetroStationLineId(station.getId(), line.getId());
        this.positionIndex = positionIndex;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public MetroStationLineId getId() {
        return id;
    }

    public MetroStation getStation() {
        return station;
    }

    public MetroLine getLine() {
        return line;
    }

    public int getPositionIndex() {
        return positionIndex;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
