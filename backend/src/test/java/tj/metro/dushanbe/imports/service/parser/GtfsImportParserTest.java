package tj.metro.dushanbe.imports.service.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import tj.metro.dushanbe.imports.service.NetworkImportValidator.Kind;
import tj.metro.dushanbe.imports.service.NetworkImportValidator.ParsedFeature;
import tj.metro.dushanbe.imports.service.parser.NetworkImportParser.ImportOptions;
import tj.metro.dushanbe.imports.service.parser.NetworkImportParser.ParseResult;

/**
 * Тесты разбора GTFS. Фикстуры-архивы собираются программно ({@link ZipOutputStream}),
 * чтобы в репозитории не заводились бинарники, а содержимое фида было видно прямо в тесте.
 */
class GtfsImportParserTest {

    private final GtfsImportParser parser = new GtfsImportParser();

    // ---- Фикстуры ---------------------------------------------------------

    /** Минимальный валидный фид: две линии метро, четыре станции, одна пересадка. */
    private static Map<String, String> validFeed() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("agency.txt", """
                agency_id,agency_name,agency_url,agency_timezone,agency_lang
                MD,Метро Душанбе,https://metro.tj,Asia/Dushanbe,ru
                """);
        files.put("routes.txt", """
                route_id,route_short_name,route_long_name,route_type,route_color,route_sort_order
                L1,1,Красная линия,1,E21B2D,1
                L2,2,Синяя линия,1,1B63E2,2
                B7,7,Автобус до рынка,3,,
                """);
        files.put("stops.txt", """
                stop_id,stop_name,stop_lat,stop_lon,location_type,parent_station,wheelchair_boarding
                ST-1,Истиклол,38.5600,68.7800,1,,1
                ST-1-P1,Истиклол платформа 1,38.5600,68.7800,0,ST-1,
                ST-2,Ватан,38.5700,68.7900,1,,0
                ST-3,Пойтахт,38.5800,68.8000,1,,
                ENT-1,Вход №1,38.5601,68.7801,2,ST-1,
                """);
        files.put("trips.txt", """
                route_id,service_id,trip_id,shape_id
                L1,WD,T1-full,SH1
                L1,WD,T1-short,SH1
                L2,WD,T2-full,SH2
                """);
        files.put("stop_times.txt", """
                trip_id,arrival_time,departure_time,stop_id,stop_sequence
                T1-full,08:00:00,08:00:00,ST-1-P1,1
                T1-full,08:03:00,08:03:00,ST-2,2
                T1-full,08:06:00,08:06:00,ST-3,3
                T1-short,09:00:00,09:00:00,ST-2,1
                T2-full,08:10:00,08:10:00,ST-3,1
                T2-full,08:14:00,08:14:00,ST-2,2
                """);
        files.put("shapes.txt", """
                shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence
                SH1,38.5600,68.7800,1
                SH1,38.5700,68.7900,2
                SH1,38.5800,68.8000,3
                SH2,38.5800,68.8000,1
                SH2,38.5700,68.7900,2
                """);
        files.put("transfers.txt", """
                from_stop_id,to_stop_id,transfer_type
                ST-2,ST-3,2
                """);
        return files;
    }

    private static byte[] zip(Map<String, String> files) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return buffer.toByteArray();
    }

    private static ParsedFeature find(ParseResult result, String code) {
        return result.features().stream().filter(f -> code.equals(f.code())).findFirst().orElseThrow();
    }

    // ---- Разбор валидного фида --------------------------------------------

    @Test
    void validFeedImportsOnlySubwayRoutesAsLines() {
        ParseResult result = parser.parse(zip(validFeed()), ImportOptions.defaults());

        assertFalse(result.rejected(), "валидный фид не должен отвергаться: " + result.topLevelErrors());
        List<String> lineCodes = result.features().stream()
                .filter(f -> f.kind() == Kind.LINE).map(ParsedFeature::code).toList();
        // B7 — автобус (route_type=3), в сеть метро не попадает
        assertEquals(List.of("L1", "L2"), lineCodes);

        ParsedFeature line = find(result, "L1");
        assertTrue(line.valid(), "ошибки линии: " + line.errors());
        assertEquals("#E21B2D", line.colorHex());
        assertEquals("Красная линия", line.name().get("ru"));
        assertEquals(1, line.sortOrder());
        assertEquals("active", line.status());
    }

    @Test
    void lineGeometryComesFromShapeOfChosenTrip() {
        ParseResult result = parser.parse(zip(validFeed()), ImportOptions.defaults());

        ParsedFeature line = find(result, "L1");
        assertEquals(List.of(List.of(68.78, 38.56), List.of(68.79, 38.57), List.of(68.80, 38.58)),
                line.path());
    }

    @Test
    void platformsCollapseIntoParentStationAndEntrancesAreSkipped() {
        ParseResult result = parser.parse(zip(validFeed()), ImportOptions.defaults());

        List<String> stationCodes = result.features().stream()
                .filter(f -> f.kind() == Kind.STATION).map(ParsedFeature::code).toList();
        assertEquals(List.of("ST-1", "ST-2", "ST-3"), stationCodes);
    }

    @Test
    void stationOrderComesFromLongestTripAndIsPerLine() {
        ParseResult result = parser.parse(zip(validFeed()), ImportOptions.defaults());

        // T1-full (3 остановки) побеждает T1-short (1 остановка); платформа свёрнута в ST-1
        assertEquals(Map.of("L1", 1), find(result, "ST-1").linePositions());
        // ST-2 — пересадочная: на L1 вторая, на L2 вторая; порядком во входе это не выразить
        assertEquals(Map.of("L1", 2, "L2", 2), find(result, "ST-2").linePositions());
        assertEquals(Map.of("L1", 3, "L2", 1), find(result, "ST-3").linePositions());
    }

    @Test
    void transferAndMultiLineStationsAreMarkedAsTransfer() {
        ParseResult result = parser.parse(zip(validFeed()), ImportOptions.defaults());

        assertEquals(Boolean.TRUE, find(result, "ST-2").isTransfer());
        assertEquals(Boolean.TRUE, find(result, "ST-3").isTransfer());
        assertEquals(Boolean.FALSE, find(result, "ST-1").isTransfer());
    }

    @Test
    void wheelchairBoardingBecomesAccessibilityTag() {
        ParseResult result = parser.parse(zip(validFeed()), ImportOptions.defaults());

        assertEquals(List.of("wheelchair"), find(result, "ST-1").accessibility());
        assertEquals(List.of(), find(result, "ST-2").accessibility());
    }

    @Test
    void coordinatesAreMappedAsLonLat() {
        ParseResult result = parser.parse(zip(validFeed()), ImportOptions.defaults());

        assertEquals(List.of(68.78, 38.56), find(result, "ST-1").coordinates());
    }

    // ---- i18n-компромисс --------------------------------------------------

    @Test
    void feedLanguageIsTakenFromAgencyAndMissingTranslationsAreFilledAndWarned() {
        ParseResult result = parser.parse(zip(validFeed()), ImportOptions.defaults());

        ParsedFeature station = find(result, "ST-1");
        // язык фида (agency_lang=ru) получает исходную строку
        assertEquals("Истиклол", station.name().get("ru"));
        // остальные языки заполнены той же строкой — иначе сущность не пройдёт I18nValidator
        assertEquals("Истиклол", station.name().get("tg"));
        assertEquals("Истиклол", station.name().get("en"));
        // ...но заглушки видны оператору как предупреждения (по одному на язык)
        assertEquals(2, station.warnings().size(), "предупреждения: " + station.warnings());
        assertTrue(station.warnings().stream().anyMatch(w -> w.contains("'tg'")));
        assertTrue(station.warnings().stream().anyMatch(w -> w.contains("'en'")));
        assertTrue(station.warnings().stream().allMatch(w -> w.contains("требуется перевод")));
        // предупреждения не делают фичу невалидной — данные применяются
        assertTrue(station.valid());
    }

    @Test
    void explicitLanguageOptionOverridesAgencyLang() {
        ParseResult result = parser.parse(zip(validFeed()), new ImportOptions("tg", null));

        ParsedFeature station = find(result, "ST-1");
        assertEquals("Истиклол", station.name().get("tg"));
        assertTrue(station.warnings().stream().anyMatch(w -> w.contains("'ru'")),
                "при языке фида tg перевода не хватает уже на ru: " + station.warnings());
    }

    @Test
    void feedWithoutAgencyLangAndWithoutOptionIsRejectedWithExplanation() {
        Map<String, String> files = validFeed();
        files.put("agency.txt", """
                agency_id,agency_name,agency_url,agency_timezone
                MD,Метро Душанбе,https://metro.tj,Asia/Dushanbe
                """);

        ParseResult result = parser.parse(zip(files), ImportOptions.defaults());

        assertTrue(result.rejected());
        assertTrue(result.topLevelErrors().getFirst().contains("agency_lang"));
        assertTrue(result.topLevelErrors().getFirst().contains("lang"));
    }

    @Test
    void unsupportedFeedLanguageIsRejectedInsteadOfGuessing() {
        Map<String, String> files = validFeed();
        files.put("agency.txt", """
                agency_id,agency_name,agency_url,agency_timezone,agency_lang
                MD,Metro,https://metro.tj,Asia/Dushanbe,fr
                """);

        ParseResult result = parser.parse(zip(files), ImportOptions.defaults());

        assertTrue(result.rejected());
        assertTrue(result.topLevelErrors().getFirst().contains("fr"));
    }

    @Test
    void regionalLanguageTagIsNormalized() {
        Map<String, String> files = validFeed();
        files.put("agency.txt", """
                agency_id,agency_name,agency_url,agency_timezone,agency_lang
                MD,Metro,https://metro.tj,Asia/Dushanbe,ru-RU
                """);

        ParseResult result = parser.parse(zip(files), ImportOptions.defaults());

        assertFalse(result.rejected(), "ru-RU — это ru: " + result.topLevelErrors());
        assertEquals("Истиклол", find(result, "ST-1").name().get("ru"));
    }

    // ---- Невалидный вход: отчёт, а не исключение ---------------------------

    @Test
    void notAZipIsReportedAsError() {
        ParseResult result = parser.parse("это вовсе не архив".getBytes(StandardCharsets.UTF_8),
                ImportOptions.defaults());

        assertTrue(result.rejected());
        assertTrue(result.topLevelErrors().getFirst().contains("GTFS"));
    }

    @Test
    void emptyBodyIsReportedAsError() {
        ParseResult result = parser.parse(new byte[0], ImportOptions.defaults());

        assertTrue(result.rejected());
        assertTrue(result.topLevelErrors().getFirst().contains("пустое"));
    }

    @Test
    void zipWithoutGtfsFilesIsReportedAsError() {
        ParseResult result = parser.parse(zip(Map.of("readme.md", "привет")), ImportOptions.defaults());

        assertTrue(result.rejected());
        assertNotNull(result.topLevelErrors().getFirst());
    }

    @Test
    void missingRequiredFileIsReportedWithItsName() {
        Map<String, String> files = validFeed();
        files.remove("stops.txt");

        ParseResult result = parser.parse(zip(files), ImportOptions.defaults());

        assertTrue(result.rejected());
        assertTrue(result.topLevelErrors().getFirst().contains("stops.txt"));
    }

    @Test
    void missingRequiredColumnIsReportedWithItsName() {
        Map<String, String> files = validFeed();
        files.put("stops.txt", """
                stop_id,stop_lat,stop_lon
                ST-1,38.56,68.78
                """);

        ParseResult result = parser.parse(zip(files), ImportOptions.defaults());

        assertTrue(result.rejected());
        assertTrue(result.topLevelErrors().getFirst().contains("stop_name"));
    }

    @Test
    void feedWithoutSubwayRoutesIsRejected() {
        Map<String, String> files = validFeed();
        files.put("routes.txt", """
                route_id,route_short_name,route_long_name,route_type,route_color
                B7,7,Автобус,3,
                """);

        ParseResult result = parser.parse(zip(files), ImportOptions.defaults());

        assertTrue(result.rejected());
        assertTrue(result.topLevelErrors().getFirst().contains("route_type=1"));
    }

    @Test
    void invalidStatusOptionIsRejected() {
        ParseResult result = parser.parse(zip(validFeed()), new ImportOptions("ru", "нет-такого"));

        assertTrue(result.rejected());
        assertTrue(result.topLevelErrors().getFirst().contains("status"));
    }

    @Test
    void brokenRowsAreReportedPerFeatureNotAsException() {
        Map<String, String> files = validFeed();
        files.put("stops.txt", """
                stop_id,stop_name,stop_lat,stop_lon,location_type
                ST-1,Истиклол,не-число,68.78,1
                ST-2,,38.57,68.79,1
                """);

        ParseResult result = parser.parse(zip(files), ImportOptions.defaults());

        assertFalse(result.rejected(), "битые строки — не повод отвергать весь фид");
        ParsedFeature broken = find(result, "ST-1");
        assertFalse(broken.valid());
        assertTrue(broken.errors().getFirst().contains("stop_lat"));
        // ссылка на фичу ведёт к конкретной строке файла
        assertTrue(broken.ref().contains("stops.txt"), broken.ref());

        ParsedFeature nameless = find(result, "ST-2");
        assertFalse(nameless.valid());
        assertTrue(nameless.errors().getFirst().contains("stop_name"));
    }

    @Test
    void routeWithInvalidColorIsReportedButOtherRoutesSurvive() {
        Map<String, String> files = validFeed();
        files.put("routes.txt", """
                route_id,route_long_name,route_type,route_color
                L1,Красная линия,1,ZZZZZZ
                L2,Синяя линия,1,
                """);

        ParseResult result = parser.parse(zip(files), ImportOptions.defaults());

        assertFalse(find(result, "L1").valid());
        assertTrue(find(result, "L1").errors().getFirst().contains("route_color"));
        // route_color необязателен: по спецификации GTFS его нет ⇒ белый
        assertTrue(find(result, "L2").valid(), "ошибки: " + find(result, "L2").errors());
        assertEquals("#FFFFFF", find(result, "L2").colorHex());
    }

    @Test
    void feedWithoutTripsImportsNetworkWithoutPositionsAndWarns() {
        Map<String, String> files = validFeed();
        files.remove("trips.txt");
        files.remove("stop_times.txt");

        ParseResult result = parser.parse(zip(files), ImportOptions.defaults());

        assertFalse(result.rejected());
        ParsedFeature station = find(result, "ST-1");
        assertTrue(station.valid());
        assertEquals(Map.of(), station.linePositions());
        assertTrue(station.warnings().stream().anyMatch(w -> w.contains("без привязки к линиям")));
    }

    @Test
    void nestedFolderInArchiveIsSupported() {
        Map<String, String> nested = new LinkedHashMap<>();
        validFeed().forEach((name, content) -> nested.put("feed-2026/" + name, content));

        ParseResult result = parser.parse(zip(nested), ImportOptions.defaults());

        assertFalse(result.rejected(), "фид во вложенной папке — обычное дело: " + result.topLevelErrors());
        assertEquals("#E21B2D", find(result, "L1").colorHex());
    }
}
