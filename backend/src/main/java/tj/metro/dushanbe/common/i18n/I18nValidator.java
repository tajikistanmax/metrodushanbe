package tj.metro.dushanbe.common.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tj.metro.dushanbe.common.error.BadRequestException;

public final class I18nValidator {

    private static final List<String> REQUIRED_LANGUAGES = List.of("tg", "ru", "en");

    private I18nValidator() {
    }

    public static void requireAll(Map<String, String> names, String fieldPrefix) {
        List<String> missing = new ArrayList<>();
        for (String lang : REQUIRED_LANGUAGES) {
            if (names == null || names.get(lang) == null || names.get(lang).isBlank()) {
                missing.add(fieldPrefix + "." + lang);
            }
        }
        if (!missing.isEmpty()) {
            throw new BadRequestException("validation.i18n_incomplete",
                    "Не заполнены обязательные языки (tg/ru/en) поля '" + fieldPrefix + "'",
                    Map.of("field", fieldPrefix, "requiredLanguages", REQUIRED_LANGUAGES, "missing", missing));
        }
    }
}
