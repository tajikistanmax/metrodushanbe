package tj.metro.dushanbe.imports.service.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tj.metro.dushanbe.imports.domain.ImportFormat;
import tj.metro.dushanbe.imports.service.NetworkImportValidator;
import tj.metro.dushanbe.imports.service.NetworkImportValidator.ParsedFeature;

/**
 * Парсер сети из GeoJSON FeatureCollection (INT-04, образец —
 * {@code data/demo-network.geojson}). Отвечает за верхний уровень: декодирование тела,
 * разбор JSON и проверку оболочки FeatureCollection; разбор и валидация отдельной фичи —
 * в {@link NetworkImportValidator}.
 *
 * <p>Исторически эта логика жила прямо в {@code ImportService}; вынесена сюда без
 * изменения поведения и текстов ошибок при появлении форматов gtfs/csv — чтобы
 * применение фич стало общим для всех форматов.
 *
 * <p>Параметры {@link ImportOptions} не используются: GeoJSON несёт и полный i18n-объект
 * {@code name}, и {@code status} в свойствах каждой фичи.
 */
@Component
public class GeoJsonImportParser implements NetworkImportParser {

    private final ObjectMapper objectMapper;
    private final NetworkImportValidator validator;

    public GeoJsonImportParser(ObjectMapper objectMapper, NetworkImportValidator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @Override
    public String format() {
        return ImportFormat.GEOJSON;
    }

    @Override
    public ParseResult parse(byte[] source, ImportOptions options) {
        String body = source == null ? "" : new String(source, StandardCharsets.UTF_8);
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException ex) {
            return ParseResult.reject("тело импорта не является корректным JSON: " + ex.getMessage());
        }
        if (root == null || root.isMissingNode() || !"FeatureCollection".equals(root.path("type").asText(null))) {
            return ParseResult.reject("ожидался GeoJSON FeatureCollection (поле type)");
        }
        JsonNode features = root.path("features");
        if (!features.isArray()) {
            return ParseResult.reject("поле features должно быть массивом фич");
        }
        List<ParsedFeature> parsed = new ArrayList<>();
        for (JsonNode feature : features) {
            parsed.add(validator.parse(feature));
        }
        return ParseResult.of(parsed);
    }
}
