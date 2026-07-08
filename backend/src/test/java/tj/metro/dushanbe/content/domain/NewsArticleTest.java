package tj.metro.dushanbe.content.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NewsArticleTest {

    private final Map<String, String> i18n = Map.of("tg", "Салом", "ru", "Привет", "en", "Hello");

    @Test
    void constructorSetsFields() {
        var id = UUID.randomUUID();
        var article = new NewsArticle(id, "test-slug", "draft", i18n, i18n, "https://media.url", null);

        assertEquals(id, article.getId());
        assertEquals("test-slug", article.getSlug());
        assertEquals("draft", article.getStatus());
        assertEquals(i18n, article.getTitleI18n());
        assertEquals(i18n, article.getBodyI18n());
        assertEquals("https://media.url", article.getCoverMediaUrl());
        assertNull(article.getPublishedAt());
    }

    @Test
    void constructorAllowsNullCoverAndPublishedAt() {
        var article = new NewsArticle(UUID.randomUUID(), "slug", "draft", i18n, i18n, null, null);

        assertNull(article.getCoverMediaUrl());
        assertNull(article.getPublishedAt());
    }

    @Test
    void onCreateSetsTimestamps() {
        var article = new NewsArticle(UUID.randomUUID(), "slug", "draft", i18n, i18n, null, null);

        assertNull(article.getCreatedAt());
        assertNull(article.getUpdatedAt());

        article.onCreate();

        assertNotNull(article.getCreatedAt());
        assertNotNull(article.getUpdatedAt());
    }

    @Test
    void onCreateDoesNotOverrideExistingCreatedAt() {
        var article = new NewsArticle(UUID.randomUUID(), "slug", "draft", i18n, i18n, null, null);
        article.onCreate();
        var original = article.getCreatedAt();

        article.onCreate();

        assertEquals(original, article.getCreatedAt());
    }

    @Test
    void onUpdateUpdatesUpdatedAt() {
        var article = new NewsArticle(UUID.randomUUID(), "slug", "draft", i18n, i18n, null, null);
        article.onCreate();
        var before = article.getUpdatedAt();

        article.onUpdate();

        assertNotNull(article.getUpdatedAt());
        assertTrue(article.getUpdatedAt().isAfter(before)
                || article.getUpdatedAt().isEqual(before));
    }

    @Test
    void markPublishedSetsStatusAndPublishedAt() {
        var article = new NewsArticle(UUID.randomUUID(), "slug", "draft", i18n, i18n, null, null);
        var when = OffsetDateTime.now();

        article.markPublished(when);

        assertEquals("published", article.getStatus());
        assertEquals(when, article.getPublishedAt());
    }

    @Test
    void updateEditorialChangesContent() {
        var article = new NewsArticle(UUID.randomUUID(), "slug", "draft", i18n, i18n, "old-url", null);
        var newI18n = Map.of("tg", "Нав", "ru", "Новый", "en", "New");

        article.updateEditorial(newI18n, newI18n, "new-url");

        assertEquals(newI18n, article.getTitleI18n());
        assertEquals(newI18n, article.getBodyI18n());
        assertEquals("new-url", article.getCoverMediaUrl());
    }
}
