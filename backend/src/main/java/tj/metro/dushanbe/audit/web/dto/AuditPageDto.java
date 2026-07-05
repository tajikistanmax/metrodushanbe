package tj.metro.dushanbe.audit.web.dto;

import java.util.List;

/**
 * Страница журнала аудита в ответах админского API.
 * {@code items} — события текущей страницы; остальные поля — метаданные пагинации.
 */
public record AuditPageDto(List<AuditEventDto> items,
                           int page,
                           int size,
                           long totalElements,
                           int totalPages) {
}
