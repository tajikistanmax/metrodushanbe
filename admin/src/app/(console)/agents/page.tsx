import AgentsPanel from "@/components/AgentsPanel";
import PageHeader from "@/components/PageHeader";
import { getAiBriefing } from "@/lib/api";

export const dynamic = "force-dynamic";

export default async function AgentsPage() {
  const briefing = await getAiBriefing();

  return (
    <>
      <PageHeader
        title="AI agents"
        lead="Operational model registry and readiness briefing for the metro platform."
      />
      <AgentsPanel data={briefing.data} error={briefing.error} />
    </>
  );
}
