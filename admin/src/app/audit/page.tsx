import { getAuditEvents } from "@/lib/admin-actions";
import SectionHeader from "@/components/SectionHeader";
import AuditTable from "@/components/AuditTable";

export const dynamic = "force-dynamic";

export default async function AuditPage() {
  const events = await getAuditEvents();
  return (
    <>
      <SectionHeader section="audit" />
      <AuditTable data={events.data} error={events.error} />
    </>
  );
}
