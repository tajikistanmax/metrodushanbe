import NewsArticleClient from "@/components/NewsArticleClient";

/**
 * Роут /news/{slug} — страница статьи. `params` в App Router — Promise и
 * ожидается на сервере (Next 16). Загрузка статьи и состояние «не найдено»
 * (null) — в клиентском NewsArticleClient.
 */
export default async function NewsArticlePage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  // key={slug} — при клиентском переходе между статьями компонент
  // перемонтируется и корректно возвращается в состояние загрузки.
  return <NewsArticleClient key={slug} slug={slug} />;
}
