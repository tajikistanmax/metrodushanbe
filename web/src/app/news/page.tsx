import type { Metadata } from "next";
import NewsListClient from "@/components/NewsListClient";

export const metadata: Metadata = {
  title: "Ахбор — Новости — News · Метрои Душанбе",
  description:
    "Новости и публикации портала метро Душанбе: опубликованные материалы на трёх языках.",
};

/**
 * Роут /news — список опубликованных новостей. Вся интерактивность и загрузка
 * данных (с офлайн-деградацией) — в клиентском NewsListClient, как на главной.
 */
export default function NewsPage() {
  return <NewsListClient />;
}
