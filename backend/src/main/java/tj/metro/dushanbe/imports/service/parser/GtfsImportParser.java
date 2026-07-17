package tj.metro.dushanbe.imports.service.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tj.metro.dushanbe.imports.domain.ImportFormat;
import tj.metro.dushanbe.imports.service.NetworkImportValidator.Kind;
import tj.metro.dushanbe.imports.service.NetworkImportValidator.ParsedFeature;
import tj.metro.dushanbe.network.service.NetworkService;

/**
 * Парсер сети из GTFS-фида (INT-04, MAP-08). GTFS — это ZIP с CSV-файлами; читается
 * штатными {@link GtfsArchive} и {@link CsvTable}, без внешней библиотеки: нам нужна лишь
 * малая, статическая часть спецификации (метро — один route_type), а onebusaway-gtfs
 * тянет собственную модель и зависимости.
 *
 * <p>Тарифы из того же контейнера разбирает {@link FaresGtfsImportParser} (GTFS Fares v2):
 * это другой вид импорта с другим приёмником, поэтому и парсер отдельный, а общий у них
 * лишь способ чтения архива.
 *
 * <h2>Отображение GTFS → модель сети</h2>
 * <table border="1">
 *   <caption>Что во что превращается</caption>
 *   <tr><th>источник</th><th>приёмник</th><th>правило</th></tr>
 *   <tr><td>{@code routes.txt} (route_type=1)</td><td>{@code metro_line}</td>
 *       <td>route_id → code; route_long_name (иначе route_short_name) → name_i18n;
 *           route_color → color_hex (по умолчанию #FFFFFF, как в спецификации GTFS);
 *           route_sort_order → sort_order. Маршруты с иным route_type — не метро,
 *           пропускаются молча (в фиде города это автобусы/троллейбусы).</td></tr>
 *   <tr><td>{@code stops.txt}</td><td>{@code metro_station}</td>
 *       <td>stop_id → code; stop_name → name_i18n; stop_lon/stop_lat → точка (EPSG:4326);
 *           stop_desc → description_i18n; wheelchair_boarding=1 → accessibility
 *           {@code wheelchair}.</td></tr>
 *   <tr><td>{@code trips.txt} + {@code stop_times.txt}</td><td>{@code metro_station_line.position}</td>
 *       <td>для каждого маршрута берётся НАИБОЛЕЕ ПОЛНЫЙ рейс (с максимальным числом
 *           stop_times); его stop_sequence, отсортированный по возрастанию, даёт позиции
 *           1..N. Короткие/оборотные рейсы дали бы неполный порядок, поэтому именно
 *           максимальный.</td></tr>
 *   <tr><td>{@code shapes.txt}</td><td>{@code metro_line.path_geom}</td>
 *       <td>shape выбранного рейса, точки по shape_pt_sequence → LineString. Нет shape —
 *           линия импортируется без геометрии.</td></tr>
 *   <tr><td>{@code transfers.txt}</td><td>{@code metro_station.is_transfer}</td>
 *       <td>станция, участвующая в пересадке между РАЗНЫМИ остановками, помечается
 *           пересадочной; также пересадочной считается станция более чем одной линии.</td></tr>
 * </table>
 *
 * <h2>Иерархия остановок</h2>
 * Платформы ({@code location_type} 0/пусто) с указанным {@code parent_station}
 * сворачиваются в родительскую станцию ({@code location_type}=1): в модели метро
 * станция одна, платформ у неё может быть несколько. Входы/узлы/зоны посадки
 * ({@code location_type} 2/3/4) не импортируются — это не станции.
 *
 * <h2>Одноязычность GTFS — принятый компромисс</h2>
 * GTFS хранит ОДНУ строку названия, а модель требует полный i18n {@code {tg,ru,en}}
 * (BR-NET-4, {@code I18nValidator.requireAll}). Переводы не выдумываются. Решение:
 * <ol>
 *   <li>язык фида определяется явно — параметром импорта {@code lang}, иначе из
 *       {@code agency.txt:agency_lang}; если не удалось определить или язык вне tg|ru|en —
 *       импорт отвергается целиком с понятным сообщением (а не молча угадывается);</li>
 *   <li>исходная строка кладётся в язык фида, а в остальные два языка — ТА ЖЕ строка
 *       как временная заглушка (иначе сущность не пройдёт валидацию и импорт сети из
 *       GTFS был бы невозможен в принципе);</li>
 *   <li>каждая такая заглушка порождает предупреждение (severity=warning) в отчёте
 *       IMP-03: оператор видит через {@code GET /v1/admin/imports/{id}/errors} точный
 *       список «что и на какой язык надо перевести». Джоб при этом остаётся успешным —
 *       данные применены, но долг по переводу зафиксирован явно.</li>
 * </ol>
 * Молчаливое дублирование строки в три языка недопустимо: оно неотличимо от настоящего
 * перевода и навсегда прячет проблему. TODO(MAP-08): {@code translations.txt} (GTFS
 * Translations) — если фид его несёт, заглушки можно заменить настоящими переводами.
 *
 * <h2>Чего парсер не делает</h2>
 * {@code calendar.txt}/{@code calendar_dates.txt} не импортируются: расписание — зона
 * модуля schedule (V011/V014) со своей моделью, а INT-04 здесь про топологию сети.
 * Статус жизненного цикла линий/станций GTFS не несёт вовсе — берётся из параметра
 * импорта {@code status} (по умолчанию {@code active}: фид описывает действующее движение).
 */
