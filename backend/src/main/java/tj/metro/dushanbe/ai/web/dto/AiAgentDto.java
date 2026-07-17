package tj.metro.dushanbe.ai.web.dto;

import java.util.List;
import java.util.Map;

/**
 * Карточка AI-агента для консоли.
 *
 * <p>Свободные тексты ({@code role}, {@code capabilities}, {@code signals}, {@code nextAction}) —
 * i18n-объекты {@code Map<String,String>} с обязательными tg/ru/en, как {@code *_i18n jsonb} у
 * остальных сущностей платформы. Резолвинг {@code ?lang=} в backend не реализован: отдаём весь
 * объект, язык выбирает фронт через {@code pickName}. Обоснование выбора — в javadoc
 * {@code AiAgentTexts}.
 *
 * <p>{@code modelProvider}, {@code modelClass} и {@code status} остаются кодами: это не тексты, а
 * технические идентификаторы; подписи статусов живут в словаре консоли ({@code agents.statuses}).
 */
public record AiAgentDto(
        String code,
        Map<String, String> name,
        Map<String, String> role,
        String modelProvider,
        String modelClass,
        String status,
        List<Map<String, String>> capabilities,
        List<Map<String, String>> signals,
        Map<String, String> nextAction
) {
}
