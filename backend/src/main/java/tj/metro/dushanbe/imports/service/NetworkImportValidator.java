package tj.metro.dushanbe.imports.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tj.metro.dushanbe.network.service.NetworkService;

/**
 * Валидатор фич GeoJSON-импорта сети. Разбирает одну фичу FeatureCollection
 * (образец — {@code data/demo-network.geojson}) в нормализованное представление
 * {@link ParsedFeature}, попутно собирая построчные ошибки (IMP-03): обязательные
 * поля, полнота i18n-имён (tg/ru/en), допустимость статуса, корректность геометрии.
 *
 * <p>Чистая логика без БД и Spring-состояния — легко покрывается юнит-тестами.
 * Фактический апсерт линий/станций выполняет {@link ImportService}, переиспользуя
 * admin-сервисы; здесь — только разбор и валидация входа. Обвязка формата (чтение
 * тела, разбор верхнего уровня) — в
 * {@link tj.metro.dushanbe.imports.service.parser.GeoJsonImportParser}; этот класс
 * остаётся форматно-специфичным разбором одной фичи GeoJSON.
 */
@Component
public class NetworkImportValidator {

    /** Обязательные языки i18n-полей (tg/ru/en) — BR-NET-4 (§8.9). */
    private static final List<String> REQUIRED_LANGUAGES = List.of("tg", "ru", "en");

    private static final Pattern COLOR_HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    /** Тип фичи по полю {@code feature_type}. */
    public enum Kind { LINE, STATION, UNKNOWN }

    /**
     * Нормализованная фича импорта — общий «язык» всех форматов (geojson/gtfs/csv):
     * парсеры формата приводят вход именно к ней, а {@link ImportService} применяет её,
     * не зная, откуда она пришла. {@link #errors} пуст ⇒ фича валидна и может быть
     * применена. Поля заполняются в меру наличия во входе (невалидные — остаются null).
     *
     * @param linePositions явные позиции станции на линиях ({@code lineCode → position},
     *        1-based). Для GeoJSON/CSV — null: там порядок задаётся порядком следования
     *        станций во входе и считается {@link ImportService}. GTFS же выводит порядок
     *        из stop_times, и станция-пересадка стоит на разных позициях разных линий —
     *        порядком во входе это не выразить, поэтому позиции передаются явно.
     * @param warnings замечания, не отменяющие применение фичи (severity=warning в
     *        IMP-03-отчёте). Основной потребитель — GTFS: им помечаются i18n-заглушки
     *        (см. {@link tj.metro.dushanbe.imports.service.parser.GtfsImportParser}).
     */
    public record ParsedFeature(Kind kind,
                                String ref,
                                String code,
                                java.util.Map<String, String> name,
                                java.util.Map<String, String> description,
                                String status,
                                String colorHex,
                                Integer sortOrder,
                                List<Double> coordinates,
                                List<List<Double>> path,
                                Boolean isTransfer,
                                List<String> accessibility,
                                List<String> lineCodes,
                                java.util.Map<String, Integer> linePositions,
                                List<String> errors,
                                List<String> warnings) {

        public boolean valid() {
            return errors.isEmpty() && kind != Kind.UNKNOWN;
        }
    }

    /** Разбор и валидация одной фичи FeatureCollection. */
    public ParsedFeature parse(JsonNode feature) {
        JsonNode props = feature.path("properties");
        JsonNode geometry = feature.path("geometry");
        String featureType = text(props, "feature_type");
        String ref = firstNonBlank(text(props, "code"), text(feature, "id"), "$");

        if ("line".equals(featureType)) {
            return parseLine(props, geometry, ref);
        }
        if ("station".equals(featureType)) {
            return parseStation(props, geometry, ref);
        }
        List<String> errors = new ArrayList<>();
        errors.add("неизвестный или отсутствующий feature_type: " + (featureType == null ? "<нет>" : featureType));
        return new ParsedFeature(Kind.UNKNOWN, ref, null, null, null, null, null, null,
                null, null, null, null, null, null, errors, List.of());
    }

    private ParsedFeature parseLine(JsonNode props, JsonNode geometry, String ref) {
        List<String> errors = new ArrayList<>();
        String code = requireText(props, "code", errors);
        java.util.Map<String, String> name = requireI18n(props, "name", errors);
        String status = requireIn(props, "status", NetworkService.LINE_STATUSES, errors);
        String colorHex = text(props, "color_hex");
        if (colorHex == null || !COLOR_HEX.matcher(colorHex).matches()) {
            errors.add("color_hex должен быть в формате #RRGGBB, получено: " + colorHex);
        }
        Integer sortOrder = props.hasNonNull("sort_order") && props.get("sort_order").isNumber()
                ? props.get("sort_order").asInt() : null;
        List<List<Double>> path = lineStringCoordinates(geometry, errors);

        return new ParsedFeature(Kind.LINE, ref, code, name, null, status, colorHex, sortOrder,
                null, path, null, null, null, null, errors, List.of());
    }

