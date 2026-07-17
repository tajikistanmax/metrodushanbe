package tj.metro.dushanbe.imports.service.parser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tj.metro.dushanbe.imports.domain.ImportFormat;
import tj.metro.dushanbe.imports.service.NetworkImportValidator.Kind;
import tj.metro.dushanbe.imports.service.NetworkImportValidator.ParsedFeature;
import tj.metro.dushanbe.network.service.NetworkService;

/**
 * Парсер плоской CSV-таблицы линий и станций (INT-04/U-OPS-06) — простой обменный
 * формат для оператора, которому не нужны ни GeoJSON, ни GTFS: выгрузка из Excel.
 *
 * <h2>Формат</h2>
 * UTF-8 (BOM допускается), разделитель — запятая, кавычки по RFC 4180. Первая строка —
 * заголовок; порядок колонок произвольный, лишние колонки игнорируются. Обязательные
 * колонки: {@code entity}, {@code code}, {@code name_tg}, {@code name_ru}, {@code name_en},
 * {@code status}. Остальные — опциональны и нужны в зависимости от {@code entity}:
 *
 * <table border="1">
 *   <caption>Колонки</caption>
 *   <tr><th>колонка</th><th>для кого</th><th>смысл</th></tr>
 *   <tr><td>{@code entity}</td><td>обе</td><td>{@code line} | {@code station}</td></tr>
 *   <tr><td>{@code code}</td><td>обе</td><td>стабильный код, ключ идемпотентности (IMP-02)</td></tr>
 *   <tr><td>{@code name_tg/name_ru/name_en}</td><td>обе</td><td>i18n-имя; все три обязательны (BR-NET-4)</td></tr>
 *   <tr><td>{@code status}</td><td>обе</td><td>статус жизненного цикла (см. NetworkService)</td></tr>
 *   <tr><td>{@code color_hex}</td><td>line</td><td>#RRGGBB, обязателен для линии</td></tr>
 *   <tr><td>{@code sort_order}</td><td>line</td><td>целое, порядок линии в списках</td></tr>
 *   <tr><td>{@code lon}, {@code lat}</td><td>station</td><td>координаты EPSG:4326, обязательны</td></tr>
 *   <tr><td>{@code lines}</td><td>station</td><td>коды линий станции через {@code |}</td></tr>
 *   <tr><td>{@code is_transfer}</td><td>station</td><td>{@code true|false|1|0|yes|no}</td></tr>
 *   <tr><td>{@code accessibility}</td><td>station</td><td>теги доступности через {@code |}</td></tr>
 *   <tr><td>{@code description_tg/ru/en}</td><td>station</td><td>необязательное описание</td></tr>
 * </table>
 *
 * <h2>Пример</h2>
 * <pre>
 * entity,code,name_tg,name_ru,name_en,status,color_hex,sort_order,lon,lat,lines,is_transfer,accessibility
 * line,L1,Хати 1,Линия 1,Line 1,planned,#E21B2D,1,,,,,
 * station,ST-1,Истгоҳи 1,Станция 1,Station 1,planned,,,68.780,38.560,L1,false,wheelchair
 * station,ST-2,Истгоҳи 2,Станция 2,Station 2,planned,,,68.790,38.570,L1|L2,true,
 * </pre>
 *
 * <h2>Порядок станций на линии</h2>
 * Задаётся порядком строк станций в файле (первая строка станции линии → позиция 1) —
 * так же, как в GeoJSON; позиции считает {@code ImportService}. Геометрия линии
 * (LineString) в CSV не выражается: линия импортируется без трассы, обновление трассы —
 * через GeoJSON или admin-API. Это осознанный компромисс простоты формата.
 *
 * <h2>Ошибки</h2>
 * Каждая проблемная строка попадает в отчёт (IMP-03) с НОМЕРОМ СТРОКИ файла в
 * {@code featureRef} — оператор должен видеть, что править в исходной таблице.
 * Отсутствие обязательной колонки — ошибка верхнего уровня: файл отвергается целиком,
 * чтобы не завалить отчёт одинаковыми построчными ошибками.
 */
