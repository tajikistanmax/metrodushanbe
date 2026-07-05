package tj.metro.dushanbe.content.web.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Новостная статья в ответах публичного API.
 * {@code title}/{@code body} — полные i18n-объекты {"tg","ru","en"};
 * {@code coverMediaUrl} — URL обложки или null; {@code publishedAt} — ISO-8601 UTC.
 * Публично отдаются только статьи в статусе {@code published}, поэтому статус
 * в контракт не выносится.
 */
public record NewsArticleDto(String slug,
                             Map<String, String> title,
                             Map<String, String> body,
                             String coverMediaUrl,
                             Instant publishedAt) {
}