    private ParsedFeature parseStation(JsonNode props, JsonNode geometry, String ref) {
        List<String> errors = new ArrayList<>();
        String code = requireText(props, "code", errors);
        java.util.Map<String, String> name = requireI18n(props, "name", errors);
        String status = requireIn(props, "status", NetworkService.STATION_STATUSES, errors);
        java.util.Map<String, String> description = optionalI18n(props, "description");
        List<Double> coordinates = pointCoordinates(geometry, errors);
        Boolean isTransfer = props.hasNonNull("is_transfer") ? props.get("is_transfer").asBoolean() : null;
        List<String> accessibility = stringArray(props.path("accessibility"));
        List<String> lineCodes = stringArray(props.path("lines"));

        return new ParsedFeature(Kind.STATION, ref, code, name, description, status, null, null,
                coordinates, null, isTransfer, accessibility, lineCodes, null, errors, List.of());
    }

    // ---- Гейты полей ------------------------------------------------------

    private static String requireText(JsonNode node, String field, List<String> errors) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            errors.add("обязательное поле '" + field + "' отсутствует или пусто");
            return null;
        }
        return value;
    }

    private static java.util.Map<String, String> requireI18n(JsonNode node, String field, List<String> errors) {
        JsonNode obj = node.path(field);
        java.util.Map<String, String> value = optionalI18n(node, field);
        List<String> missing = new ArrayList<>();
        for (String lang : REQUIRED_LANGUAGES) {
            String s = obj.path(lang).asText(null);
            if (s == null || s.isBlank()) {
                missing.add(field + "." + lang);
            }
        }
        if (!missing.isEmpty()) {
            errors.add("не заполнены обязательные языки i18n-поля '" + field + "': " + missing);
        }
        return value;
    }

    private static java.util.Map<String, String> optionalI18n(JsonNode node, String field) {
        JsonNode obj = node.path(field);
        if (!obj.isObject()) {
            return null;
        }
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        obj.fields().forEachRemaining(e -> {
            if (e.getValue().isTextual()) {
                map.put(e.getKey(), e.getValue().asText());
            }
        });
        return map.isEmpty() ? null : map;
    }

    private static String requireIn(JsonNode node, String field, java.util.Collection<String> allowed,
                                    List<String> errors) {
        String value = text(node, field);
        if (value == null || !allowed.contains(value)) {
            errors.add("недопустимое значение '" + field + "': " + value
                    + " (допустимо: " + allowed.stream().sorted().toList() + ")");
        }
        return value;
    }

    // ---- Геометрия --------------------------------------------------------

    private static List<Double> pointCoordinates(JsonNode geometry, List<String> errors) {
        if (!"Point".equals(text(geometry, "type"))) {
            errors.add("geometry станции должна быть Point, получено: " + text(geometry, "type"));
            return null;
        }
        JsonNode coords = geometry.path("coordinates");
        if (!coords.isArray() || coords.size() != 2
                || !coords.get(0).isNumber() || !coords.get(1).isNumber()) {
            errors.add("coordinates станции должны быть массивом [lon, lat]");
            return null;
        }
        return List.of(coords.get(0).asDouble(), coords.get(1).asDouble());
    }

    private static List<List<Double>> lineStringCoordinates(JsonNode geometry, List<String> errors) {
        if (!"LineString".equals(text(geometry, "type"))) {
            errors.add("geometry линии должна быть LineString, получено: " + text(geometry, "type"));
            return null;
        }
        JsonNode coords = geometry.path("coordinates");
        if (!coords.isArray() || coords.size() < 2) {
            errors.add("coordinates линии должны содержать не менее двух точек [lon, lat]");
            return null;
        }
        List<List<Double>> path = new ArrayList<>();
        for (JsonNode p : coords) {
            if (!p.isArray() || p.size() != 2 || !p.get(0).isNumber() || !p.get(1).isNumber()) {
                errors.add("каждая точка трассы линии — массив [lon, lat]");
                return null;
            }
            path.add(List.of(p.get(0).asDouble(), p.get(1).asDouble()));
        }
        return path;
    }

    // ---- Утилиты чтения JSON ----------------------------------------------

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isTextual() ? v.asText() : null;
    }

    private static List<String> stringArray(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(n -> {
                if (n.isTextual()) {
                    list.add(n.asText());
                }
            });
        }
        return list;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "$";
    }
}
