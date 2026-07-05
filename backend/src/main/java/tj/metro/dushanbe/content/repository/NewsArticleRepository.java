package tj.metro.dushanbe.content.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tj.metro.dushanbe.content.domain.NewsArticle;

/**
 * Доступ к новостным статьям модуля контента.
 * Публичная выборка опирается на индекс {@code ix_news_article_status_published_at}
 * (V005): список отдаёт только статьи в статусе {@code published}.
 */
public interface NewsArticleRepository extends JpaRepository<NewsArticle, UUID> {

    /** Опубликованные статьи, новые сверху (для публичного списка). */
    List<NewsArticle> findByStatusOrderByPublishedAtDesc(String status);

    /** Статья по слагу в заданном статусе (публичный доступ — только published). */
    Optional<NewsArticle> findBySlugAndStatus(String slug, String status);

    /** Статья по слагу вне зависимости от статуса (редакционные операции, гейт публикации). */
    Optional<NewsArticle> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
