import { getNews } from "@/lib/api";
import SectionHeader from "@/components/SectionHeader";
import NewsManager from "@/components/admin/NewsManager";

export const dynamic = "force-dynamic";

/** Раздел «Новости»: список новостей с формами и публикацией (draft → published). */
export default async function NewsPage() {
  const news = await getNews();

  return (
    <>
      <SectionHeader section="news" />
      <NewsManager data={news.data} error={news.error} />
    </>
  );
}
