import type { Metadata } from "next";
import { notFound } from "next/navigation";
import NewsArticleClient from "@/components/NewsArticleClient";
import { loadNewsArticleForPage } from "@/lib/news-server";

type NewsArticlePageProps = {
  params: Promise<{ slug: string }>;
};

export async function generateMetadata({
  params,
}: NewsArticlePageProps): Promise<Metadata> {
  const { slug } = await params;
  const article = await loadNewsArticleForPage(slug);
  if (!article) {
    return {
      title: "Новость не найдена",
      robots: { index: false, follow: false },
    };
  }
  const description = article.body.ru.replace(/\s+/g, " ").trim().slice(0, 180);
  return {
    title: `${article.title.ru} — Метро Душанбе`,
    description,
    openGraph: {
      type: "article",
      title: article.title.ru,
      description,
      publishedTime: article.publishedAt,
      images: article.coverMediaUrl ? [article.coverMediaUrl] : undefined,
    },
  };
}

/** A missing published article is a real HTTP 404; backend outages remain route errors. */
export default async function NewsArticlePage({ params }: NewsArticlePageProps) {
  const { slug } = await params;
  const article = await loadNewsArticleForPage(slug);
  if (!article) {
    notFound();
  }
  return <NewsArticleClient article={article} />;
}
