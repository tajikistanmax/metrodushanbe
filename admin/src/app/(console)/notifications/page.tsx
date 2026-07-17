import SectionHeader from "@/components/SectionHeader";
import NotificationsManager from "@/components/operations/NotificationsManager";
import {
  getNotificationProblemDeliveries,
  getNotificationTemplates,
  getNotifications,
} from "@/lib/admin-actions";
import { hasAdminRole } from "@/lib/server-auth";

export const dynamic = "force-dynamic";

/**
 * Рассылки (NTF-01…06). Читать может любой оператор консоли; вести рассылки и
 * повторять доставки — роль operator, править шаблоны — editor (гейты стоят в
 * самих действиях, здесь роль нужна только чтобы не рисовать кнопку, которую
 * backend отклонит с 403).
 */
export default async function NotificationsPage() {
  const [messages, templates, problems, canOperate, canEditTemplates] =
    await Promise.all([
      getNotifications(),
      getNotificationTemplates(),
      getNotificationProblemDeliveries(),
      hasAdminRole("operator"),
      hasAdminRole("editor"),
    ]);

  return (
    <>
      <SectionHeader section="notifications" />
      <NotificationsManager
        messages={messages.data}
        error={messages.error}
        templates={templates.data}
        templatesError={templates.error}
        problems={problems.data}
        problemsError={problems.error}
        canOperate={canOperate}
        canEditTemplates={canEditTemplates}
      />
    </>
  );
}