@Component
public class GtfsImportParser implements NetworkImportParser {

    private static final String AGENCY = "agency.txt";
    private static final String ROUTES = "routes.txt";
    private static final String STOPS = "stops.txt";
    private static final String TRIPS = "trips.txt";
    private static final String STOP_TIMES = "stop_times.txt";
    private static final String SHAPES = "shapes.txt";
    private static final String TRANSFERS = "transfers.txt";

    /** Читаем только эти файлы: остальное в архиве нас не касается. */
    private static final Set<String> RELEVANT_FILES =
            Set.of(AGENCY, ROUTES, STOPS, TRIPS, STOP_TIMES, SHAPES, TRANSFERS);

    /** route_type=1 — метро/подземка (GTFS Reference). */
    private static final String ROUTE_TYPE_SUBWAY = "1";

    /** location_type=1 — станция (родитель платформ). */
    private static final String LOCATION_TYPE_STATION = "1";

    /** location_type 2/3/4 — вход, узел, зона посадки: не станции. */
    private static final Set<String> NON_STOP_LOCATION_TYPES = Set.of("2", "3", "4");

    private static final List<String> REQUIRED_LANGUAGES = List.of("tg", "ru", "en");

    /** Цвет линии по умолчанию согласно GTFS Reference (route_color по умолчанию FFFFFF). */
    private static final String DEFAULT_COLOR_HEX = "#FFFFFF";

    private static final java.util.regex.Pattern COLOR_HEX =
            java.util.regex.Pattern.compile("^[0-9A-Fa-f]{6}$");

    @Override
    public String format() {
        return ImportFormat.GTFS;
    }

