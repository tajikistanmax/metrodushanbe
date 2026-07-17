import SectionHeader from "@/components/SectionHeader";
import CalendarManager from "@/components/operations/CalendarManager";
import { getCalendarExceptions } from "@/lib/admin-actions";

export const dynamic = "force-dynamic";

export default async function CalendarPage() {
  const exceptions = await getCalendarExceptions();
  return (
    <>
      <SectionHeader section="calendar" />
      <CalendarManager data={exceptions.data} error={exceptions.error} />
    </>
  );
}
