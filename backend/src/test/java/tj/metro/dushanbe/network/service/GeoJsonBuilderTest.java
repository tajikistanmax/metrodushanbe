package tj.metro.dushanbe.network.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.junit.jupiter.api.DisplayName;
import tj.metro.dushanbe.network.domain.MetroLine;
import tj.metro.dushanbe.network.domain.MetroStation;
import tj.metro.dushanbe.network.web.dto.GeoJsonFeature;
import tj.metro.dushanbe.network.web.dto.GeoJsonFeatureCollection;

/**
 * Юнит-тест сборки GeoJSON FeatureCollection из фикстур —
 * без БД и без Spring-контекста. Фикстуры повторяют фрагмент
 * data/demo-network.geojson (линия L1 и пересадочная станция ST-HUB-CENTER).
 */
class GeoJsonBuilderTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    private final GeoJsonBuilder builder = new GeoJsonBuilder();

    @Test
    @DisplayName("FeatureCollection: схема свойств идентична demo-network.geojson")
    void buildsFeatureCollectionMatchingDemoSchema() {
        MetroLine l1 = new MetroLine(UUID.randomUUID(), "L1", "#E21B2D", "planned",
                Map.of("tg", "Хати 1", "ru", "Линия 1", "en", "Line 1"), 1,
                GF.createMultiLineString(new LineString[]{
                        GF.createLineString(new Coordinate[]{
                                new Coordinate(68.8180, 38.5210),
                                new Coordinate(68.8155, 38.5435)
                        })
                }));
        MetroStation hub = new MetroStation(UUID.randomUUID(), "ST-HUB-CENTER", "planned",
                Map.of("tg", "Маркази шаҳр", "ru", "Центр", "en", "City Center"),
                GF.createPoint(new Coordinate(68.7860, 38.5737)), true,
                List.of("elevator", "escalator", "ramp", "tactile", "audio_assist"));

        GeoJsonFeatureCollection collection = builder.buildNetwork(
                List.of(l1), List.of(hub), Map.of(hub.getId(), List.of("L1", "L2")));

        assertEquals("FeatureCollection", collection.type());
        assertEquals(2, collection.features().size());

        // --- линия ---
        GeoJsonFeature lineFeature = collection.features().get(0);
        assertEquals("Feature", lineFeature.type());
        assertEquals("L1", lineFeature.id());
        assertIterableEquals(
                List.of("feature_type", "code", "name", "color_hex", "status", "sort_order"),
                lineFeature.properties().keySet());
        assertEquals("line", lineFeature.properties().get("feature_type"));
        assertEquals("L1", lineFeature.properties().get("code"));
        assertEquals("#E21B2D", lineFeature.properties().get("color_hex"));
        assertEquals("planned", lineFeature.properties().get("status"));
        assertEquals(1, lineFeature.properties().get("sort_order"));
        // порядок языков в name — tg, ru, en, как в демо-файле
        @SuppressWarnings("unchecked")
        Map<String, String> lineName = (Map<String, String>) lineFeature.properties().get("name");
        assertIterableEquals(List.of("tg", "ru", "en"), lineName.keySet());
        assertEquals("Хати 1", lineName.get("tg"));
        // одиночная MultiLineString разворачивается в LineString, как в демо-файле
        assertEquals("LineString", lineFeature.geometry().get("type"));
        assertEquals(
                List.of(List.of(68.8180, 38.5210), List.of(68.8155, 38.5435)),
                lineFeature.geometry().get("coordinates"));

        // --- станция ---
        GeoJsonFeature stationFeature = collection.features().get(1);
        assertEquals("Feature", stationFeature.type());
        assertEquals("ST-HUB-CENTER", stationFeature.id());
        assertIterableEquals(
                List.of("feature_type", "code", "name", "status", "lines", "is_transfer", "accessibility"),
                stationFeature.properties().keySet());
        assertEquals("station", stationFeature.properties().get("feature_type"));
        assertEquals("ST-HUB-CENTER", stationFeature.properties().get("code"));
        assertEquals("planned", stationFeature.properties().get("status"));
        assertEquals(List.of("L1", "L2"), stationFeature.properties().get("lines"));
        assertEquals(Boolean.TRUE, stationFeature.properties().get("is_transfer"));
        assertEquals(List.of("elevator", "escalator", "ramp", "tactile", "audio_assist"),
                stationFeature.properties().get("accessibility"));
        assertEquals("Point", stationFeature.geometry().get("type"));
        assertEquals(List.of(68.7860, 38.5737), stationFeature.geometry().get("coordinates"));
    }

    @Test
    @DisplayName("Станция без привязки к линиям получает пустой массив lines")
    void stationWithoutLinesGetsEmptyLinesArray() {
        MetroStation station = new MetroStation(UUID.randomUUID(), "ST-X", "planned",
                Map.of("tg", "Т", "ru", "Т", "en", "T"),
                GF.createPoint(new Coordinate(68.0, 38.0)), false, List.of());

        GeoJsonFeatureCollection collection = builder.buildNetwork(List.of(), List.of(station), Map.of());

        GeoJsonFeature feature = collection.features().get(0);
        assertEquals(List.of(), feature.properties().get("lines"));
        assertEquals(Boolean.FALSE, feature.properties().get("is_transfer"));
    }

    @Test
    @DisplayName("Линия без геометрии сериализуется с geometry = null")
    void lineWithoutGeometryHasNullGeometry() {
        MetroLine line = new MetroLine(UUID.randomUUID(), "L9", "#082742", "planned",
                Map.of("tg", "Хати 9", "ru", "Линия 9", "en", "Line 9"), 9, null);

        GeoJsonFeatureCollection collection = builder.buildNetwork(List.of(line), List.of(), Map.of());

        assertNull(collection.features().get(0).geometry());
    }
}
