package tj.metro.dushanbe.content.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.content.domain.NewsArticle;
import tj.metro.dushanbe.content.repository.NewsArticleRepository;
import tj.metro.dushanbe.content.web.dto.NewsArticleDto;

/**
 * Публичный контур модуля контента (ТЗ §6.2.7): отдаёт только новостные статьи
 * в статусе {@code published}, новые сверху (по {@code published_at}).
 *
 * <p>Реализует гейт полноты языков BR-CMS-1: перевод статьи в {@code published}
 * запрещён, пока не заполнены все обязательные языки (tg/ru/en, см. §8.9)
 * публичных полей title/body. «Сейчас» берётся из инжектируемого {@link Clock}
 * — для детерминированной тестируемости момента публикации.
 */
@Service
@Transactional(readOnly = true)
public class ContentService {

    /** Опубликованный статус — единственный, видимый публичному API. */
    public static final String STATUS_PUBLISHED = "published";

    /** Обязательные языки публичных полей (docs/dev-conventions.md §6, ТЗ §8.9). */
    public static final List<String> REQUIRED_LANGUAGES = List.of("tg", "ru", "en");

    private final NewsArticleRepository repository;
    private final Clock clock;

    public ContentService(NewsArticleRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Список опубликованных новостей, отсортированный по дате публикации по убыванию. */
    public List<NewsArticleDto> publishedNews() {
        return repository.findByStatusOrderByPublishedAtDesc(STATUS_PUBLISHED).stream()
                .map(this::toDto)
                .toList();
    }

    /** Опубликованная статья по слагу; 404 news.not_found, если её нет или она не опубликована. */
    public NewsArticleDto publishedBySlug(String slug) {
        return repository.findBySlugAndStatus(slug, STATUS_PUBLISHED)
                .map(this::toDto)
                .orElseThrow(() -> NotFoundException.news(slug));
    }

    /**
     * Публикация статьи (переход в {@code published}) с гейтом полноты языков BR-CMS-1:
     * если хотя бы в одном обязательном языке (tg/ru/en) пусты title или body — публикация
     * запрещена (HTTP 400) с перечислением недостающих полей в {@code details}.
     * Момент публикации фиксируется из {@link Clock}.
     */
    @Transactional
    public NewsArticleDto publish(String slug) {
        NewsArticle article = repository.findBySlug(slug)
                .orElseThrow(() -> NotFoundException.news(slug));
        requireAllLanguagesPresent(article);
        article.markPublished(OffsetDateTime.now(clock));
        return toDto(repository.save(article));
    }

    /** Гейт полноты языков BR-CMS-1 для публичных полей title/body. */
    private void requireAllLanguagesPresent(NewsArticle article) {
        List<String> missing = new ArrayList<>();
        for (String lang : REQUIRED_LANGUAGES) {
            if (isBlankValue(article.getTitleI18n(), lang)) {
                missing.add("title." + lang);
            }
            if (isBlankValue(article.getBodyI18n(), lang)) {
                missing.add("body." + lang);
            }
        }
        if (!missing.isEmpty()) {
            throw new BadRequestException(
                    "Публикация запрещена: не заполнены обязательные языки публичных полей (BR-CMS-1)",
                    Map.of("rule", "BR-CMS-1", "slug", article.getSlug(),
                            "requiredLanguages", REQUIRED_LANGUAGES, "missing", missing));
        }
    }

    private NewsArticleDto toDto(NewsArticle article) {
        return new NewsArticleDto(
                article.getSlug(),
                article.getTitleI18n(),
                article.getBodyI18n(),
                article.getCoverMediaUrl(),
                article.getPublishedAt() != null ? article.getPublishedAt().toInstant() : null);
    }

    private static boolean isBlankValue(Map<String, String> i18n, String lang) {
        if (i18n == null) {
            return true;
        }
        String value = i18n.get(lang);
        return value == null || value.isBlank();
    }
}
