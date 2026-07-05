"use client";

/**
 * Страница новостной статьи (ТЗ §6.2.7): заголовок, дата, обложка (если есть)
 * и тело. Загрузка — loadNewsArticle(slug) с офлайн-деградацией: при любой
 * ошибке или 404 news.not_found возвращается null и показывается состояние
 * «статья не найдена» (dev-conventions.md §8). Тело хранится одной строкой на
 * язык — разбивается на абзацы по пустым строкам, переносы внутри сохраняются.
 */

import Link from "next/link";
import { useEffect, useState } from "react";
import { formatNewsDate, loadNewsArticle } from "@/lib/news-data";
import { pickName } from "@/lib/i18n";
import type { NewsArticle } from "@/lib/types";
import Header from "./Header";
import { useI18n } from "./I18nProvider";

/** id основного контейнера — цель skip-link (A11Y). */
const NEWS_MAIN_ID = "news-content";

type LoadState =
  | { status: "loading" }
  | { status: "ready"; article: NewsArticle }
  | { status: "notfound" };

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
      className="mt-5 w-full rounded-2xl border border-[var(--panel-border)] object-cover"
    />
  );
}

export default function NewsArticleClient({ slug }: { slug: string }) {
  const { lang, dict } = useI18n();
  const [state, setState] = useState<LoadState>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;
    loadNewsArticle(slug).then((article) => {
      if (cancelled) {
        return;
      }
      setState(article ? { status: "ready", article } : { status: "notfound" });
    });
    return () => {
      cancelled = true;
    };
  }, [slug]);

  return (
    <div className="flex min-h-dvh w-full flex-col">
      {/* Skip-link — первый фокусируемый элемент страницы (A11Y) */}
      <a href={`#${NEWS_MAIN_ID}`} className="skip-link">
        {dict.news.skipToContent}
      </a>

      <Header />

      <main
        id={NEWS_MAIN_ID}
        className="mx-auto w-full max-w-3xl flex-1 px-4 py-8 sm:px-6"
      >
        <p className="mb-6">
          <Link
            href="/news"
            className="inline-flex items-center gap-1 text-sm font-bold text-brand-red hover:underline"
          >
            <span aria-hidden="true">←</span>
            {dict.news.backToList}
          </Link>
        </p>

        {state.status === "loading" && (
          <p role="status" className="text-text-secondary">
            {dict.loading}
          </p>
        )}

        {state.status === "notfound" && (
          <div role="alert">
            <h1 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
              {dict.news.notFoundTitle}
            </h1>
            <p className="mt-3 text-text-secondary">{dict.news.notFoundBody}</p>
          </div>
        )}

        {state.status === "ready" && (
          <article>
            <h1 className="text-2xl font-extrabold leading-tight tracking-tight sm:text-3xl">
              {pickName(state.article.title, lang)}
            </h1>

            <p className="mt-2 text-xs font-semibold uppercase tracking-wide text-text-secondary">
              <span className="sr-only">{dict.news.publishedLabel}: </span>
              <time dateTime={state.article.publishedAt}>
                {formatNewsDate(state.article.publishedAt, lang)}
              </time>
            </p>

            {state.article.coverMediaUrl && (
              <CoverImage
                src={state.article.coverMediaUrl}
                alt={pickName(state.article.title, lang)}
              />
            )}

            <div className="mt-6 flex flex-col gap-4 text-[15px] leading-relaxed text-[var(--text-primary)]">
              {pickName(state.article.body, lang)
                .split(/\n{2,}/)
                .map((paragraph, index) => (
                  <p key={index} className="whitespace-pre-line">
                    {paragraph}
                  </p>
                ))}
            </div>
          </article>
        )}
      </main>
    </div>
  );
}
