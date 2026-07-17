package tj.metro.dushanbe.imports.service.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import tj.metro.dushanbe.imports.service.NetworkImportValidator.Kind;
import tj.metro.dushanbe.imports.service.NetworkImportValidator.ParsedFeature;
import tj.metro.dushanbe.imports.service.parser.NetworkImportParser.ImportOptions;
import tj.metro.dushanbe.imports.service.parser.NetworkImportParser.ParseResult;

/** Тесты разбора плоской CSV-таблицы сети (INT-04/U-OPS-06). */
class CsvImportParserTest {

    private static final String HEADER =
            "entity,code,name_tg,name_ru,name_en,status,color_hex,sort_order,lon,lat,lines,is_transfer,accessibility";

    private final CsvImportParser parser = new CsvImportParser();

    private ParseResult parse(String csv) {
        return parser.parse(csv.getBytes(StandardCharsets.UTF_8), ImportOptions.defaults());
    }

    private static ParsedFeature find(ParseResult result, String code) {
        return result.features().stream().filter(f -> code.equals(f.code())).findFirst().orElseThrow();
    }

    @Test
    void validTableParsesLinesAndStations() {
        ParseResult result = parse(HEADER + "\n"
                + "line,L1,Хати 1,Линия 1,Line 1,planned,#E21B2D,1,,,,,\n"
                + "station,ST-1,Истгоҳи 1,Станция 1,Station 1,planned,,,68.780,38.560,L1,false,wheelchair\n"
                + "station,ST-2,Истгоҳи 2,Станция 2,Station 2,planned,,,68.790,38.570,L1|L2,true,\n");

        assertFalse(result.rejected(), "ошибки верхнего уровня: " + result.topLevelErrors());
        assertEquals(3, result.features().size());

        ParsedFeature line = find(result, "L1");
        assertEquals(Kind.LINE, line.kind());
        assertTrue(line.valid(), "ошибки линии: " + line.errors());
        assertEquals("#E21B2D", line.colorHex());
        assertEquals(1, line.sortOrder());
        assertEquals("Линия 1", line.name().get("ru"));
        // геометрия линии в CSV не выражается — линия импортируется без трассы
        assertNull(line.path());

        ParsedFeature station = find(result, "ST-1");
        assertEquals(Kind.STATION, station.kind());
        assertTrue(station.valid(), "ошибки станции: " + station.errors());
        assertEquals(List.of(68.78, 38.56), station.coordinates());
        assertEquals(List.of("L1"), station.lineCodes());
        assertEquals(List.of("wheelchair"), station.accessibility());
        assertEquals(Boolean.FALSE, station.isTransfer());
        // позиции считает ImportService по порядку строк — парсер их не задаёт
        assertNull(station.linePositions());

        assertEquals(List.of("L1", "L2"), find(result, "ST-2").lineCodes());
        assertEquals(Boolean.TRUE, find(result, "ST-2").isTransfer());
    }

    @Test
    void brokenRowReportsItsLineNumber() {
        // во второй строке данных (строка 3 файла) не хватает колонок
        ParseResult result = parse(HEADER + "\n"
                + "line,L1,Хати 1,Линия 1,Line 1,planned,#E21B2D,1,,,,,\n"
                + "station,ST-1,Истгоҳи 1\n"
                + "station,ST-2,Истгоҳи 2,Станция 2,Station 2,planned,,,68.790,38.570,L1,true,\n");

        assertFalse(result.rejected(), "битая строка не должна отвергать весь файл");
        ParsedFeature broken = find(result, "ST-1");
        assertFalse(broken.valid());
        assertTrue(broken.ref().contains("строка 3"), "ссылка на фичу: " + broken.ref());
        assertTrue(broken.errors().getFirst().contains("строке 3"), broken.errors().toString());
        assertTrue(broken.errors().getFirst().contains("13"), "ожидаемое число колонок: " + broken.errors());
        // остальные строки применяются
        assertTrue(find(result, "L1").valid());
        assertTrue(find(result, "ST-2").valid());
    }

