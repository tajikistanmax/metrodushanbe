package tj.metro.dushanbe.ticketing.web.dto;

import java.time.Instant;

/**
 * Запись чёрного списка (TKT-06). Только админский контур — пассажиру состав
 * чёрного списка не показывается: это подсказка, какие правила обходить.
 *
 * @param subjectCode для {@code subjectType=token} здесь SHA-256, а не токен
 */
public record BlocklistEntryDto(
        String code,
        String subjectType,
        String subjectCode,
        String reason,
        String createdBy,
        Instant createdAt) {
}
