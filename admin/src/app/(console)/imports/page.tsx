import SectionHeader from "@/components/SectionHeader";
import ImportManager from "@/components/operations/ImportManager";
import { getImportJobs } from "@/lib/admin-actions";

export const dynamic = "force-dynamic";

export default async function ImportsPage() {
  const jobs = await getImportJobs();
  return (
    <>
      <SectionHeader section="imports" />
      <ImportManager data={jobs.data} error={jobs.error} />
    </>
  );
}
