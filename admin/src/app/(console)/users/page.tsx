import SectionHeader from "@/components/SectionHeader";
import UsersManager from "@/components/operations/UsersManager";
import { getAdminUsers } from "@/lib/admin-actions";
import { requireAdminRole } from "@/lib/server-auth";

export const dynamic = "force-dynamic";

/**
 * Управление операторами. Guard стоит и здесь, и в самих действиях
 * (getAdminUsers и др.): страница закрывает переход по URL, действия —
 * прямой вызов Server Action в обход навигации.
 */
export default async function UsersPage() {
  const session = await requireAdminRole("superadmin");
  const users = await getAdminUsers();
  return (
    <>
      <SectionHeader section="users" />
      <UsersManager
        data={users.data}
        error={users.error}
        currentUsername={session.username}
      />
    </>
  );
}
