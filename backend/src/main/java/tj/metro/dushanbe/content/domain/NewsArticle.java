package tj.metro.dushanbe.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Новостная статья модуля контента (таблица {@code news_article}, ТЗ §6.2.7).
 * Тексты — JSONB {@code title_i18n}/{@code body_i18n} вида {"tg","ru","en"}
 * (как {@code name_i18n} у станций/линий); {@code slug} — стабильный внешний
 * идентификатор публичного URL (никогда не меняется); {@code published_at}
 * заполняется при переходе в статус {@code published}.
 *
 * <p>Жизненный цикл (CMS-03): draft -> review -> scheduled -> published -> archived.
 * Гейт полноты языков (BR-CMS-1) применяется сервисом при переходе в {@code published}.
 */
@Entity
@Table(name = "news_article")
public class NewsArticle {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Стабильный слаг публичного URL (никогда не меняется), например "metro-line-1-progress". */
    @Column(name = "slug", nullable = false, unique = true, length = 160)
    private String slug;

    /** draft|review|scheduled|published|archived (жизненный цикл ТЗ §6.2.7, CMS-03). */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "title_i18n", nullable = false)
    private Map<String, String> titleI18n;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "body_i18n", nullable = false)
    private Map<String, String> bodyI18n;

    /** URL обложки в медиатеке (CMS-02); необязателен. */
    @Column(name = "cover_media_url", length = 512)
    private String coverMediaUrl;

    /** Момент публикации; NULL, пока статья не переведена в published. */
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Конструктор для JPA. */
    protected NewsArticle() {
    }

    public NewsArticle(UUID id, String slug, String status,
                       Map<String, String> titleI18n, Map<String, String> bodyI18n,
                       String coverMediaUrl, OffsetDateTime publishedAt) {
        this.id = id;
        this.slug = slug;
        this.status = status;
        this.titleI18n = titleI18n;
        this.bodyI18n = bodyI18n;
        this.coverMediaUrl = coverMediaUrl;
        this.publishedAt = publishedAt;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /**
     * Перевод статьи в статус {@code published} с фиксацией момента публикации.
     * Гейт полноты языков (BR-CMS-1) проверяет вызывающий сервис ДО этого перехода;
     * сама сущность лишь применяет валидное изменение состояния.
     */
    public void markPublished(OffsetDateTime publishedAt) {
        this.status = "published";
        this.publishedAt = publishedAt;
    }

    /**
     * Редакционное изменение статьи (ADM-03/CMS): тексты и обложка. Стабильный слаг
     * и статус здесь не меняются (публикация — через {@link #markPublished}).
     * Гейт полноты языков (BR-CMS-1) применяется при публикации, а не при правке черновика.
     */
    public void updateEditorial(Map<String, String> titleI18n, Map<String, String> bodyI18n,
                                String coverMediaUrl) {
        this.titleI18n = titleI18n;
        this.bodyI18n = bodyI18n;
        this.coverMediaUrl = coverMediaUrl;
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getStatus() {
        return status;
    }

    public Map<String, String> getTitleI18n() {
        return titleI18n;
    }

    public Map<String, String> getBodyI18n() {
        return bodyI18n;
    }

    public String getCoverMediaUrl() {
        return coverMediaUrl;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
