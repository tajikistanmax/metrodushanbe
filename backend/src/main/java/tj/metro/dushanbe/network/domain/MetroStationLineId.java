package tj.metro.dushanbe.network.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Составной ключ связи станция-линия (таблица {@code metro_station_line}).
 */
@Embeddable
public class MetroStationLineId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "station_id", nullable = false)
    private UUID stationId;

    @Column(name = "line_id", nullable = false)
    private UUID lineId;

    /** Конструктор для JPA. */
    protected MetroStationLineId() {
    }

    public MetroStationLineId(UUID stationId, UUID lineId) {
        this.stationId = stationId;
        this.lineId = lineId;
    }

    public UUID getStationId() {
        return stationId;
    }

    public UUID getLineId() {
        return lineId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MetroStationLineId that)) {
            return false;
        }
        return Objects.equals(stationId, that.stationId) && Objects.equals(lineId, that.lineId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stationId, lineId);
    }
}
