-- V005: модуль контента — новостные статьи (news article).
-- Источник: ТЗ metrodushanbe-v2.md §6.2.7 (CMS-01/CMS-03, жизненный цикл
--   draft -> review -> scheduled -> published -> archived) + BR-CMS-1
--   (гейт полноты языков публичных полей title/body: tg/ru/en, §8.9).
-- Тексты — JSONB title_i18n/body_i18n {"tg","ru","en"}, как name_i18n у сети (V001).

-- ---------------------------------------------------------------------------
-- Новостные статьи
-- ---------------------------------------------------------------------------
CREATE TABLE news_article (
    id              UUID PRIMARY KEY,
    slug            VARCHAR(160) NOT NULL UNIQUE,   -- стабильный публичный идентификатор URL
    status          VARCHAR(16)  NOT NULL,
    title_i18n      JSONB        NOT NULL,
    body_i18n       JSONB        NOT NULL,
    cover_media_url VARCHAR(512),                    -- URL обложки в медиатеке (CMS-02), необязателен
    published_at    TIMESTAMPTZ,                     -- NULL, пока статья не опубликована
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Статусы редакционного жизненного цикла — по ТЗ §6.2.7 (CMS-03)
    CONSTRAINT ck_news_article_status CHECK (
        status IN ('draft', 'review', 'scheduled', 'published', 'archived')
    )
);

-- ---------------------------------------------------------------------------
-- Индексы:
--  * ix_news_article_status_published_at — под основную выборку публичного API
--    (ContentService.publishedNews: status='published' ORDER BY published_at DESC);
--  * ix_news_article_published_at — выборки/сортировки по дате публикации;
--  * уникальный индекс по slug создаётся ограничением UNIQUE выше (доступ по слагу).
-- ---------------------------------------------------------------------------
CREATE INDEX ix_news_article_status_published_at ON news_article (status, published_at DESC);
CREATE INDEX ix_news_article_published_at        ON news_article (published_at DESC);
