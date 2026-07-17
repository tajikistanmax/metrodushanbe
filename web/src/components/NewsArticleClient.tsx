"use client";

/**
 * Страница новостной статьи (ТЗ §6.2.7): заголовок, дата, обложка (если есть)
 * и тело. Загрузка — loadNewsArticle(slug) с офлайн-деградацией: при любой
 * ошибке или 404 news.not_found возвращается null и показывается состояние
 * «статья не найдена» (dev-conventions.md §8). Тело хранится одной строкой на
 * язык — разбивается на абзацы по пустым строкам, переносы внутри сохраняются.
 */

import Link from "next/link";
import { useState } from "react";
import { MAIN_CONTENT_ID } from "@/lib/dom-ids";
import { formatNewsDate } from "@/lib/news-data";
import { pickName } from "@/lib/i18n";
import type { NewsArticle } from "@/lib/types";
import { Container } from "@/shared/ui";
import { useI18n } from "./I18nProvider";

/** Обложка со скрытием при ошибке загрузки (внешние URL могут быть недоступны). */
function CoverImage({ src, alt }: { src: string; alt: string }) {
  const [failed, setFailed] = useState(false);
  if (failed) {
    return null;
  }
  return (
    // eslint-disable-next-line @next/next/no-img-element -- внешние обложки CMS, next/image здесь не нужен
    <img
      src={src}
      alt={alt}
      loading="lazy"
      onError={() => setFailed(true)}
      className="mt-6 w-full rounded-panel border border-[var(--border-subtle)] object-cover"
    />
  );
}

export default function NewsArticleClient({ article }: { article: NewsArticle }) {
  const { lang, dict } = useI18n();

  return (
    <main id={MAIN_CONTENT_ID} className="flex-1 py-8 sm:py-12">
      <Container width="narrow">
        <p className="mb-6">
          <Link
            href="/news"
            className="inline-flex items-center gap-1 text-small font-bold text-brand-red hover:underline"
          >
            <span aria-hidden="true">←</span>
            {dict.news.backToList}
          </Link>
        </p>

        <article>
            <h1 className="text-title-l font-bold leading-tight sm:text-title-xl">
              {pickName(article.title, lang)}
            </h1>

            <p className="mt-3 text-caption font-semibold uppercase tracking-[0.08em] text-text-secondary">
              <span className="sr-only">{dict.news.publishedLabel}: </span>
              <time dateTime={article.publishedAt}>
                {formatNewsDate(article.publishedAt, lang)}
              </time>
            </p>

            {article.coverMediaUrl && (
              <CoverImage
                src={article.coverMediaUrl}
                alt={pickName(article.title, lang)}
              />
            )}

            {/* Ширина колонки текста ограничена 65ch: длиннее ~75 символов
                глаз теряет начало следующей строки (SC 1.4.8) */}
            <div className="mt-6 flex max-w-[65ch] flex-col gap-4 text-body leading-relaxed text-[var(--text-primary)]">
              {pickName(article.body, lang)
                .split(/\n{2,}/)
                .map((paragraph, index) => (
                  <p key={index} className="whitespace-pre-line">
                    {paragraph}
                  </p>
                ))}
            </div>
          </article>
      </Container>
    </main>
  );
}
