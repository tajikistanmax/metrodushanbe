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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import tj.metro.dushanbe.common.error.BadRequestException;

/**
 * Общие проверки и построение геометрий для admin-write контура.
 * Гейты вынесены сюда, чтобы единообразно возвращать доменные коды ошибок
 * в стиле {@link BadRequestException} (envelope через GlobalExceptionHandler).
 */
final class AdminSupport {

    /** Обязательные языки публичных i18n-полей (tg/ru/en) — BR-NET-4/BR-CMS-1 (§8.9). */
    static final List<String> REQUIRED_LANGUAGES = List.of("tg", "ru", "en");

    /** Потолок размера страницы админских лент — тот же, что у ленты импортов. */
    static final int MAX_PAGE_SIZE = 200;

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

    /**
     * Страница админской ленты в контракте {@code /v1/admin/imports}: {@code page}
     * с нуля, {@code size} зажат в [1..200].
     *
     * <p>Кривые значения зажимаются, а не отвергаются 400: {@code page=-1} или
     * {@code size=100000} — это опечатка в адресной строке, и отдать первую
     * страницу полезнее, чем ошибку. Верхний потолок при этом обязателен: без
     * него {@code ?size=} возвращает эндпоинт ровно к тому, от чего пагинация и
     * спасает.
     *
     * <p>{@code sort} обязан заканчиваться уникальным полем: при неуникальном
     * ключе (например, одинаковый {@code updated_at} у доставок одной рассылки)
     * порядок между страницами не определён, и строка на границе способна
     * попасть в обе страницы или ни в одну.
     */
    static Pageable pageable(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
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
        validateCoordinate(coordinates.get(0), coordinates.get(1), "coordinates", null);
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
            validateCoordinate(p.get(0), p.get(1), "path", i);
            coords[i] = new Coordinate(p.get(0), p.get(1));
        }
        LineString line = GEOMETRY_FACTORY.createLineString(coords);
        return GEOMETRY_FACTORY.createMultiLineString(new LineString[] {line});
    }

    private static void validateCoordinate(double longitude, double latitude,
                                           String field, Integer index) {
        if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                || longitude < -180 || longitude > 180
                || latitude < -90 || latitude > 90) {
            Map<String, Object> details = new java.util.LinkedHashMap<>();
            details.put("field", field);
            if (index != null) {
                details.put("index", index);
            }
            details.put("longitude", longitude);
            details.put("latitude", latitude);
            throw new BadRequestException("validation.coordinates_invalid",
                    "Координаты должны быть конечными: longitude [-180,180], latitude [-90,90]",
                    details);
        }
    }
}
