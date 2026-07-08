package tj.metro.dushanbe.network.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MetroLineTest {

    @Test
    void constructorSetsFields() {
        var id = UUID.randomUUID();
        var line = new MetroLine(id, "L1", "#FF0000", "active",
                Map.of("tg", "Хат", "ru", "Линия", "en", "Line"), 1, null);

        assertEquals(id, line.getId());
        assertEquals("L1", line.getCode());
        assertEquals("#FF0000", line.getColorHex());
        assertEquals("active", line.getStatus());
        assertEquals("Линия", line.getNameI18n().get("ru"));
        assertEquals(1, line.getSortOrder());
        assertNull(line.getGeom());
        assertNull(line.getDeletedAt());
    }

    @Test
    void updateDetailsChangesMutableFields() {
        var line = new MetroLine(UUID.randomUUID(), "L1", "#FF0000", "active",
                Map.of("tg", "Хат", "ru", "Линия", "en", "Line"), 1, null);

        line.updateDetails("#00FF00", "under_construction",
                Map.of("tg", "Хат", "ru", "Линия 1", "en", "Line 1"), 2);

        assertEquals("#00FF00", line.getColorHex());
        assertEquals("under_construction", line.getStatus());
        assertEquals("Линия 1", line.getNameI18n().get("ru"));
        assertEquals(2, line.getSortOrder());
    }

    @Test
    void softDeleteSetsDeletedAtAndEffectiveTo() {
        var line = new MetroLine(UUID.randomUUID(), "L1", "#FF0000", "active",
                Map.of("tg", "Хат", "ru", "Линия", "en", "Line"), 1, null);
        assertNull(line.getDeletedAt());

        var now = OffsetDateTime.now();
        line.softDelete(now);

        assertEquals(now, line.getDeletedAt());
        assertEquals(now, line.getEffectiveTo());
    }

    @Test
    void setGeomChangesGeometry() {
        var line = new MetroLine(UUID.randomUUID(), "L1", "#FF0000", "active",
                Map.of("tg", "Хат", "ru", "Линия", "en", "Line"), 1, null);
        assertNull(line.getGeom());

        var gf = new org.locationtech.jts.geom.GeometryFactory();
        var geom = gf.createMultiLineString();
        line.setGeom(geom);

        assertEquals(geom, line.getGeom());
    }

    @Test
    void onCreateSetsTimestamps() {
        var line = new MetroLine(UUID.randomUUID(), "L1", "#FF0000", "active",
                Map.of("tg", "Хат", "ru", "Линия", "en", "Line"), 1, null);
        assertNull(line.getCreatedAt());
        assertNull(line.getEffectiveFrom());

        line.onCreate();

        assertNotNull(line.getCreatedAt());
        assertNotNull(line.getEffectiveFrom());
        assertNotNull(line.getUpdatedAt());
    }

    @Test
    void onUpdateSetsUpdatedAt() {
        var line = new MetroLine(UUID.randomUUID(), "L1", "#FF0000", "active",
                Map.of("tg", "Хат", "ru", "Линия", "en", "Line"), 1, null);

        line.onUpdate();

        assertNotNull(line.getUpdatedAt());
    }
}
