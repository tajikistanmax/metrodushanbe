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

class AccessibilityFeatureTest {

    @Test
    void constructorSetsFields() {
        var id = UUID.randomUUID();
        var station = new MetroStation(UUID.randomUUID(), "ST-L1-01", "active",
                Map.of("tg", "Истгоҳ", "ru", "Станция", "en", "Station"),
                new GeometryFactory().createPoint(new Coordinate(68.7, 38.5)),
                false, List.of());
        var feature = new AccessibilityFeature(id, station, "elevator",
                Map.of("tg", "Лифт", "ru", "Лифт", "en", "Elevator"), "available");

        assertEquals(id, feature.getId());
        assertEquals(station, feature.getStation());
        assertEquals("elevator", feature.getType());
        assertEquals("Лифт", feature.getDescriptionI18n().get("ru"));
        assertEquals("available", feature.getStatus());
        assertNull(feature.getCreatedAt());
        assertNull(feature.getUpdatedAt());
    }

    @Test
    void testGetters() {
        var id = UUID.randomUUID();
        var station = new MetroStation(UUID.randomUUID(), "ST-L1-01", "active",
                Map.of("tg", "Истгоҳ", "ru", "Станция", "en", "Station"),
                new GeometryFactory().createPoint(new Coordinate(68.7, 38.5)),
                false, List.of());
        var feature = new AccessibilityFeature(id, station, "ramp",
                Map.of("tg", "Пандус", "ru", "Пандус", "en", "Ramp"), "out_of_service");

        assertEquals(id, feature.getId());
        assertEquals(station, feature.getStation());
        assertEquals("ramp", feature.getType());
        assertEquals("Пандус", feature.getDescriptionI18n().get("tg"));
        assertEquals("out_of_service", feature.getStatus());
    }

    @Test
    void onCreateSetsTimestamps() {
        var station = new MetroStation(UUID.randomUUID(), "ST-L1-01", "active",
                Map.of("tg", "Истгоҳ", "ru", "Станция", "en", "Station"),
                new GeometryFactory().createPoint(new Coordinate(68.7, 38.5)),
                false, List.of());
        var feature = new AccessibilityFeature(UUID.randomUUID(), station, "escalator",
                Map.of("tg", "Эскалатор", "ru", "Эскалатор", "en", "Escalator"), "planned");
        assertNull(feature.getCreatedAt());

        feature.onCreate();

        assertNotNull(feature.getCreatedAt());
        assertNotNull(feature.getUpdatedAt());
    }

    @Test
    void onUpdateSetsUpdatedAt() {
        var station = new MetroStation(UUID.randomUUID(), "ST-L1-01", "active",
                Map.of("tg", "Истгоҳ", "ru", "Станция", "en", "Station"),
                new GeometryFactory().createPoint(new Coordinate(68.7, 38.5)),
                false, List.of());
        var feature = new AccessibilityFeature(UUID.randomUUID(), station, "tactile",
                Map.of("tg", "Тактил", "ru", "Тактильный", "en", "Tactile"), "available");

        feature.onUpdate();

        assertNotNull(feature.getUpdatedAt());
    }
}
