package tj.metro.dushanbe.imports.web.dto;

import java.util.List;

/**
 * Страница ленты заданий импорта в ответах админского API.
 * {@code items} — джобы текущей страницы; остальные поля — метаданные пагинации.
 */
public record ImportPageDto(List<ImportJobDto> items,
                            int page,
                            int size,
                            long totalElements,
                            int totalPages) {
}
