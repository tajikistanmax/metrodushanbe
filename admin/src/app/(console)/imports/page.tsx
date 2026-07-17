import SectionHeader from "@/components/SectionHeader";
import ImportManager from "@/components/operations/ImportManager";
import { getImportJobs } from "@/lib/admin-actions";
import { hasAdminRole } from "@/lib/server-auth";

export const dynamic = "force-dynamic";

/**
 * Импорт сети (GeoJSON/GTFS/CSV, INT-04). Историю заданий видит любой оператор;
 * запускать импорт вправе только суперадмин — он переписывает справочники сети
 * целиком (зеркало AdminKeyAuthFilter, префикс /v1/admin/imports).
 */
export default async function ImportsPage() {
  const [jobs, canImport] = await Promise.all([
    getImportJobs(),
    hasAdminRole("superadmin"),
  ]);

  return (
    <>
      <SectionHeader section="imports" />
      <ImportManager data={jobs.data} error={jobs.error} canImport={canImport} />
    </>
  );
}
