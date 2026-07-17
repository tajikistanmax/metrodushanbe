package tj.metro.dushanbe.imports.service.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import tj.metro.dushanbe.imports.domain.ImportFormat;
import tj.metro.dushanbe.imports.service.NetworkImportValidator;
import tj.metro.dushanbe.imports.service.NetworkImportValidator.Kind;
import tj.metro.dushanbe.imports.service.NetworkImportValidator.ParsedFeature;
import tj.metro.dushanbe.imports.service.parser.NetworkImportParser.ImportOptions;
import tj.metro.dushanbe.imports.service.parser.NetworkImportParser.ParseResult;

/**
 * Тесты парсера GeoJSON: фиксируют поведение верхнего уровня, вынесенное из ImportService,
 * — тексты ошибок и разбор оболочки FeatureCollection не должны были измениться.
 */
class GeoJsonImportParserTest {

    private final GeoJsonImportParser parser =
            new GeoJsonImportParser(new ObjectMapper(), new NetworkImportValidator());

    private ParseResult parse(String body) {
        return parser.parse(body == null ? null : body.getBytes(StandardCharsets.UTF_8),
                ImportOptions.defaults());
    }

    @Test
    void formatIsGeoJson() {
        assertEquals(ImportFormat.GEOJSON, parser.format());
    }

    @Test
    void validFeatureCollectionIsParsedIntoFeatures() {
        ParseResult result = parse("""
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":{"feature_type":"line","code":"L1",
                    "name":{"tg":"Хат","ru":"Линия","en":"Line"},"color_hex":"#E21B2D",
                    "status":"planned","sort_order":1},
                    "geometry":{"type":"LineString","coordinates":[[68.8,38.5],[68.7,38.6]]}},
                  {"type":"Feature","properties":{"feature_type":"station","code":"ST-1",
                    "name":{"tg":"Ист1","ru":"Станция1","en":"Station1"},"status":"planned","lines":["L1"]},
                    "geometry":{"type":"Point","coordinates":[68.8,38.5]}}]}""");

        assertFalse(result.rejected());
        assertEquals(2, result.features().size());
        ParsedFeature line = result.features().getFirst();
        assertEquals(Kind.LINE, line.kind());
        assertTrue(line.valid(), line.errors().toString());
        assertEquals(List.of(List.of(68.8, 38.5), List.of(68.7, 38.6)), line.path());
        // GeoJSON не задаёт позиции явно — их считает ImportService по порядку фич
        assertNull(result.features().get(1).linePositions());
        assertEquals(List.of(), result.features().get(1).warnings());
    }

    @Test
    void notJsonIsRejectedWithOriginalMessage() {
        ParseResult result = parse("not json");

        assertTrue(result.rejected());
        assertTrue(result.topLevelErrors().getFirst().startsWith("тело импорта не является корректным JSON"));
    }

    @Test
    void wrongTypeIsRejected() {
        ParseResult result = parse("{\"type\":\"Nonsense\"}");

        assertTrue(result.rejected());
        assertEquals("ожидался GeoJSON FeatureCollection (поле type)", result.topLevelErrors().getFirst());
    }

    @Test
    void nullBodyIsRejected() {
        ParseResult result = parse(null);

        assertTrue(result.rejected());
        assertEquals("ожидался GeoJSON FeatureCollection (поле type)", result.topLevelErrors().getFirst());
    }

    @Test
    void featuresMustBeArray() {
        ParseResult result = parse("{\"type\":\"FeatureCollection\",\"features\":{}}");

        assertTrue(result.rejected());
        assertEquals("поле features должно быть массивом фич", result.topLevelErrors().getFirst());
    }

    @Test
    void unknownFeatureTypeIsReportedPerFeature() {
        ParseResult result = parse("""
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":{"feature_type":"depot","code":"D1"},
                   "geometry":{"type":"Point","coordinates":[68.8,38.5]}}]}""");

        assertFalse(result.rejected(), "неизвестная фича — не повод отвергать весь файл");
        assertEquals(Kind.UNKNOWN, result.features().getFirst().kind());
    }
}