@Component
public class CsvImportParser implements NetworkImportParser {

    /** Колонки, без которых таблица бессмысленна для любой сущности. */
    private static final List<String> REQUIRED_COLUMNS =
            List.of("entity", "code", "name_tg", "name_ru", "name_en", "status");

    /** Разделитель значений внутри одной ячейки (lines, accessibility). */
    private static final String MULTI_VALUE_SEPARATOR = "\\|";

    private static final java.util.regex.Pattern COLOR_HEX =
            java.util.regex.Pattern.compile("^#[0-9A-Fa-f]{6}$");

    @Override
    public String format() {
        return ImportFormat.CSV;
    }

    @Override
    public ParseResult parse(byte[] source, ImportOptions options) {
        if (source == null || source.length == 0) {
            return ParseResult.reject("тело импорта пустое: ожидалась CSV-таблица с заголовком");
        }
        CsvTable table = CsvTable.parse(new String(source, StandardCharsets.UTF_8));
        if (table.header().isEmpty()) {
            return ParseResult.reject("не удалось прочитать строку заголовка CSV");
        }
        List<String> missing = table.missingColumns(REQUIRED_COLUMNS);
        if (!missing.isEmpty()) {
            return ParseResult.reject("в заголовке CSV отсутствуют обязательные колонки: " + missing);
        }
        if (table.rows().isEmpty()) {
            return ParseResult.reject("в CSV нет ни одной строки данных (только заголовок)");
        }

        List<ParsedFeature> features = new ArrayList<>();
        for (CsvTable.Row row : table.rows()) {
            features.add(parseRow(table, row));
        }
        return ParseResult.of(features);
    }

    private ParsedFeature parseRow(CsvTable table, CsvTable.Row row) {
        String code = table.get(row, "code");
        String ref = ref(row, code);
        List<String> errors = new ArrayList<>();

        // «Рваная» строка — самая частая беда ручных выгрузок; сообщаем явно и сразу.
        if (row.cells().size() != table.header().size()) {
            errors.add("в строке " + row.lineNumber() + " ожидалось " + table.header().size()
                    + " колонок (по заголовку), получено " + row.cells().size());
            return new ParsedFeature(Kind.UNKNOWN, ref, code, null, null, null, null, null,
                    null, null, null, null, null, null, errors, List.of());
        }

        String entity = lower(table.get(row, "entity"));
        if ("line".equals(entity)) {
            return parseLine(table, row, ref, code, errors);
        }
        if ("station".equals(entity)) {
            return parseStation(table, row, ref, code, errors);
        }
        errors.add("неизвестное или отсутствующее значение колонки 'entity': "
                + (entity == null ? "<пусто>" : entity) + " (допустимо: line, station)");
        return new ParsedFeature(Kind.UNKNOWN, ref, code, null, null, null, null, null,
                null, null, null, null, null, null, errors, List.of());
    }

    private ParsedFeature parseLine(CsvTable table, CsvTable.Row row, String ref, String code,
                                    List<String> errors) {
        requireCode(code, errors);
        Map<String, String> name = i18n(table, row, "name", errors);
        String status = requireIn(table.get(row, "status"), NetworkService.LINE_STATUSES, errors);
        String colorHex = table.get(row, "color_hex");
        if (colorHex == null || !COLOR_HEX.matcher(colorHex).matches()) {
            errors.add("color_hex должен быть в формате #RRGGBB, получено: " + colorHex);
        }
        Integer sortOrder = integer(table.get(row, "sort_order"), "sort_order", errors);

        return new ParsedFeature(Kind.LINE, ref, code, name, null, status, colorHex, sortOrder,
                null, null, null, null, null, null, errors, List.of());
    }

