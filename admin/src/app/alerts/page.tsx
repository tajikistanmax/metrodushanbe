import { getAlerts } from "@/lib/api";
import SectionHeader from "@/components/SectionHeader";
import AlertsTable from "@/components/AlertsTable";

export const dynamic = "force-dynamic";

/** Раздел «Уведомления»: read-only таблица активных сервисных алертов. */
export default async function AlertsPage() {
  const alerts = await getAlerts();

  return (
    <>
      <SectionHeader section="alerts" />
      <AlertsTable data={alerts.data} error={alerts.error} />
    </>
  );
}