    @Override
    public ParseResult parse(byte[] source, ImportOptions options) {
        if (source == null || source.length == 0) {
            return ParseResult.reject("тело импорта пустое: ожидался GTFS-фид (ZIP-архив)");
        }
        Map<String, String> files;
        try {
            files = GtfsArchive.read(source, RELEVANT_FILES);
        } catch (IOException ex) {
            return ParseResult.reject("не удалось прочитать GTFS-архив (ZIP): " + ex.getMessage());
        }

        List<String> topLevel = new ArrayList<>();
        if (!files.containsKey(ROUTES)) {
            topLevel.add("в GTFS-архиве нет обязательного файла " + ROUTES);
        }
        if (!files.containsKey(STOPS)) {
            topLevel.add("в GTFS-архиве нет обязательного файла " + STOPS);
        }
        if (!topLevel.isEmpty()) {
            return ParseResult.reject(topLevel);
        }

        CsvTable routes = CsvTable.parse(files.get(ROUTES));
        CsvTable stops = CsvTable.parse(files.get(STOPS));
        requireColumns(routes, ROUTES, List.of("route_id", "route_type"), topLevel);
        requireColumns(stops, STOPS, List.of("stop_id", "stop_name", "stop_lat", "stop_lon"), topLevel);

        String language = resolveLanguage(options, files, topLevel);
        String status = resolveStatus(options, topLevel);
        if (!topLevel.isEmpty()) {
            return ParseResult.reject(topLevel);
        }

        List<CsvTable.Row> metroRoutes = new ArrayList<>();
        for (CsvTable.Row row : routes.rows()) {
            if (ROUTE_TYPE_SUBWAY.equals(routes.get(row, "route_type"))) {
                metroRoutes.add(row);
            }
        }
        if (metroRoutes.isEmpty()) {
            return ParseResult.reject("в " + ROUTES + " нет ни одного маршрута метро (route_type=1) — "
                    + "фид не содержит данных, которые можно импортировать в сеть метро");
        }

        StopIndex stopIndex = indexStops(stops);
        Map<String, Trip> trips = readTrips(files, stopIndex);
        Map<String, Trip> chosenTrips = chooseLongestTripPerRoute(trips);
        Map<String, List<List<Double>>> shapes = readShapes(files, chosenTrips);
        Ordering ordering = buildOrdering(routes, metroRoutes, chosenTrips);
        Set<String> transferStations = readTransfers(files, stopIndex);

        List<ParsedFeature> features = new ArrayList<>();
        for (CsvTable.Row row : metroRoutes) {
            features.add(parseRoute(routes, row, language, status, shapes));
        }
        for (String stopId : stopIndex.stationOrder()) {
            features.add(parseStop(stops, stopIndex, stopId, language, status, ordering, transferStations));
        }
        return ParseResult.of(features);
    }

    // ---- Линии ------------------------------------------------------------

    private ParsedFeature parseRoute(CsvTable routes, CsvTable.Row row, String language, String status,
                                     Map<String, List<List<Double>>> shapes) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String routeId = routes.get(row, "route_id");
        String ref = ref(ROUTES, row, routeId);
        if (routeId == null) {
            errors.add("обязательное поле 'route_id' пусто");
            return new ParsedFeature(Kind.UNKNOWN, ref, null, null, null, null, null, null,
                    null, null, null, null, null, null, errors, warnings);
        }

        String rawName = firstNonNull(routes.get(row, "route_long_name"), routes.get(row, "route_short_name"));
        Map<String, String> name = null;
        if (rawName == null) {
            errors.add("у маршрута нет ни route_long_name, ни route_short_name — нечем заполнить название линии");
        } else {
            name = i18n(rawName, language, "название линии '" + routeId + "'", warnings);
        }

        String colorHex = colorHex(routes.get(row, "route_color"), errors);
        Integer sortOrder = integer(routes.get(row, "route_sort_order"), "route_sort_order", errors);
        List<List<Double>> path = shapes.get(routeId);

