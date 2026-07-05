package tj.metro.dushanbe.imports.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tj.metro.dushanbe.imports.service.NetworkImportValidator.Kind;
import tj.metro.dushanbe.imports.service.NetworkImportValidator.ParsedFeature;

/**
 * Юнит-тесты валидатора GeoJSON-импорта без БД и Spring: проверяют разбор валидных
 * фич линии/станции и построчные ошибки для битых (нет i18n, неверный статус/цвет,
 * неверная геометрия, неизвестный feature_type).
 */
class NetworkImportValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final NetworkImportValidator validator = new NetworkImportValidator();

    private ParsedFeature parse(String json) throws Exception {
        JsonNode node = mapper.readTree(json);
        return validator.parse(node);
    }

    @Test
    void validLineParsesWithoutErrors() throws Exception {
        ParsedFeature f = parse("""
                {"type":"Feature","id":"L1","properties":{
                  "feature_type":"line","code":"L1",
                  "name":{"tg":"Хат","ru":"Линия","en":"Line"},
                  "color_hex":"#E21B2D","status":"planned","sort_order":1},
                  "geometry":{"type":"LineString","coordinates":[[68.8,38.5],[68.7,38.6]]}}""");

        assertTrue(f.valid(), "валидная линия не должна давать ошибок: " + f.errors());
        assertEquals(Kind.LINE, f.kind());
        assertEquals("L1", f.code());
        assertEquals("#E21B2D", f.colorHex());
        assertEquals(1, f.sortOrder());
        assertEquals(2, f.path().size());
        assertEquals("Линия", f.name().get("ru"));
    }

    @Test
    void validStationParsesWithLinesAndAccessibility() throws Exception {
        ParsedFeature f = parse("""
                {"type":"Feature","id":"ST-1","properties":{
                  "feature_type":"station","code":"ST-1",
                  "name":{"tg":"Ист","ru":"Станция","en":"Station"},
                  "status":"planned","lines":["L1","L2"],"is_transfer":true,
                  "accessibility":["elevator","tactile"]},
                  "geometry":{"type":"Point","coordinates":[68.8,38.5]}}""");

        assertTrue(f.valid(), "валидная станция не должна давать ошибок: " + f.errors());
        assertEquals(Kind.STATION, f.kind());
        assertEquals("ST-1", f.code());
        assertEquals(java.util.List.of(68.8, 38.5), f.coordinates());
        assertEquals(java.util.List.of("L1", "L2"), f.lineCodes());
        assertTrue(f.isTransfer());
        assertEquals(java.util.List.of("elevator", "tactile"), f.accessibility());
    }

    @Test
    void stationMissingLanguageIsInvalid() throws Exception {
        ParsedFeature f = parse("""
                {"type":"Feature","properties":{
                  "feature_type":"station","code":"ST-2",
                  "name":{"tg":"Ист","ru":"Станция"},
                  "status":"planned"},
                  "geometry":{"type":"Point","coordinates":[68.8,38.5]}}""");

        assertFalse(f.valid());
        assertTrue(f.errors().stream().anyMatch(e -> e.contains("name.en")),
                "должна быть ошибка о недостающем name.en: " + f.errors());
    }

    @Test
    void lineInvalidStatusAndColorAreReported() throws Exception {
        ParsedFeature f = parse("""
                {"type":"Feature","properties":{
                  "feature_type":"line","code":"L9",
                  "name":{"tg":"Хат","ru":"Линия","en":"Line"},
                  "color_hex":"red","status":"bogus"},
                  "geometry":{"type":"LineString","coordinates":[[68.8,38.5],[68.7,38.6]]}}""");

        assertFalse(f.valid());
        assertTrue(f.errors().stream().anyMatch(e -> e.contains("status")), "ожидалась ошибка статуса");
        assertTrue(f.errors().stream().anyMatch(e -> e.contains("color_hex")), "ожидалась ошибка цвета");
    }

    @Test
    void stationWithWrongGeometryTypeIsInvalid() throws Exception {
        ParsedFeature f = parse("""
                {"type":"Feature","properties":{
                  "feature_type":"station","code":"ST-3",
                  "name":{"tg":"Ист","ru":"Станция","en":"Station"},
                  "status":"planned"},
                  "geometry":{"type":"LineString","coordinates":[[68.8,38.5],[68.7,38.6]]}}""");

        assertFalse(f.valid());
        assertTrue(f.errors().stream().anyMatch(e -> e.contains("Point")), "ожидалась ошибка геометрии Point");
    }

    @Test
    void unknownFeatureTypeIsUnknownKind() throws Exception {
        ParsedFeature f = parse("""
                {"type":"Feature","properties":{"feature_type":"zone","code":"Z1"},
                  "geometry":{"type":"Polygon","coordinates":[]}}""");

        assertFalse(f.valid());
        assertEquals(Kind.UNKNOWN, f.kind());
    }
}
