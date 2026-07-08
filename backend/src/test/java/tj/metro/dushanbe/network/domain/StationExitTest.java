package tj.metro.dushanbe.network.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

class StationExitTest {

    @Test
    void constructorSetsFields() {
        var id = UUID.randomUUID();
        var station = new MetroStation(UUID.randomUUID(), "ST-L1-01", "active",
                Map.of("tg", "Истгоҳ", "ru", "Станция", "en", "Station"),
                new GeometryFactory().createPoint(new Coordinate(68.7, 38.5)),
                false, List.of());
        var point = new GeometryFactory().createPoint(new Coordinate(68.71, 38.51));

        var exit = new StationExit(id, station, "EX-ST-L1-01-A",
                Map.of("tg", "Баромад", "ru", "Выход", "en", "Exit"),
                point, true, 1);

        assertEquals(id, exit.getId());
        assertEquals(station, exit.getStation());
        assertEquals("EX-ST-L1-01-A", exit.getCode());
        assertEquals("Выход", exit.getNameI18n().get("ru"));
        assertEquals(point, exit.getPointGeom());
        assertTrue(exit.isAccessible());
        assertEquals(1, exit.getSortOrder());
        assertNull(exit.getCreatedAt());
        assertNull(exit.getUpdatedAt());
    }

    @Test
    void testGetters() {
        var id = UUID.randomUUID();
        var station = new MetroStation(UUID.randomUUID(), "ST-L1-01", "active",
                Map.of("tg", "Истгоҳ", "ru", "Станция", "en", "Station"),
                new GeometryFactory().createPoint(new Coordinate(68.7, 38.5)),
                false, List.of());
        var point = new GeometryFactory().createPoint(new Coordinate(68.71, 38.51));

        var exit = new StationExit(id, station, "EX-ST-L1-01-A",
                Map.of("tg", "Баромад", "ru", "Выход", "en", "Exit"),
                point, false, 2);

        assertEquals(id, exit.getId());
        assertEquals(station, exit.getStation());
        assertEquals("EX-ST-L1-01-A", exit.getCode());
        assertEquals("Баромад", exit.getNameI18n().get("tg"));
        assertEquals(point, exit.getPointGeom());
        assertEquals(2, exit.getSortOrder());
    }

    @Test
    void onCreateSetsTimestamps() {
        var station = new MetroStation(UUID.randomUUID(), "ST-L1-01", "active",
                Map.of("tg", "Истгоҳ", "ru", "Станция", "en", "Station"),
                new GeometryFactory().createPoint(new Coordinate(68.7, 38.5)),
                false, List.of());
        var exit = new StationExit(UUID.randomUUID(), station, "EX-ST-L1-01-A",
                Map.of("tg", "Баромад", "ru", "Выход", "en", "Exit"),
                new GeometryFactory().createPoint(new Coordinate(68.71, 38.51)),
                true, 1);
        assertNull(exit.getCreatedAt());
        assertNull(exit.getUpdatedAt());

        exit.onCreate();

        assertNotNull(exit.getCreatedAt());
        assertNotNull(exit.getUpdatedAt());
    }

    @Test
    void onUpdateSetsUpdatedAt() {
        var station = new MetroStation(UUID.randomUUID(), "ST-L1-01", "active",
                Map.of("tg", "Истгоҳ", "ru", "Станция", "en", "Station"),
                new GeometryFactory().createPoint(new Coordinate(68.7, 38.5)),
                false, List.of());
        var exit = new StationExit(UUID.randomUUID(), station, "EX-ST-L1-01-A",
                Map.of("tg", "Баромад", "ru", "Выход", "en", "Exit"),
                new GeometryFactory().createPoint(new Coordinate(68.71, 38.51)),
                true, 1);

        exit.onUpdate();

        assertNotNull(exit.getUpdatedAt());
    }
}
