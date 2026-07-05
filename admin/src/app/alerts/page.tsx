import { getAlerts } from "@/lib/api";
import SectionHeader from "@/components/SectionHeader";
import AlertsManager from "@/components/admin/AlertsManager";

export const dynamic = "force-dynamic";

/** Раздел «Уведомления»: активные алерты с формами и публикацией (draft → published). */
export default async function AlertsPage() {
  const alerts = await getAlerts();

  return (
    <>
      <SectionHeader section="alerts" />
      <AlertsManager data={alerts.data} error={alerts.error} />
    </>
  );
}
