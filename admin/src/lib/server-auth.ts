import "server-only";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import {
  SESSION_COOKIE,
  roleAtLeast,
  verifySessionToken,
  type AdminRole,
  type AdminSession,
} from "./auth";
import { API_BASE } from "./api";

const DEFAULT_ADMIN_KEY = "dev-admin-key-change-me";

type CurrentAdmin = {
  username?: string;
  displayName?: string;
  role?: string;
  active?: boolean;
  sessionVersion?: number;
};

async function isCurrentSession(session: AdminSession): Promise<boolean> {
  try {
    const response = await fetch(
      `${API_BASE}/admin/auth/session/${encodeURIComponent(session.username)}`,
      {
        cache: "no-store",
        headers: {
          Accept: "application/json",
          "X-Admin-Key": process.env.ADMIN_API_KEY ?? DEFAULT_ADMIN_KEY,
        },
        signal: AbortSignal.timeout(3_000),
      },
    );
    if (!response.ok) return false;
    const current = (await response.json()) as CurrentAdmin;
    return (
      current.active === true &&
      current.username === session.username &&
      current.role === session.role &&
      current.sessionVersion === session.accountVersion
    );
  } catch {
    return false;
  }
}

/** Claims текущего оператора либо null, если сессии нет или она невалидна. */
export async function getAdminSession(): Promise<AdminSession | null> {
  const store = await cookies();
  const token = store.get(SESSION_COOKIE)?.value ?? "";
  if (!token) {
    return null;
  }
  const session = await verifySessionToken(token);
  if (!session || !(await isCurrentSession(session))) {
    return null;
  }
  return session;
}

/**
 * Обязательный guard для Server Components и Server Actions. Proxy остаётся
 * только ранней оптимистичной проверкой и не является границей безопасности.
 */
export async function requireAdminSession(): Promise<AdminSession> {
  const session = await getAdminSession();
  if (!session) {
    redirect("/login");
  }
  return session;
}

/**
 * Guard раздела, требующего роли не ниже `required`.
 *
 * Это и есть точка применения RBAC: backend доверяет любому вызову с корректным
 * X-Admin-Key, поэтому роль обязана проверяться здесь, до обращения к API.
 * Недостаток прав — не редирект на /login (сессия-то валидна), а 403.
 */
export async function requireAdminRole(required: AdminRole): Promise<AdminSession> {
  const session = await requireAdminSession();
  if (!roleAtLeast(session.role, required)) {
    redirect("/?denied=" + encodeURIComponent(required));
  }
  return session;
}

/** Проверка роли без редиректа — для условного показа элементов интерфейса. */
export async function hasAdminRole(required: AdminRole): Promise<boolean> {
  const session = await getAdminSession();
  return session !== null && roleAtLeast(session.role, required);
}
