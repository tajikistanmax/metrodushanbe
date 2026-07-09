import { getLines } from "@/lib/api";
import SectionHeader from "@/components/SectionHeader";
import LinesManager from "@/components/admin/LinesManager";

export const dynamic = "force-dynamic";

/** Раздел «Линии»: таблица линий с CRUD-формами (admin-write). */
export default async function LinesPage() {
  const lines = await getLines();

  return (
    <>
      <SectionHeader section="lines" />
      <LinesManager data={lines.data} error={lines.error} />
    </>
  );
}
