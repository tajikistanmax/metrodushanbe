import AgentsPanel from "@/components/AgentsPanel";
import SectionHeader from "@/components/SectionHeader";
import { getAiBriefing } from "@/lib/api";

export const dynamic = "force-dynamic";

export default async function AgentsPage() {
  const briefing = await getAiBriefing();

  return (
    <>
      <SectionHeader section="agents" />
      <AgentsPanel data={briefing.data} error={briefing.error} />
    </>
  );
}
