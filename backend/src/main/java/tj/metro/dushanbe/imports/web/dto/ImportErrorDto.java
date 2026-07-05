package tj.metro.dushanbe.imports.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Ошибка импорта отдельной фичи в ответах админского API (IMP-03).
 * {@code featureRef} — code/id проблемной фичи ({@code $} для ошибок верхнего уровня).
 */
public record ImportErrorDto(UUID id,
                             String featureRef,
                             String message,
                             String severity,
                             Instant at) {
}
