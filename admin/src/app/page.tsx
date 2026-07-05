import { getAlerts, getLines, getNews, getStations } from "@/lib/api";
import SectionHeader from "@/components/SectionHeader";
import OverviewCards from "@/components/OverviewCards";

// Операционная консоль: рендер по запросу, без статического кэша —
// счётчики всегда актуальны, а `next build` не пытается ходить в API.
export const dynamic = "force-dynamic";

/** Обзор: счётчики линий, станций, активных уведомлений и новостей. */
export default async function OverviewPage() {
  const [lines, stations, alerts, news] = await Promise.all([
    getLines(),
    getStations(),
    getAlerts(),
    getNews(),
  ]);

  return (
    <>
      <SectionHeader section="overview" />
      <OverviewCards
        lines={lines.data?.length ?? null}
        stations={stations.data?.length ?? null}
        alerts={alerts.data?.length ?? null}
        news={news.data?.length ?? null}
      />
    </>
  );
}
