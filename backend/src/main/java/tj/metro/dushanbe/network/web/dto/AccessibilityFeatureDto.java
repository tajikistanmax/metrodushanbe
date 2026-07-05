package tj.metro.dushanbe.network.web.dto;

import java.util.Map;

/**
 * Объект доступности станции в ответах API.
 * {@code type} — elevator|escalator|ramp|tactile|audio_assist|accessible_toilet;
 * {@code description} — полный i18n-объект {"tg","ru","en"};
 * {@code status} — available|out_of_service|planned.
 */
public record AccessibilityFeatureDto(String type,
                                      Map<String, String> description,
                                      String status) {
}
