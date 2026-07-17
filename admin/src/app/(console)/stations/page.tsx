import { getLines, getNetworkGeoJson, getStations } from "@/lib/api";
import SectionHeader from "@/components/SectionHeader";
import StationsManager from "@/components/admin/StationsManager";

export const dynamic = "force-dynamic";

/**
 * Раздел «Станции»: таблица станций с CRUD-формами (+ цвета линий для бейджей).
 * Геометрия сети — подложка карты в форме (клик ставит точку станции).
 */
export default async function StationsPage() {
  const [stations, lines, network] = await Promise.all([
    getStations(),
    getLines(),
    getNetworkGeoJson(),
  ]);

  const lineColors: Record<string, string> = {};
  for (const line of lines.data ?? []) {
    lineColors[line.code] = line.colorHex;
  }

  return (
    <>
      <SectionHeader section="stations" />
      <StationsManager
        data={stations.data}
        error={stations.error}
        lineColors={lineColors}
        network={network.data}
      />
    </>
  );
}
