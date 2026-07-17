package tj.metro.dushanbe.ai.web.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Сводка готовности AI-контура.
 *
 * <p>{@code recommendations} — список i18n-объектов с обязательными tg/ru/en (см. javadoc
 * {@code AiAgentTexts}). {@code posture} остаётся кодом: подписи в словаре консоли
 * ({@code agents.postures}).
 */
public record AiBriefingDto(
        OffsetDateTime generatedAt,
        String posture,
        List<AiAgentDto> agents,
        List<Map<String, String>> recommendations
) {
}
