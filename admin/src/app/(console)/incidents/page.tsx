import SectionHeader from "@/components/SectionHeader";
import IncidentsManager from "@/components/operations/IncidentsManager";
import { getIncidentStats, getIncidents } from "@/lib/admin-actions";
import { hasAdminRole } from "@/lib/server-auth";

export const dynamic = "force-dynamic";

/**
 * Очередь инцидентов. Читать может любой оператор консоли; заводить и
 * двигать по workflow — роль operator и выше (гейт стоит в самих действиях).
 */
export default async function IncidentsPage() {
  const [incidents, stats, canWrite] = await Promise.all([
    getIncidents(),
    getIncidentStats(),
    hasAdminRole("operator"),
  ]);

  return (
    <>
      <SectionHeader section="incidents" />
      <IncidentsManager
        data={incidents.data}
        error={incidents.error}
        stats={stats.data}
        canWrite={canWrite}
      />
    </>
  );
}