    @Test
    void incompleteI18nIsReportedWithLineNumber() {
        ParseResult result = parse(HEADER + "\n"
                + "station,ST-1,Истгоҳи 1,,Station 1,planned,,,68.780,38.560,L1,false,\n");

        ParsedFeature station = find(result, "ST-1");
        assertFalse(station.valid());
        assertTrue(station.errors().getFirst().contains("name_ru"), station.errors().toString());
        assertTrue(station.ref().contains("строка 2"), station.ref());
    }

    @Test
    void invalidStatusIsReported() {
        ParseResult result = parse(HEADER + "\n"
                + "station,ST-1,Истгоҳи 1,Станция 1,Station 1,нет-такого,,,68.780,38.560,L1,false,\n");

        ParsedFeature station = find(result, "ST-1");
        assertFalse(station.valid());
        assertTrue(station.errors().getFirst().contains("status"), station.errors().toString());
    }

    @Test
    void nonNumericCoordinateIsReported() {
        ParseResult result = parse(HEADER + "\n"
                + "station,ST-1,Истгоҳи 1,Станция 1,Station 1,planned,,,не-число,38.560,L1,false,\n");

        ParsedFeature station = find(result, "ST-1");
        assertFalse(station.valid());
        assertTrue(station.errors().getFirst().contains("lon"), station.errors().toString());
    }

    @Test
    void missingCoordinatesAreReported() {
        ParseResult result = parse(HEADER + "\n"
                + "station,ST-1,Истгоҳи 1,Станция 1,Станция 1,planned,,,,,L1,false,\n");

        ParsedFeature station = find(result, "ST-1");
        assertFalse(station.valid());
        assertTrue(station.errors().getFirst().contains("обязательны"), station.errors().toString());
    }

    @Test
    void unknownEntityIsReported() {
        ParseResult result = parse(HEADER + "\n"
                + "депо,D1,Депо,Депо,Depot,planned,,,68.780,38.560,,,\n");

        ParsedFeature feature = result.features().getFirst();
        assertEquals(Kind.UNKNOWN, feature.kind());
        assertFalse(feature.valid());
        assertTrue(feature.errors().getFirst().contains("entity"));
    }

    @Test
    void invalidLineColorIsReported() {
        ParseResult result = parse(HEADER + "\n"
                + "line,L1,Хати 1,Линия 1,Line 1,planned,красный,1,,,,,\n");

        assertTrue(find(result, "L1").errors().getFirst().contains("color_hex"));
    }

    @Test
    void quotedFieldWithCommaIsParsed() {
        ParseResult result = parse(HEADER + "\n"
                + "station,ST-1,\"Истгоҳи 1, марказ\",\"Станция 1, центр\",\"Station 1, center\","
                + "planned,,,68.780,38.560,L1,false,\n");

        ParsedFeature station = find(result, "ST-1");
        assertTrue(station.valid(), station.errors().toString());
        assertEquals("Станция 1, центр", station.name().get("ru"));
    }

    @Test
    void missingRequiredColumnRejectsWholeFile() {
        ParseResult result = parse("entity,code,name_ru\nline,L1,Линия 1\n");

        assertTrue(result.rejected());
        assertTrue(result.topLevelErrors().getFirst().contains("name_tg"));
    }

    @Test
    void headerOnlyIsRejected() {
        ParseResult result = parse(HEADER + "\n");

        assertTrue(result.rejected());
        assertTrue(result.topLevelErrors().getFirst().contains("ни одной строки"));
    }

    @Test
    void emptyBodyIsRejected() {
        ParseResult result = parser.parse(new byte[0], ImportOptions.defaults());

        assertTrue(result.rejected());
        assertTrue(result.topLevelErrors().getFirst().contains("пустое"));
    }
}
