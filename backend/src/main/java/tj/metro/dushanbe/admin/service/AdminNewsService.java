package tj.metro.dushanbe.admin.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.admin.web.dto.NewsCreateRequest;
import tj.metro.dushanbe.admin.web.dto.NewsUpdateRequest;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.content.domain.NewsArticle;
import tj.metro.dushanbe.content.repository.NewsArticleRepository;
import tj.metro.dushanbe.content.service.ContentService;
import tj.metro.dushanbe.content.web.dto.NewsArticleDto;

/**
 * Admin-write контур новостей (ADM-03/CMS). Создание черновиков, редактирование и
 * публикация. Гейт полноты языков BR-CMS-1 переиспользуется из {@link ContentService#publish}
 * (единый источник правила). Все мутации фиксируются в аудите (BR-ADM-1).
 */
@Service
public class AdminNewsService {

    private final NewsArticleRepository newsRepository;
    private final ContentService contentService;
    private final AuditService auditService;

    public AdminNewsService(NewsArticleRepository newsRepository, ContentService contentService,
                            AuditService auditService) {
        this.newsRepository = newsRepository;
        this.contentService = contentService;
        this.auditService = auditService;
    }

    /** Создать новость в статусе draft (аудит news.create). */
    @Transactional
    public NewsArticleDto create(NewsCreateRequest request, String actor) {
        AdminSupport.requireUnique(newsRepository.existsBySlug(request.slug()),
                "news.slug_exists", "slug", request.slug());
        NewsArticle article = new NewsArticle(UUID.randomUUID(), request.slug(), "draft",
                request.title(), request.body(), request.coverMediaUrl(), null);
        NewsArticle saved = newsRepository.save(article);

        auditService.record(actor, "news.create", "news", saved.getSlug(), null, snapshot(saved));
        return toDto(saved);
    }

    /** Обновить содержание новости по слагу (аудит news.update). */
    @Transactional
    public NewsArticleDto update(String slug, NewsUpdateRequest request, String actor) {
        NewsArticle article = newsRepository.findBySlug(slug).orElseThrow(() -> NotFoundException.news(slug));
        Map<String, Object> before = snapshot(article);
        article.updateEditorial(request.title(), request.body(), request.coverMediaUrl());
        NewsArticle saved = newsRepository.save(article);

        auditService.record(actor, "news.update", "news", slug, before, snapshot(saved));
        return toDto(saved);
    }

    /**
     * Публикация новости с гейтом полноты языков BR-CMS-1: правило применяет
     * {@link ContentService#publish} (бросает 400 при недостающих языках — тогда
     * аудит не пишется). При успехе фиксируется событие news.publish со снимками.
     */
    @Transactional
    public NewsArticleDto publish(String slug, String actor) {
        NewsArticle before = newsRepository.findBySlug(slug).orElseThrow(() -> NotFoundException.news(slug));
        Map<String, Object> beforeSnapshot = snapshot(before);
        NewsArticleDto published = contentService.publish(slug);
        NewsArticle after = newsRepository.findBySlug(slug).orElseThrow(() -> NotFoundException.news(slug));

        auditService.record(actor, "news.publish", "news", slug, beforeSnapshot, snapshot(after));
        return published;
    }

    private static NewsArticleDto toDto(NewsArticle article) {
        return new NewsArticleDto(article.getSlug(), article.getTitleI18n(), article.getBodyI18n(),
                article.getCoverMediaUrl(),
                article.getPublishedAt() != null ? article.getPublishedAt().toInstant() : null);
    }

    private static Map<String, Object> snapshot(NewsArticle article) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("slug", article.getSlug());
        snapshot.put("status", article.getStatus());
        snapshot.put("title", article.getTitleI18n());
        snapshot.put("body", article.getBodyI18n());
        snapshot.put("coverMediaUrl", article.getCoverMediaUrl());
        snapshot.put("publishedAt", article.getPublishedAt() != null
                ? article.getPublishedAt().toInstant().toString() : null);
        return snapshot;
    }
}
