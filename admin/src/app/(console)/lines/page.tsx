import { getLines, getNetworkGeoJson } from "@/lib/api";
import SectionHeader from "@/components/SectionHeader";
import LinesManager from "@/components/admin/LinesManager";

export const dynamic = "force-dynamic";

/**
 * Раздел «Линии»: таблица линий с CRUD-формами (admin-write).
 * Геометрия сети нужна форме: карточка линии (LineDto) трассу не отдаёт,
 * поэтому существующая трасса для правки берётся из /network/geojson.
 */
export default async function LinesPage() {
  const [lines, network] = await Promise.all([getLines(), getNetworkGeoJson()]);

  return (
    <>
      <SectionHeader section="lines" />
      <LinesManager data={lines.data} error={lines.error} network={network.data} />
    </>
  );
}
