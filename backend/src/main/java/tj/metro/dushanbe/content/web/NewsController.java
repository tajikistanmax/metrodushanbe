package tj.metro.dushanbe.content.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.content.service.ContentService;
import tj.metro.dushanbe.content.web.dto.NewsArticleDto;

/**
 * Новости (модуль контента). Итоговые пути с учётом context-path: /api/v1/news...
 * Публично отдаются только статьи в статусе published (ТЗ §6.2.7).
 * Контракт — docs/dev-conventions.md §3.
 */
@RestController
@RequestMapping("/v1/news")
@Tag(name = "News", description = "Новости и публикации портала")
public class NewsController {

    private final ContentService contentService;

    public NewsController(ContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping
    @Operation(summary = "Список опубликованных новостей",
            description = "Возвращает только published-статьи, отсортированные по дате публикации "
                    + "(published_at) по убыванию. Поля title/body — полные i18n-объекты {tg, ru, en}.")
    public List<NewsArticleDto> list() {
        return contentService.publishedNews();
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Новостная статья по слагу",
            description = "Возвращает опубликованную статью по стабильному слагу. "
                    + "404 news.not_found — если статьи нет или она не опубликована.")
    public NewsArticleDto bySlug(@PathVariable("slug") String slug) {
        return contentService.publishedBySlug(slug);
    }
}
