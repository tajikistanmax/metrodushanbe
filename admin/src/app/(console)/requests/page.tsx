import SectionHeader from "@/components/SectionHeader";
import CitizenRequestsManager from "@/components/operations/CitizenRequestsManager";
import { getCitizenRequests } from "@/lib/admin-actions";

export const dynamic = "force-dynamic";

export default async function CitizenRequestsPage() {
  const requests = await getCitizenRequests();
  return (
    <>
      <SectionHeader section="requests" />
      <CitizenRequestsManager data={requests.data} error={requests.error} />
    </>
  );
}
