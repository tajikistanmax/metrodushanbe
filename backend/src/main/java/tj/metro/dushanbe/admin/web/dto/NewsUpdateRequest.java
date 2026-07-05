package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Запрос обновления новости (ADM-03/CMS). Стабильный слаг неизменен — из пути.
 * Публикация — отдельным вызовом (с гейтом BR-CMS-1).
 */
public record NewsUpdateRequest(
        @NotNull Map<String, String> title,
        @NotNull Map<String, String> body,
        String coverMediaUrl) {
}
