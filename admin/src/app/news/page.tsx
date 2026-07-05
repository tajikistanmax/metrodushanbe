import { getNews } from "@/lib/api";
import SectionHeader from "@/components/SectionHeader";
import NewsTable from "@/components/NewsTable";

export const dynamic = "force-dynamic";

/** Раздел «Новости»: read-only список новостей. */
export default async function NewsPage() {
  const news = await getNews();

  return (
    <>
      <SectionHeader section="news" />
      <NewsTable data={news.data} error={news.error} />
    </>
  );
}
