"use client";

/**
 * Публичный список новостей (ТЗ §6.2.7): заголовок, дата и текстовое превью
 * каждой опубликованной статьи со ссылкой на страницу статьи. Загрузка —
 * через loadNews() с офлайн-деградацией (пустой список при недоступном
 * backend, dev-conventions.md §8): состояние «загрузка» отделено от «пусто».
 * Даты форматируются по текущему языку (formatNewsDate).
 *
 * Раздел не работает с данными сети, поэтому источник данных в шапку не
 * публикуется и пилюли источника здесь нет (см. DataSourceProvider).
 */

import Link from "next/link";
import { useEffect, useState } from "react";
import { MAIN_CONTENT_ID } from "@/lib/dom-ids";
import { formatNewsDate, loadNews } from "@/lib/news-data";
import { pickName } from "@/lib/i18n";
import type { NewsArticle } from "@/lib/types";
import { Card, Container, Stack } from "@/shared/ui";
import { useI18n } from "./I18nProvider";

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
    <main id={MAIN_CONTENT_ID} className="flex-1 py-8 sm:py-12">
      <Container width="narrow">
        <h1 className="text-title-l font-bold sm:text-title-xl">
          {dict.news.heading}
        </h1>

        {articles === null ? (
          <p role="status" className="mt-6 text-body text-text-secondary">
            {dict.loading}
          </p>
        ) : articles.length === 0 ? (
          <p className="mt-6 text-body text-text-secondary">{dict.news.empty}</p>
        ) : (
          <Stack as="ul" gap={4} className="mt-8">
            {articles.map((article) => (
              <Card as="li" key={article.slug} padding="lg">
                <h2 className="text-title-s font-bold leading-snug">
                  <Link
                    href={`/news/${article.slug}`}
                    className="text-[var(--text-primary)] hover:text-brand-red"
                  >
                    {pickName(article.title, lang)}
                  </Link>
                </h2>

                <p className="mt-1 text-caption font-semibold uppercase tracking-[0.08em] text-text-secondary">
                  <span className="sr-only">{dict.news.publishedLabel}: </span>
                  <time dateTime={article.publishedAt}>
                    {formatNewsDate(article.publishedAt, lang)}
                  </time>
                </p>

                <p className="mt-3 line-clamp-3 text-small leading-relaxed text-text-secondary">
                  {pickName(article.body, lang)}
                </p>

                <p className="mt-4">
                  <Link
                    href={`/news/${article.slug}`}
                    className="inline-flex items-center gap-1 text-small font-bold text-brand-red hover:underline"
                  >
                    {dict.news.read}
                    <span aria-hidden="true">→</span>
                  </Link>
                </p>
              </Card>
            ))}
          </Stack>
        )}
      </Container>
    </main>
  );
}
