package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Запрос создания новости (ADM-03/CMS). Создаётся в статусе {@code draft};
 * гейт полноты языков (BR-CMS-1) применяется при публикации. {@code title}/{@code body}
 * — i18n-объекты; {@code coverMediaUrl} — необязательная обложка.
 */
public record NewsCreateRequest(
        @NotBlank String slug,
        @NotNull Map<String, String> title,
        @NotNull Map<String, String> body,
        String coverMediaUrl) {
}
