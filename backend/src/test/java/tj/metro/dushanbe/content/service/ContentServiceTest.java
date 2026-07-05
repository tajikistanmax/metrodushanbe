package tj.metro.dushanbe.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.content.domain.NewsArticle;
import tj.metro.dushanbe.content.repository.NewsArticleRepository;
import tj.metro.dushanbe.content.web.dto.NewsArticleDto;

/**
 * Юнит-тесты ContentService без БД: репозиторий — mock, «сейчас» — Clock.fixed.
 * Сортировку и фильтр status='published' выполняет БД (derived-query
 * {@code findByStatusOrderByPublishedAtDesc}), поэтому здесь проверяются:
 * передача статуса published в репозиторий, маппинг i18n-полей в DTO,
 * 404 по неопубликованному слагу и гейт полноты языков BR-CMS-1.
 */
class ContentServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-05T09:00:00Z");

    private final NewsArticleRepository repository = mock(NewsArticleRepository.class);
    private final ContentService service =
            new ContentService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void publishedNewsQueriesOnlyPublishedAndMapsI18n() {
        NewsArticle article = published("metro-construction-launch", NOW.minusSeconds(3600));
        when(repository.findByStatusOrderByPublishedAtDesc("published")).thenReturn(List.of(article));

        List<NewsArticleDto> news = service.publishedNews();

        verify(repository).findByStatusOrderByPublishedAtDesc("published");
        assertEquals(1, news.size());
        NewsArticleDto dto = news.getFirst();
        assertEquals("metro-construction-launch", dto.slug());
        assertEquals(Map.of("tg", "Сарлавҳа", "ru", "Заголовок", "en", "Title"), dto.title());
        assertEquals(Map.of("tg", "Матн", "ru", "Текст", "en", "Body"), dto.body());
        assertEquals("https://cdn.metro.tj/cover.jpg", dto.coverMediaUrl());
        assertEquals(NOW.minusSeconds(3600), dto.publishedAt());
    }

    @Test
    void publishedBySlugReturnsPublishedArticle() {
        NewsArticle article = published("accessibility-standards", NOW.minusSeconds(7200));
        when(repository.findBySlugAndStatus("accessibility-standards", "published"))
                .thenReturn(Optional.of(article));

        NewsArticleDto dto = service.publishedBySlug("accessibility-standards");

        assertEquals("accessibility-standards", dto.slug());
    }

    @Test
    void publishedBySlugThrowsNotFoundWhenAbsentOrUnpublished() {
        when(repository.findBySlugAndStatus("winter-schedule-draft", "published"))
                .thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.publishedBySlug("winter-schedule-draft"));

        assertEquals("news.not_found", ex.getCode());
    }

    @Test
    void publishTransitionsToPublishedAndStampsClockTime() {
        NewsArticle draft = draft("metro-construction-launch",
                Map.of("tg", "Сарлавҳа", "ru", "Заголовок", "en", "Title"),
                Map.of("tg", "Матн", "ru", "Текст", "en", "Body"));
        when(repository.findBySlug("metro-construction-launch")).thenReturn(Optional.of(draft));
        when(repository.save(any(NewsArticle.class))).thenAnswer(inv -> inv.getArgument(0));

        NewsArticleDto dto = service.publish("metro-construction-launch");

        ArgumentCaptor<NewsArticle> saved = ArgumentCaptor.forClass(NewsArticle.class);
        verify(repository).save(saved.capture());
        assertEquals("published", saved.getValue().getStatus());
        assertEquals(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), saved.getValue().getPublishedAt());
        assertEquals(NOW, dto.publishedAt());
    }

    @Test
    void publishBlockedWhenRequiredLanguageMissing_BR_CMS_1() {
        // body без английского языка — гейт полноты языков должен запретить публикацию
        Map<String, String> title = Map.of("tg", "Сарлавҳа", "ru", "Заголовок", "en", "Title");
        Map<String, String> bodyMissingEn = new HashMap<>();
        bodyMissingEn.put("tg", "Матн");
        bodyMissingEn.put("ru", "Текст");
        NewsArticle draft = draft("line-1-tunnel-progress", title, bodyMissingEn);
        when(repository.findBySlug("line-1-tunnel-progress")).thenReturn(Optional.of(draft));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.publish("line-1-tunnel-progress"));

        Map<?, ?> details = assertInstanceOf(Map.class, ex.getDetails());
        assertEquals("BR-CMS-1", details.get("rule"));
        assertTrue(((List<?>) details.get("missing")).contains("body.en"),
                "details.missing должен указывать на незаполненный body.en");
        // публикация не должна состояться — статус остаётся draft, save не вызывается
        assertEquals("draft", draft.getStatus());
        assertNull(draft.getPublishedAt());
        verify(repository, never()).save(any(NewsArticle.class));
    }

    @Test
    void publishBlockedWhenLanguageValueIsBlank_BR_CMS_1() {
        // пустая строка ("   ") в обязательном языке приравнивается к незаполненному
        Map<String, String> title = new HashMap<>();
        title.put("tg", "Сарлавҳа");
        title.put("ru", "   ");
        title.put("en", "Title");
        NewsArticle draft = draft("metro-construction-launch", title,
                Map.of("tg", "Матн", "ru", "Текст", "en", "Body"));
        when(repository.findBySlug("metro-construction-launch")).thenReturn(Optional.of(draft));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.publish("metro-construction-launch"));

        Map<?, ?> details = assertInstanceOf(Map.class, ex.getDetails());
        assertTrue(((List<?>) details.get("missing")).contains("title.ru"));
        verify(repository, never()).save(any(NewsArticle.class));
    }

    @Test
    void publishThrowsNotFoundForUnknownSlug() {
        when(repository.findBySlug("nope")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.publish("nope"));
    }

    /** Опубликованная статья с фиксированными i18n-текстами и обложкой. */
    private static NewsArticle published(String slug, Instant publishedAt) {
        return new NewsArticle(
                UUID.randomUUID(), slug, "published",
                Map.of("tg", "Сарлавҳа", "ru", "Заголовок", "en", "Title"),
                Map.of("tg", "Матн", "ru", "Текст", "en", "Body"),
                "https://cdn.metro.tj/cover.jpg",
                OffsetDateTime.ofInstant(publishedAt, ZoneOffset.UTC));
    }

    /** Черновик статьи с заданными i18n-полями (для проверок гейта публикации). */
    private static NewsArticle draft(String slug, Map<String, String> title, Map<String, String> body) {
        return new NewsArticle(UUID.randomUUID(), slug, "draft", title, body, null, null);
    }
}
