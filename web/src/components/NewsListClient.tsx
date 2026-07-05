"use client";

/**
 * Публичный список новостей (ТЗ §6.2.7): заголовок, дата и текстовое превью
 * каждой опубликованной статьи со ссылкой на страницу статьи. Загрузка —
 * через loadNews() с офлайн-деградацией (пустой список при недоступном
 * backend, dev-conventions.md §8): состояние «загрузка» отделено от «пусто».
 * Даты форматируются по текущему языку (formatNewsDate).
 */

import Link from "next/link";
import { useEffect, useState } from "react";
import { formatNewsDate, loadNews } from "@/lib/news-data";
import { pickName } from "@/lib/i18n";
import type { NewsArticle } from "@/lib/types";
import Header from "./Header";
import { useI18n } from "./I18nProvider";

/** id основного контейнера — цель skip-link (A11Y). */
const NEWS_MAIN_ID = "news-content";

export default function NewsListClient() {
  const { lang, dict } = useI18n();
  // null — ещё грузится; [] — загружено и пусто (loadNews не отклоняется)
  const [articles, setArticles] = useState<NewsArticle[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    loadNews().then((res) => {
      if (!cancelled) {
        setArticles(res);
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

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
        <h1 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
          {dict.news.heading}
        </h1>

        {articles === null ? (
          <p role="status" className="mt-6 text-text-secondary">
            {dict.loading}
          </p>
        ) : articles.length === 0 ? (
          <p className="mt-6 text-text-secondary">{dict.news.empty}</p>
        ) : (
          <ul className="mt-6 flex flex-col gap-4">
            {articles.map((article) => (
              <li key={article.slug}>
                <article className="rounded-2xl border border-[var(--panel-border)] bg-[var(--panel-bg)] p-5 shadow-[var(--shadow-card)] transition-colors">
                  <h2 className="text-lg font-bold leading-snug sm:text-xl">
                    <Link
                      href={`/news/${article.slug}`}
                      className="text-[var(--text-primary)] hover:text-brand-red"
                    >
                      {pickName(article.title, lang)}
                    </Link>
                  </h2>

                  <p className="mt-1 text-xs font-semibold uppercase tracking-wide text-text-secondary">
                    <span className="sr-only">
                      {dict.news.publishedLabel}:{" "}
                    </span>
                    <time dateTime={article.publishedAt}>
                      {formatNewsDate(article.publishedAt, lang)}
                    </time>
                  </p>

                  <p className="mt-3 line-clamp-3 text-sm leading-relaxed text-text-secondary">
                    {pickName(article.body, lang)}
                  </p>

                  <p className="mt-4">
                    <Link
                      href={`/news/${article.slug}`}
                      className="inline-flex items-center gap-1 text-sm font-bold text-brand-red hover:underline"
                    >
                      {dict.news.read}
                      <span aria-hidden="true">→</span>
                    </Link>
                  </p>
                </article>
              </li>
            ))}
          </ul>
        )}
      </main>
    </div>
  );
}
