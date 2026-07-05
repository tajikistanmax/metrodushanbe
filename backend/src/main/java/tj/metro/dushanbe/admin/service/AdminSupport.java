package tj.metro.dushanbe.admin.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import tj.metro.dushanbe.common.error.BadRequestException;

/**
 * Общие проверки и построение геометрий для admin-write контура.
 * Гейты вынесены сюда, чтобы единообразно возвращать доменные коды ошибок
 * в стиле {@link BadRequestException} (envelope через GlobalExceptionHandler).
 */
final class AdminSupport {

    /** Обязательные языки публичных i18n-полей (tg/ru/en) — BR-NET-4/BR-CMS-1 (§8.9). */
    static final List<String> REQUIRED_LANGUAGES = List.of("tg", "ru", "en");

    /** Общая фабрика геометрий в EPSG:4326 (порядок осей GeoJSON — [lon, lat]). */
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    private AdminSupport() {
    }

    /**
     * Гейт полноты языков публичного i18n-поля: каждый из tg/ru/en должен быть задан
     * непустой строкой. Иначе 400 с кодом {@code validation.i18n_incomplete} и списком
     * недостающих полей.
     */
    static void requireLanguages(Map<String, String> value, String field) {
        List<String> missing = new ArrayList<>();
        for (String lang : REQUIRED_LANGUAGES) {
            if (value == null || value.get(lang) == null || value.get(lang).isBlank()) {
                missing.add(field + "." + lang);
            }
        }
        if (!missing.isEmpty()) {
            throw new BadRequestException("validation.i18n_incomplete",
                    "Не заполнены обязательные языки (tg/ru/en) поля '" + field + "'",
                    Map.of("field", field, "requiredLanguages", REQUIRED_LANGUAGES, "missing", missing));
        }
    }

    /** Проверка принадлежности значения множеству допустимых; иначе 400 с заданным кодом. */
    static void requireIn(String value, java.util.Collection<String> allowed, String code, String param) {
        if (value == null || !allowed.contains(value)) {
            throw new BadRequestException(code,
                    "Недопустимое значение поля '" + param + "': " + value,
                    Map.of("field", param, "value", String.valueOf(value),
                            "allowed", allowed.stream().sorted().toList()));
        }
    }

    /** Общая проверка уникальности стабильного кода/слага; иначе 400 с заданным кодом. */
    static void requireUnique(boolean exists, String code, String field, String value) {
        if (exists) {
            throw new BadRequestException(code,
                    "Значение '" + value + "' поля '" + field + "' уже занято",
                    Map.of("field", field, "value", value));
        }
    }

    /** Точка [lon, lat] в EPSG:4326 из пары координат. */
    static Point point(List<Double> coordinates) {
        if (coordinates == null || coordinates.size() != 2
                || coordinates.get(0) == null || coordinates.get(1) == null) {
            throw new BadRequestException("validation.coordinates_invalid",
                    "Координаты точки должны быть массивом [lon, lat]",
                    Map.of("field", "coordinates", "expected", "[lon, lat]"));
        }
        return GEOMETRY_FACTORY.createPoint(new Coordinate(coordinates.get(0), coordinates.get(1)));
    }

    /**
     * MultiLineString из одной трассы — списка точек [lon, lat] (EPSG:4326).
     * Пустой/отсутствующий путь → {@code null} (геометрия линии необязательна).
     */
    static MultiLineString multiLine(List<List<Double>> path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        if (path.size() < 2) {
            throw new BadRequestException("validation.coordinates_invalid",
                    "Трасса линии должна содержать не менее двух точек [lon, lat]",
                    Map.of("field", "path", "points", path.size()));
        }
        Coordinate[] coords = new Coordinate[path.size()];
        for (int i = 0; i < path.size(); i++) {
            List<Double> p = path.get(i);
            if (p == null || p.size() != 2 || p.get(0) == null || p.get(1) == null) {
                throw new BadRequestException("validation.coordinates_invalid",
                        "Каждая точка трассы — массив [lon, lat]",
                        Map.of("field", "path", "index", i));
            }
            coords[i] = new Coordinate(p.get(0), p.get(1));
        }
        LineString line = GEOMETRY_FACTORY.createLineString(coords);
        return GEOMETRY_FACTORY.createMultiLineString(new LineString[] {line});
    }
}
