package tj.metro.dushanbe.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tj.metro.dushanbe.admin.web.dto.NewsCreateRequest;
import tj.metro.dushanbe.admin.web.dto.NewsUpdateRequest;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.content.domain.NewsArticle;
import tj.metro.dushanbe.content.repository.NewsArticleRepository;
import tj.metro.dushanbe.content.service.ContentService;
import tj.metro.dushanbe.content.web.dto.NewsArticleDto;

class AdminNewsServiceTest {

    private static final String ACTOR = "editor-1";
    private static final Map<String, String> FULL_I18N =
            Map.of("tg", "Сарлавҳа", "ru", "Заголовок", "en", "Title");
    private static final Map<String, String> FULL_BODY =
            Map.of("tg", "Матн", "ru", "Текст", "en", "Body");

    private final NewsArticleRepository newsRepository = mock(NewsArticleRepository.class);
    private final ContentService contentService = mock(ContentService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AdminNewsService service =
            new AdminNewsService(newsRepository, contentService, auditService);

    @Test
    void createPersistsDraftAndRecordsAudit() {
        when(newsRepository.existsBySlug("news-1")).thenReturn(false);
        when(newsRepository.save(any(NewsArticle.class))).thenAnswer(inv -> inv.getArgument(0));
        NewsCreateRequest request = new NewsCreateRequest("news-1", FULL_I18N, FULL_BODY, "https://example.com/img.jpg");

        NewsArticleDto dto = service.create(request, ACTOR);

        assertEquals("news-1", dto.slug());
        verify(newsRepository).save(any(NewsArticle.class));
        verify(auditService).record(eq(ACTOR), eq("news.create"), eq("news"), eq("news-1"), isNull(), any());
    }

    @Test
    void createRejectsIncompleteTitleI18n() {
        when(newsRepository.existsBySlug("news-bad")).thenReturn(false);
        NewsCreateRequest request = new NewsCreateRequest("news-bad",
                Map.of("tg", "Сарлавҳа", "ru", "Заголовок"), FULL_BODY, null);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.create(request, ACTOR));

        assertEquals("validation.i18n_incomplete", ex.getCode());
        verify(newsRepository, never()).save(any());
    }

    @Test
    void createRejectsIncompleteBodyI18n() {
        when(newsRepository.existsBySlug("news-bad-body")).thenReturn(false);
        NewsCreateRequest request = new NewsCreateRequest("news-bad-body",
                FULL_I18N, Map.of("tg", "Матн", "ru", "Текст"), null);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.create(request, ACTOR));

        assertEquals("validation.i18n_incomplete", ex.getCode());
        verify(newsRepository, never()).save(any());
    }

    @Test
    void updateModifiesAndRecordsAudit() {
        NewsArticle article = new NewsArticle(UUID.randomUUID(), "news-upd", "draft",
                FULL_I18N, FULL_BODY, null, null);
        when(newsRepository.findBySlug("news-upd")).thenReturn(Optional.of(article));
        when(newsRepository.save(any(NewsArticle.class))).thenAnswer(inv -> inv.getArgument(0));
        NewsUpdateRequest request = new NewsUpdateRequest(FULL_I18N, FULL_BODY, "https://example.com/new.jpg");

        NewsArticleDto dto = service.update("news-upd", request, ACTOR);

        assertEquals("news-upd", dto.slug());
        verify(newsRepository).save(any(NewsArticle.class));
        verify(auditService).record(eq(ACTOR), eq("news.update"), eq("news"), eq("news-upd"), any(), any());
    }

    @Test
    void updateRejectsIncompleteTitleI18n() {
        NewsArticle article = new NewsArticle(UUID.randomUUID(), "news-upd-bad", "draft",
                FULL_I18N, FULL_BODY, null, null);
        when(newsRepository.findBySlug("news-upd-bad")).thenReturn(Optional.of(article));
        NewsUpdateRequest request = new NewsUpdateRequest(
                Map.of("tg", "Сарлавҳа", "ru", "Заголовок"), FULL_BODY, null);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.update("news-upd-bad", request, ACTOR));

        assertEquals("validation.i18n_incomplete", ex.getCode());
        verify(newsRepository, never()).save(any());
    }

    @Test
    void publishDelegatesToContentServiceAndRecordsAudit() {
        NewsArticle before = new NewsArticle(UUID.randomUUID(), "news-pub", "draft",
                FULL_I18N, FULL_BODY, null, null);
        NewsArticle after = new NewsArticle(UUID.randomUUID(), "news-pub", "published",
                FULL_I18N, FULL_BODY, null, OffsetDateTime.ofInstant(Instant.parse("2026-07-05T10:00:00Z"), ZoneOffset.UTC));
        when(newsRepository.findBySlug("news-pub"))
                .thenReturn(Optional.of(before), Optional.of(after));
        NewsArticleDto publishedDto = new NewsArticleDto("news-pub", FULL_I18N, FULL_BODY,
                null, Instant.parse("2026-07-05T10:00:00Z"));
        when(contentService.publish("news-pub")).thenReturn(publishedDto);

        NewsArticleDto dto = service.publish("news-pub", ACTOR);

        assertEquals("news-pub", dto.slug());
        verify(contentService).publish("news-pub");
        verify(auditService).record(eq(ACTOR), eq("news.publish"), eq("news"), eq("news-pub"), any(), any());
    }

    @Test
    void publishThrowsNotFoundForUnknownSlug() {
        when(newsRepository.findBySlug("news-nope")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.publish("news-nope", ACTOR));
    }
}
