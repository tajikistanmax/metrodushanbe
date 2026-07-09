import {
  getAlerts,
  getLines,
  getNetworkGeoJson,
  getNews,
  getStations,
} from "@/lib/api";
import AnalyticsClient from "@/components/analytics/AnalyticsClient";

export const dynamic = "force-dynamic";

/**
 * Аналитика сети: показатели считаются из живых данных API; при недоступном
 * backend геометрия деградирует к демо-копии (см. getNetworkGeoJson).
 */
export default async function AnalyticsPage() {
  const [lines, stations, alerts, news, network] = await Promise.all([
    getLines(),
    getStations(),
    getAlerts(),
    getNews(),
    getNetworkGeoJson(),
  ]);

  return (
    <AnalyticsClient
      lines={lines.data}
      stations={stations.data}
      alerts={alerts.data}
      news={news.data}
      network={network.data}
    />
  );
}
