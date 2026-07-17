import SectionHeader from "@/components/SectionHeader";
import WebhooksManager from "@/components/operations/WebhooksManager";
import {
  getWebhookDeliveries,
  getWebhookSubscriptions,
} from "@/lib/admin-actions";
import { hasAdminRole } from "@/lib/server-auth";

export const dynamic = "force-dynamic";

/**
 * Интеграции (ADM-06, U-OPS-04). Права раздела расщеплены намеренно и зеркалят
 * AdminKeyAuthFilter: подписчики и секреты — суперадмин, очередь доставок и
 * ручной повтор — дежурный оператор. Чинить застрявшую доставку должна смена,
 * а не владелец ключей, поэтому раздел не закрыт целиком под superadmin.
 *
 * Очередь грузится без ?status=: backend отдаёт только требующие внимания —
 * failed и dead (DLQ).
 */
export default async function WebhooksPage() {
  const [subscriptions, deliveries, canManage, canRetry] = await Promise.all([
    getWebhookSubscriptions(),
    getWebhookDeliveries(),
    hasAdminRole("superadmin"),
    hasAdminRole("operator"),
  ]);

  return (
    <>
      <SectionHeader section="webhooks" />
      <WebhooksManager
        subscriptions={subscriptions.data}
        subscriptionsError={subscriptions.error}
        deliveries={deliveries.data}
        deliveriesError={deliveries.error}
        canManage={canManage}
        canRetry={canRetry}
      />
    </>
  );
}
