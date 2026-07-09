import {
  getAiBriefing,
  getAlerts,
  getBackendHealth,
  getLines,
  getNetworkGeoJson,
  getNews,
  getStations,
} from "@/lib/api";
import { getAuditEvents } from "@/lib/admin-actions";
import DashboardClient from "@/components/dashboard/DashboardClient";

// Операционная консоль: рендер по запросу, без статического кэша —
// показатели всегда актуальны, а `next build` не пытается ходить в API.
export const dynamic = "force-dynamic";

/**
 * Операционный дашборд (ТЗ §8.4): все источники тянутся параллельно на
 * сервере; при недоступном backend каждая панель деградирует независимо
 * (схема сети — к бандл-копии демо-данных).
 */
export default async function OverviewPage() {
  const [lines, stations, alerts, news, briefing, audit, network, health] =
    await Promise.all([
      getLines(),
      getStations(),
      getAlerts(),
      getNews(),
      getAiBriefing(),
      getAuditEvents(),
      getNetworkGeoJson(),
      getBackendHealth(),
    ]);

  return (
    <DashboardClient
      lines={lines.data}
      stations={stations.data}
      alerts={alerts.data}
      news={news.data}
      briefing={briefing.data}
      audit={audit.data}
      network={network.data}
      networkSource={network.source}
      health={health}
    />
  );
}
