import { getLines } from "@/lib/api";
import SectionHeader from "@/components/SectionHeader";
import LinesTable from "@/components/LinesTable";

export const dynamic = "force-dynamic";

/** Раздел «Линии»: read-only таблица линий сети. */
export default async function LinesPage() {
  const lines = await getLines();

  return (
    <>
      <SectionHeader section="lines" />
      <LinesTable data={lines.data} error={lines.error} />
    </>
  );
}