    private ParsedFeature parseStation(CsvTable table, CsvTable.Row row, String ref, String code,
                                       List<String> errors) {
        requireCode(code, errors);
        Map<String, String> name = i18n(table, row, "name", errors);
        String status = requireIn(table.get(row, "status"), NetworkService.STATION_STATUSES, errors);
        Map<String, String> description = optionalI18n(table, row, "description");
        List<Double> coordinates = coordinates(table, row, errors);
        Boolean isTransfer = bool(table.get(row, "is_transfer"), errors).orElse(null);
        List<String> accessibility = multiValue(table.get(row, "accessibility"));
        List<String> lineCodes = multiValue(table.get(row, "lines"));

        return new ParsedFeature(Kind.STATION, ref, code, name, description, status, null, null,
                coordinates, null, isTransfer, accessibility, lineCodes, null, errors, List.of());
    }

    // ---- Гейты полей ------------------------------------------------------

    /** Номер строки в ссылке обязателен: без него оператор не найдёт место правки. */
    private static String ref(CsvTable.Row row, String code) {
        return code == null ? "строка " + row.lineNumber()
                : "строка " + row.lineNumber() + " (" + code + ")";
    }

    private static void requireCode(String code, List<String> errors) {
        if (code == null) {
            errors.add("обязательная колонка 'code' пуста");
        }
    }

    private static Map<String, String> i18n(CsvTable table, CsvTable.Row row, String field,
                                            List<String> errors) {
        Map<String, String> map = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (String language : List.of("tg", "ru", "en")) {
            String value = table.get(row, field + "_" + language);
            if (value == null) {
                missing.add(field + "_" + language);
            } else {
                map.put(language, value);
            }
        }
        if (!missing.isEmpty()) {
            errors.add("не заполнены обязательные языки i18n-поля '" + field + "': " + missing);
        }
        return map.isEmpty() ? null : map;
    }

    private static Map<String, String> optionalI18n(CsvTable table, CsvTable.Row row, String field) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String language : List.of("tg", "ru", "en")) {
            String value = table.get(row, field + "_" + language);
            if (value != null) {
                map.put(language, value);
            }
        }
        return map.isEmpty() ? null : map;
    }

    private static String requireIn(String value, java.util.Collection<String> allowed, List<String> errors) {
        if (value == null || !allowed.contains(value)) {
            errors.add("недопустимое значение 'status': " + value
                    + " (допустимо: " + allowed.stream().sorted().toList() + ")");
        }
        return value;
    }

    private static List<Double> coordinates(CsvTable table, CsvTable.Row row, List<String> errors) {
        Double lon = decimal(table.get(row, "lon"), "lon", errors);
        Double lat = decimal(table.get(row, "lat"), "lat", errors);
        if (lon == null || lat == null) {
            if (table.get(row, "lon") == null || table.get(row, "lat") == null) {
                errors.add("координаты станции обязательны: колонки 'lon' и 'lat'");
            }
            return null;
        }
        return List.of(lon, lat);
    }

    private static Double decimal(String value, String field, List<String> errors) {
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value.replace(',', '.'));
        } catch (NumberFormatException ex) {
            errors.add("колонка '" + field + "' должна быть числом, получено: " + value);
            return null;
        }
    }

    private static Integer integer(String value, String field, List<String> errors) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            errors.add("колонка '" + field + "' должна быть целым числом, получено: " + value);
            return null;
        }
    }

    private static Optional<Boolean> bool(String value, List<String> errors) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (List.of("true", "1", "yes", "да").contains(normalized)) {
            return Optional.of(Boolean.TRUE);
        }
        if (List.of("false", "0", "no", "нет").contains(normalized)) {
            return Optional.of(Boolean.FALSE);
        }
        errors.add("колонка 'is_transfer' должна быть true|false, получено: " + value);
        return Optional.empty();
    }

    private static List<String> multiValue(String value) {
        if (value == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String part : value.split(MULTI_VALUE_SEPARATOR)) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
