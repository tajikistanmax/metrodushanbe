package tj.metro.dushanbe.network.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

class MetroStationTest {

    @Test
    void constructorSetsFields() {
        var id = UUID.randomUUID();
        var point = new GeometryFactory().createPoint(new Coordinate(68.7, 38.5));
        var station = new MetroStation(id, "ST-L1-01", "active",
                Map.of("tg", "Истгоҳ", "ru", "Станция", "en", "Station"),
                point, true, List.of("elevator", "ramp"));

        assertEquals(id, station.getId());
        assertEquals("ST-L1-01", station.getCode());
        assertEquals("active", station.getStatus());
        assertEquals("Станция", station.getNameI18n().get("ru"));
        assertEquals(point, station.getPointGeom());
        assertTrue(station.isTransfer());
        assertEquals(List.of("elevator", "ramp"), station.getAccessibility());
        assertEquals(Map.of(), station.getAttrs());
        assertNull(station.getDeletedAt());
    }

    @Test
    void updateDetailsChangesMutableFields() {
        var station = new MetroStation(UUID.randomUUID(), "ST-L1-01", "active",
                Map.of("tg", "Истгоҳ", "ru", "Станция", "en", "Station"),
                new GeometryFactory().createPoint(new Coordinate(68.7, 38.5)),
                true, List.of("elevator"));

        station.updateDetails("under_construction",
                Map.of("tg", "Истгоҳи нав", "ru", "Новая станция", "en", "New station"),
                Map.of("tg", "Тавсиф", "ru", "Описание", "en", "Description"),
                false, List.of("ramp"));

        assertEquals("under_construction", station.getStatus());
        assertEquals("Новая станция", station.getNameI18n().get("ru"));
        assertEquals("Описание", station.getDescriptionI18n().get("ru"));
        assertFalse(station.isTransfer());
        assertEquals(List.of("ramp"), station.getAccessibility());
    }

    @Test
    void softDeleteSetsDeletedAt() {
        var station = new MetroStation(UUID.randomUUID(), "ST-L1-01", "active",
                Map.of("tg", "Истгоҳ", "ru", "Станция", "en", "Station"),
                new GeometryFactory().createPoint(new Coordinate(68.7, 38.5)),
                false, List.of());
        assertNull(station.getDeletedAt());

        var now = OffsetDateTime.now();
        station.softDelete(now);

        assertEquals(now, station.getDeletedAt());
    }

    @Test
    void setPointGeomChangesGeometry() {
        var station = new MetroStation(UUID.randomUUID(), "ST-L1-01", "active",
                Map.of("tg", "Истгоҳ", "ru", "Станция", "en", "Station"),
                new GeometryFactory().createPoint(new Coordinate(68.7, 38.5)),
                false, List.of());

        var newPoint = new GeometryFactory().createPoint(new Coordinate(68.8, 38.6));
        station.setPointGeom(newPoint);

        assertEquals(newPoint, station.getPointGeom());
    }

    @Test
    void onCreateSetsTimestamps() {
        var station = new MetroStation(UUID.randomUUID(), "ST-L1-01", "active",
                Map.of("tg", "Истгоҳ", "ru", "Станция", "en", "Station"),
                new GeometryFactory().createPoint(new Coordinate(68.7, 38.5)),
                false, List.of());
        assertNull(station.getCreatedAt());
        assertNull(station.getEffectiveFrom());

        station.onCreate();

        assertNotNull(station.getCreatedAt());
        assertNotNull(station.getEffectiveFrom());
        assertNotNull(station.getUpdatedAt());
    }

    @Test
    void onUpdateSetsUpdatedAt() {
        var station = new MetroStation(UUID.randomUUID(), "ST-L1-01", "active",
                Map.of("tg", "Истгоҳ", "ru", "Станция", "en", "Station"),
                new GeometryFactory().createPoint(new Coordinate(68.7, 38.5)),
                false, List.of());

        station.onUpdate();

        assertNotNull(station.getUpdatedAt());
    }

    @Test
    void gettersReturnExpectedValues() {
        var id = UUID.randomUUID();
        var point = new GeometryFactory().createPoint(new Coordinate(68.7, 38.5));
        var station = new MetroStation(id, "ST-L1-01", "active",
                Map.of("tg", "Истгоҳ", "ru", "Станция", "en", "Station"),
                point, true, List.of("elevator"));

        assertEquals(id, station.getId());
        assertEquals("ST-L1-01", station.getCode());
        assertEquals("active", station.getStatus());
        assertEquals("Истгоҳ", station.getNameI18n().get("tg"));
        assertEquals(point, station.getPointGeom());
        assertNull(station.getAreaGeom());
        assertTrue(station.isTransfer());
        assertEquals(List.of("elevator"), station.getAccessibility());
        assertEquals(Map.of(), station.getAttrs());
        assertNull(station.getEffectiveFrom());
        assertNull(station.getCreatedAt());
        assertNull(station.getUpdatedAt());
        assertNull(station.getDeletedAt());
    }
}
