package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Запрос создания объекта доступности станции (ADM-02/NET-03).
 * {@code type} — elevator|escalator|ramp|tactile|audio_assist|accessible_toilet;
 * {@code description} — i18n-объект (tg/ru/en обязательны);
 * {@code status} — available|out_of_service|planned (по умолчанию available).
 */
public record AccessibilityFeatureRequest(
        @NotBlank String type,
        @NotNull Map<String, String> description,
        String status) {
}