        return new ParsedFeature(Kind.LINE, ref, routeId, name, null, status, colorHex, sortOrder,
                null, path, null, null, null, null, errors, warnings);
    }

    private static String colorHex(String routeColor, List<String> errors) {
        if (routeColor == null) {
            return DEFAULT_COLOR_HEX;
        }
        String value = routeColor.startsWith("#") ? routeColor.substring(1) : routeColor;
        if (!COLOR_HEX.matcher(value).matches()) {
            errors.add("route_color должен быть шестизначным HEX без '#', получено: " + routeColor);
            return null;
        }
        return "#" + value.toUpperCase(Locale.ROOT);
    }

    // ---- Станции ----------------------------------------------------------

    private ParsedFeature parseStop(CsvTable stops, StopIndex stopIndex, String stopId, String language,
                                    String status, Ordering ordering, Set<String> transferStations) {
        CsvTable.Row row = stopIndex.rowOf(stopId);
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String ref = ref(STOPS, row, stopId);

        String rawName = stops.get(row, "stop_name");
        Map<String, String> name = null;
        if (rawName == null) {
            errors.add("обязательное поле 'stop_name' пусто");
        } else {
            name = i18n(rawName, language, "название станции '" + stopId + "'", warnings);
        }

        Map<String, String> description = null;
        String rawDescription = stops.get(row, "stop_desc");
        if (rawDescription != null) {
            description = i18n(rawDescription, language, "описание станции '" + stopId + "'", warnings);
        }

        List<Double> coordinates = coordinates(stops, row, errors);
        Map<String, Integer> linePositions = ordering.positionsOf(stopId);
        List<String> lineCodes = List.copyOf(linePositions.keySet());
        if (lineCodes.isEmpty()) {
            warnings.add("станция не встречается ни в одном рейсе метро (trips/stop_times) — "
                    + "импортирована без привязки к линиям");
        }
        boolean isTransfer = transferStations.contains(stopId) || lineCodes.size() > 1;

        return new ParsedFeature(Kind.STATION, ref, stopId, name, description, status, null, null,
                coordinates, null, isTransfer, accessibility(stops, row), lineCodes, linePositions,
                errors, warnings);
    }

    private static List<Double> coordinates(CsvTable stops, CsvTable.Row row, List<String> errors) {
        Double lon = decimal(stops.get(row, "stop_lon"), "stop_lon", errors);
        Double lat = decimal(stops.get(row, "stop_lat"), "stop_lat", errors);
        if (lon == null || lat == null) {
            if (stops.get(row, "stop_lon") == null || stops.get(row, "stop_lat") == null) {
                errors.add("обязательные координаты 'stop_lat'/'stop_lon' пусты");
            }
            return null;
        }
        return List.of(lon, lat);
    }

    /** GTFS знает лишь про доступность для кресла-коляски — остальные теги вносит оператор. */
    private static List<String> accessibility(CsvTable stops, CsvTable.Row row) {
        return "1".equals(stops.get(row, "wheelchair_boarding")) ? List.of("wheelchair") : List.of();
    }

    // ---- i18n -------------------------------------------------------------

    /**
     * Строит полный i18n-объект из одноязычной строки GTFS: язык фида получает исходное
     * значение, остальные — его же как временную заглушку, и на каждую заглушку заводится
     * предупреждение (см. раздел «Одноязычность GTFS» в описании класса).
     */
    private static Map<String, String> i18n(String value, String language, String what, List<String> warnings) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(language, value);
        for (String other : REQUIRED_LANGUAGES) {
            if (other.equals(language)) {
                continue;
            }
            map.put(other, value);
            warnings.add(what + ": в GTFS нет перевода на '" + other + "' — временно подставлено значение"
                    + " языка фида '" + language + "' («" + value + "»), требуется перевод оператором");
        }
        return map;
    }

    private String resolveLanguage(ImportOptions options, Map<String, String> files, List<String> topLevel) {
        String explicit = normalizeLanguage(options.language());
        if (explicit != null) {
            if (!REQUIRED_LANGUAGES.contains(explicit)) {
                topLevel.add("недопустимый язык фида '" + options.language()
                        + "' в параметре lang (допустимо: " + REQUIRED_LANGUAGES + ")");
            }
            return explicit;
        }
        String fromAgency = agencyLanguage(files);
        if (fromAgency == null) {
            topLevel.add("не удалось определить язык фида: в " + AGENCY + " нет agency_lang — "
                    + "укажите язык параметром lang (" + REQUIRED_LANGUAGES + "), "
                    + "иначе непонятно, на какой язык класть названия из GTFS");
            return null;
        }
        if (!REQUIRED_LANGUAGES.contains(fromAgency)) {
            topLevel.add("язык фида '" + fromAgency + "' (" + AGENCY + ":agency_lang) не поддерживается "
                    + "(допустимо: " + REQUIRED_LANGUAGES + ") — укажите язык параметром lang");
            return null;
        }
        return fromAgency;
    }

    private static String agencyLanguage(Map<String, String> files) {
        String agency = files.get(AGENCY);
        if (agency == null) {
            return null;
        }
        CsvTable table = CsvTable.parse(agency);
        for (CsvTable.Row row : table.rows()) {
            String value = normalizeLanguage(table.get(row, "agency_lang"));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /** {@code ru-RU}/{@code RU} → {@code ru}: agency_lang приходит как BCP-47. */
    private static String normalizeLanguage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int dash = normalized.indexOf('-');
        return dash > 0 ? normalized.substring(0, dash) : normalized;
    }

    private static String resolveStatus(ImportOptions options, List<String> topLevel) {
        String status = options.status();
        // Один статус на весь фид: он применяется и к линиям, и к станциям.
        if (!NetworkService.LINE_STATUSES.contains(status) || !NetworkService.STATION_STATUSES.contains(status)) {
            List<String> allowed = NetworkService.LINE_STATUSES.stream()
                    .filter(NetworkService.STATION_STATUSES::contains).sorted().toList();
            topLevel.add("недопустимый статус '" + status + "' в параметре status (допустимо: " + allowed + ")");
            return null;
        }
        return status;
    }

    // ---- Индексы GTFS -----------------------------------------------------

    /**
     * Остановки фида: какие из них становятся станциями и куда сворачиваются платформы.
     * {@code stationOf} отображает ЛЮБОЙ stop_id (в т.ч. платформы) в код станции —
     * через него stop_times выходят на станции.
     */
    private record StopIndex(Map<String, CsvTable.Row> rows,
                             Map<String, String> stationOf,
                             List<String> stationOrder) {

        CsvTable.Row rowOf(String stopId) {
            return rows.get(stopId);
        }

        /** null, если stop_id неизвестен фиду (битая ссылка из stop_times/transfers). */
        String stationOf(String stopId) {
            return stationOf.get(stopId);
        }
    }

    private static StopIndex indexStops(CsvTable stops) {
        Map<String, CsvTable.Row> rows = new LinkedHashMap<>();
        Map<String, String> locationTypes = new LinkedHashMap<>();
        Map<String, String> parents = new LinkedHashMap<>();
        for (CsvTable.Row row : stops.rows()) {
            String stopId = stops.get(row, "stop_id");
            if (stopId == null) {
                continue; // строка без идентификатора: ссылаться на неё всё равно нечем
            }
            String locationType = stops.get(row, "location_type");
            if (locationType != null && NON_STOP_LOCATION_TYPES.contains(locationType)) {
                continue;
            }
            rows.putIfAbsent(stopId, row);
            locationTypes.put(stopId, locationType == null ? "0" : locationType);
            String parent = stops.get(row, "parent_station");
            if (parent != null) {
                parents.put(stopId, parent);
            }
        }

        Map<String, String> stationOf = new LinkedHashMap<>();
        for (String stopId : rows.keySet()) {
            String parent = parents.get(stopId);
            boolean parentIsStation = parent != null
                    && LOCATION_TYPE_STATION.equals(locationTypes.get(parent));
            stationOf.put(stopId, parentIsStation ? parent : stopId);
        }
        // Станции — только те остановки, в которые кто-то сворачивается (включая себя);
        // платформы с родителем сюда не попадают и отдельными станциями не импортируются.
        List<String> order = new ArrayList<>(new LinkedHashSet<>(stationOf.values()));
        order.removeIf(stopId -> !rows.containsKey(stopId));
        return new StopIndex(rows, stationOf, order);
    }

    /** Рейс: маршрут, форма трассы и остановки по возрастанию stop_sequence. */
    private record Trip(String routeId, String shapeId, List<String> stationCodes) {
    }

    private static Map<String, Trip> readTrips(Map<String, String> files, StopIndex stopIndex) {
        String tripsText = files.get(TRIPS);
        String stopTimesText = files.get(STOP_TIMES);
        if (tripsText == null || stopTimesText == null) {
            // Без рейсов порядок станций не выводится — не ошибка: сеть импортируется
            // без позиций, оператор проставит их вручную (см. предупреждение по станции).
            return Map.of();
        }
        CsvTable trips = CsvTable.parse(tripsText);
        CsvTable stopTimes = CsvTable.parse(stopTimesText);
        if (!trips.hasColumn("trip_id") || !trips.hasColumn("route_id")
                || !stopTimes.hasColumn("trip_id") || !stopTimes.hasColumn("stop_id")) {
            return Map.of();
        }

        Map<String, String> routeOfTrip = new LinkedHashMap<>();
        Map<String, String> shapeOfTrip = new LinkedHashMap<>();
        for (CsvTable.Row row : trips.rows()) {
            String tripId = trips.get(row, "trip_id");
            String routeId = trips.get(row, "route_id");
            if (tripId == null || routeId == null) {
                continue;
            }
            routeOfTrip.put(tripId, routeId);
            String shapeId = trips.get(row, "shape_id");
            if (shapeId != null) {
                shapeOfTrip.put(tripId, shapeId);
            }
        }

        Map<String, List<Sequenced>> stopsOfTrip = new LinkedHashMap<>();
        for (CsvTable.Row row : stopTimes.rows()) {
            String tripId = stopTimes.get(row, "trip_id");
            String stopId = stopTimes.get(row, "stop_id");
            if (tripId == null || stopId == null || !routeOfTrip.containsKey(tripId)) {
                continue;
            }
            String stationCode = stopIndex.stationOf(stopId);
            if (stationCode == null) {
                continue; // ссылка на неизвестную/неимпортируемую остановку
            }
            Integer sequence = parseInt(stopTimes.get(row, "stop_sequence"));
            if (sequence == null) {
                continue;
            }
            stopsOfTrip.computeIfAbsent(tripId, key -> new ArrayList<>())
                    .add(new Sequenced(sequence, stationCode));
        }

        Map<String, Trip> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Sequenced>> entry : stopsOfTrip.entrySet()) {
            List<Sequenced> sequenced = new ArrayList<>(entry.getValue());
            sequenced.sort(Comparator.comparingInt(Sequenced::sequence));
            List<String> stationCodes = new ArrayList<>();
            for (Sequenced item : sequenced) {
                stationCodes.add(item.value());
            }
            result.put(entry.getKey(), new Trip(routeOfTrip.get(entry.getKey()),
                    shapeOfTrip.get(entry.getKey()), stationCodes));
        }
        return result;
    }

    /** Пара «порядковый номер → значение» для сортировок stop_times/shapes. */
    private record Sequenced(int sequence, String value) {
    }

    /** Для каждого маршрута — самый полный рейс: короткие рейсы дали бы неполный порядок. */
    private static Map<String, Trip> chooseLongestTripPerRoute(Map<String, Trip> trips) {
        Map<String, Trip> chosen = new LinkedHashMap<>();
        for (Trip trip : trips.values()) {
            Trip current = chosen.get(trip.routeId());
            if (current == null || trip.stationCodes().size() > current.stationCodes().size()) {
                chosen.put(trip.routeId(), trip);
            }
        }
        return chosen;
    }

    /** Позиции станций на линиях: {@code stationCode → (lineCode → position)}. */
    private record Ordering(Map<String, Map<String, Integer>> positions) {

        Map<String, Integer> positionsOf(String stationCode) {
            return positions.getOrDefault(stationCode, Map.of());
        }
    }

    private static Ordering buildOrdering(CsvTable routes, List<CsvTable.Row> metroRoutes,
                                          Map<String, Trip> chosenTrips) {
        Map<String, Map<String, Integer>> positions = new LinkedHashMap<>();
        for (CsvTable.Row row : metroRoutes) {
            String routeId = routes.get(row, "route_id");
            Trip trip = routeId == null ? null : chosenTrips.get(routeId);
            if (trip == null) {
                continue;
            }
            int position = 0;
            for (String stationCode : trip.stationCodes()) {
                Map<String, Integer> perLine = positions.computeIfAbsent(stationCode,
                        key -> new LinkedHashMap<>());
                if (perLine.containsKey(routeId)) {
                    continue; // кольцевой/оборотный рейс: станция повторяется — держим первую позицию
                }
                perLine.put(routeId, ++position);
            }
        }
        return new Ordering(positions);
    }

    private static Map<String, List<List<Double>>> readShapes(Map<String, String> files,
                                                              Map<String, Trip> chosenTrips) {
        String shapesText = files.get(SHAPES);
        if (shapesText == null) {
            return Map.of();
        }
        CsvTable shapes = CsvTable.parse(shapesText);
        if (!shapes.hasColumn("shape_id") || !shapes.hasColumn("shape_pt_lat")
                || !shapes.hasColumn("shape_pt_lon")) {
            return Map.of();
        }
        // Собираем только те формы, которые реально нужны выбранным рейсам.
        Map<String, String> routeOfShape = new LinkedHashMap<>();
        for (Trip trip : chosenTrips.values()) {
            if (trip.shapeId() != null) {
                routeOfShape.put(trip.shapeId(), trip.routeId());
            }
        }
        Map<String, Map<Integer, List<Double>>> coordinates = new LinkedHashMap<>();
        for (CsvTable.Row row : shapes.rows()) {
            String shapeId = shapes.get(row, "shape_id");
            if (shapeId == null || !routeOfShape.containsKey(shapeId)) {
                continue;
            }
            Double lat = parseDouble(shapes.get(row, "shape_pt_lat"));
            Double lon = parseDouble(shapes.get(row, "shape_pt_lon"));
            Integer sequence = parseInt(shapes.get(row, "shape_pt_sequence"));
            if (lat == null || lon == null || sequence == null) {
                continue;
            }
            coordinates.computeIfAbsent(shapeId, key -> new LinkedHashMap<>())
                    .put(sequence, List.of(lon, lat));
        }

        Map<String, List<List<Double>>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Integer, List<Double>>> entry : coordinates.entrySet()) {
            List<Integer> sequences = new ArrayList<>(entry.getValue().keySet());
            sequences.sort(Comparator.naturalOrder());
            List<List<Double>> path = new ArrayList<>();
            for (Integer sequence : sequences) {
                path.add(entry.getValue().get(sequence));
            }
            // Трасса из одной точки — не LineString; лучше без геометрии, чем битая.
            if (path.size() >= 2) {
                result.put(routeOfShape.get(entry.getKey()), path);
            }
        }
        return result;
    }

    /** Станции, участвующие в пересадках (transfers.txt между разными остановками). */
    private static Set<String> readTransfers(Map<String, String> files, StopIndex stopIndex) {
        String transfersText = files.get(TRANSFERS);
        if (transfersText == null) {
            return Set.of();
        }
        CsvTable transfers = CsvTable.parse(transfersText);
        if (!transfers.hasColumn("from_stop_id") || !transfers.hasColumn("to_stop_id")) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (CsvTable.Row row : transfers.rows()) {
            String from = stopIndex.stationOf(transfers.get(row, "from_stop_id"));
            String to = stopIndex.stationOf(transfers.get(row, "to_stop_id"));
            if (from == null || to == null || from.equals(to)) {
                continue; // пересадка внутри одной станции — не признак пересадочного узла
            }
            result.add(from);
            result.add(to);
        }
        return result;
    }

    // ---- Утилиты ----------------------------------------------------------

    private static void requireColumns(CsvTable table, String file, List<String> required,
                                       List<String> topLevel) {
        List<String> missing = table.missingColumns(required);
        if (!missing.isEmpty()) {
            topLevel.add("в файле " + file + " нет обязательных колонок: " + missing);
        }
    }

    private static String ref(String file, CsvTable.Row row, String id) {
        String base = file + ":строка " + row.lineNumber();
        return id == null ? base : base + " (" + id + ")";
    }

    private static Integer integer(String value, String field, List<String> errors) {
        if (value == null) {
            return null;
        }
        Integer parsed = parseInt(value);
        if (parsed == null) {
            errors.add("поле '" + field + "' должно быть целым числом, получено: " + value);
        }
        return parsed;
    }

    private static Double decimal(String value, String field, List<String> errors) {
        if (value == null) {
            return null;
        }
        Double parsed = parseDouble(value);
        if (parsed == null) {
            errors.add("поле '" + field + "' должно быть числом, получено: " + value);
        }
        return parsed;
    }

    private static Integer parseInt(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Double parseDouble(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
