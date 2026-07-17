import SectionHeader from "@/components/SectionHeader";
import TicketingManager from "@/components/operations/TicketingManager";
import { getBlocklist, getPayments, getTickets } from "@/lib/admin-actions";
import { hasAdminRole } from "@/lib/server-auth";

export const dynamic = "force-dynamic";

/**
 * Билеты, платежи и чёрный список (TKT-01…06). Возврат и блокировка — работа
 * кассы и дежурного, поэтому роль operator (гейт — в самих действиях).
 */
export default async function TicketsPage() {
  const [tickets, payments, blocklist, canOperate] = await Promise.all([
    getTickets(),
    getPayments(),
    getBlocklist(),
    hasAdminRole("operator"),
  ]);

  return (
    <>
      <SectionHeader section="tickets" />
      <TicketingManager
        tickets={tickets.data}
        ticketsError={tickets.error}
        payments={payments.data}
        paymentsError={payments.error}
        blocklist={blocklist.data}
        blocklistError={blocklist.error}
        canOperate={canOperate}
      />
    </>
  );
}
