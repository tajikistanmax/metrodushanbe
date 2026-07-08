package tj.metro.dushanbe.network.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

class MetroStationLineTest {

    @Test
    void constructorSetsFields() {
        var station = new MetroStation(UUID.randomUUID(), "ST-L1-01", "active",
                Map.of("tg", "Истгоҳ", "ru", "Станция", "en", "Station"),
                new GeometryFactory().createPoint(new Coordinate(68.7, 38.5)),
                false, List.of());
        var line = new MetroLine(UUID.randomUUID(), "L1", "#FF0000", "active",
                Map.of("tg", "Хат", "ru", "Линия", "en", "Line"), 1, null);

        var stationLine = new MetroStationLine(station, line, 3);

        assertNotNull(stationLine.getId());
        assertEquals(station.getId(), stationLine.getId().getStationId());
        assertEquals(line.getId(), stationLine.getId().getLineId());
        assertEquals(station, stationLine.getStation());
        assertEquals(line, stationLine.getLine());
        assertEquals(3, stationLine.getPositionIndex());
        assertNull(stationLine.getCreatedAt());
    }

    @Test
    void gettersReturnExpectedValues() {
        var station = new MetroStation(UUID.randomUUID(), "ST-L1-01", "active",
                Map.of("tg", "Истгоҳ", "ru", "Станция", "en", "Station"),
                new GeometryFactory().createPoint(new Coordinate(68.7, 38.5)),
                false, List.of());
        var line = new MetroLine(UUID.randomUUID(), "L1", "#FF0000", "active",
                Map.of("tg", "Хат", "ru", "Линия", "en", "Line"), 1, null);

        var stationLine = new MetroStationLine(station, line, 5);

        assertEquals(station.getId(), stationLine.getId().getStationId());
        assertEquals(line.getId(), stationLine.getId().getLineId());
        assertEquals(station, stationLine.getStation());
        assertEquals(line, stationLine.getLine());
        assertEquals(5, stationLine.getPositionIndex());
    }

    @Test
    void onCreateSetsCreatedAt() {
        var station = new MetroStation(UUID.randomUUID(), "ST-L1-01", "active",
                Map.of("tg", "Истгоҳ", "ru", "Станция", "en", "Station"),
                new GeometryFactory().createPoint(new Coordinate(68.7, 38.5)),
                false, List.of());
        var line = new MetroLine(UUID.randomUUID(), "L1", "#FF0000", "active",
                Map.of("tg", "Хат", "ru", "Линия", "en", "Line"), 1, null);
        var stationLine = new MetroStationLine(station, line, 2);
        assertNull(stationLine.getCreatedAt());

        stationLine.onCreate();

        assertNotNull(stationLine.getCreatedAt());
    }
}
