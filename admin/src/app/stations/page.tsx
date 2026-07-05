import { getLines, getStations } from "@/lib/api";
import SectionHeader from "@/components/SectionHeader";
import StationsTable from "@/components/StationsTable";

export const dynamic = "force-dynamic";

/** Раздел «Станции»: read-only таблица станций (+ цвета линий для бейджей). */
export default async function StationsPage() {
  const [stations, lines] = await Promise.all([getStations(), getLines()]);

  const lineColors: Record<string, string> = {};
  for (const line of lines.data ?? []) {
    lineColors[line.code] = line.colorHex;
  }

  return (
    <>
      <SectionHeader section="stations" />
      <StationsTable
        data={stations.data}
        error={stations.error}
        lineColors={lineColors}
      />
    </>
  );
}
