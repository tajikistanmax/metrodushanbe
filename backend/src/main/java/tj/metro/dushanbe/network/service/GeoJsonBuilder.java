package tj.metro.dushanbe.network.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;
import tj.metro.dushanbe.network.domain.MetroLine;
import tj.metro.dushanbe.network.domain.MetroStation;
import tj.metro.dushanbe.network.web.dto.GeoJsonFeature;
import tj.metro.dushanbe.network.web.dto.GeoJsonFeatureCollection;

/**
 * Ручная сборка GeoJSON FeatureCollection сети (без сторонних GeoJSON-библиотек).
 * Схема свойств фич идентична data/demo-network.geojson:
 * линии — feature_type/code/name/color_hex/status/sort_order,
 * станции — feature_type/code/name/status/lines/is_transfer/accessibility.
 */
@Component
public class GeoJsonBuilder {

    /** Порядок языков в объекте name — как в демо-файле. */
    private static final List<String> LANGUAGE_ORDER = List.of("tg", "ru", "en");

    /**
     * Собирает FeatureCollection: сначала линии, затем станции.
     *
     * @param lines            линии в требуемом порядке
     * @param stations         станции в требуемом порядке
     * @param stationLineCodes коды линий каждой станции (ключ — id станции)
     */
    public GeoJsonFeatureCollection buildNetwork(List<MetroLine> lines,
                                                 List<MetroStation> stations,
                                                 Map<UUID, List<String>> stationLineCodes) {
        List<GeoJsonFeature> features = new ArrayList<>();
        for (MetroLine line : lines) {
            features.add(lineFeature(line));
        }
        for (MetroStation station : stations) {
            features.add(stationFeature(station, stationLineCodes.getOrDefault(station.getId(), List.of())));
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("crs", "EPSG:4326");
        metadata.put("languages", LANGUAGE_ORDER);
        return new GeoJsonFeatureCollection("FeatureCollection", metadata, features);
    }

    private GeoJsonFeature lineFeature(MetroLine line) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("feature_type", "line");
        properties.put("code", line.getCode());
        properties.put("name", orderedName(line.getNameI18n()));
        properties.put("color_hex", line.getColorHex());
        properties.put("status", line.getStatus());
        properties.put("sort_order", line.getSortOrder());
        return new GeoJsonFeature("Feature", line.getCode(), properties, lineGeometry(line.getGeom()));
    }

    private GeoJsonFeature stationFeature(MetroStation station, List<String> lineCodes) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("feature_type", "station");
        properties.put("code", station.getCode());
        properties.put("name", orderedName(station.getNameI18n()));
        properties.put("status", station.getStatus());
        properties.put("lines", lineCodes);
        properties.put("is_transfer", station.isTransfer());
        properties.put("accessibility", station.getAccessibility() != null ? station.getAccessibility() : List.of());
        return new GeoJsonFeature("Feature", station.getCode(), properties, pointGeometry(station.getPointGeom()));
    }

    /** Ключи name в порядке tg, ru, en (затем прочие), как в демо-файле. */
    private Map<String, String> orderedName(Map<String, String> nameI18n) {
        Map<String, String> ordered = new LinkedHashMap<>();
        if (nameI18n == null) {
            return ordered;
        }
        for (String lang : LANGUAGE_ORDER) {
            String value = nameI18n.get(lang);
            if (value != null) {
                ordered.put(lang, value);
            }
        }
        nameI18n.forEach(ordered::putIfAbsent);
        return ordered;
    }

    /**
     * Геометрия линии. В БД хранится MultiLineString (по DDL ТЗ §6.3.1);
     * одиночную трассу разворачиваем в LineString — как в демо-файле.
     */
    private Map<String, Object> lineGeometry(MultiLineString geom) {
        if (geom == null) {
            return null;
        }
        if (geom.getNumGeometries() == 1) {
            LineString single = (LineString) geom.getGeometryN(0);
            return geometry("LineString", lineCoordinates(single));
        }
        List<List<List<Double>>> multi = new ArrayList<>();
        for (int i = 0; i < geom.getNumGeometries(); i++) {
            multi.add(lineCoordinates((LineString) geom.getGeometryN(i)));
        }
        return geometry("MultiLineString", multi);
    }

    private Map<String, Object> pointGeometry(Point point) {
        if (point == null) {
            return null;
        }
        return geometry("Point", List.of(point.getX(), point.getY()));
    }

    private List<List<Double>> lineCoordinates(LineString lineString) {
        List<List<Double>> coordinates = new ArrayList<>();
        for (Coordinate c : lineString.getCoordinates()) {
            coordinates.add(List.of(c.getX(), c.getY()));
        }
        return coordinates;
    }

    private Map<String, Object> geometry(String type, Object coordinates) {
        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("type", type);
        geometry.put("coordinates", coordinates);
        return geometry;
    }
}
