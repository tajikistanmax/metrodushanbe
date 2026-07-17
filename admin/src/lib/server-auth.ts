import "server-only";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { SESSION_COOKIE, computeSessionToken, tokensEqual } from "./auth";

/** Проверяет HMAC-сессию непосредственно в доверенном серверном контуре. */
export async function hasAdminSession(): Promise<boolean> {
  const store = await cookies();
  const token = store.get(SESSION_COOKIE)?.value ?? "";
  if (!token) {
    return false;
  }
  return tokensEqual(token, await computeSessionToken());
}

/**
 * Обязательный guard для Server Components и Server Actions. Proxy остаётся
 * только ранней оптимистичной проверкой и не является границей безопасности.
 */
export async function requireAdminSession(): Promise<void> {
  if (!(await hasAdminSession())) {
    redirect("/login");
  }
}
