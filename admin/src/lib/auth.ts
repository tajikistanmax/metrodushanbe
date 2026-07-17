/**
 * Dev-контур аутентификации операционной консоли (сессия на httpOnly-cookie).
 *
 * По ТЗ (§6.1.7, §9) целевая схема — OAuth2/OIDC через Keycloak с MFA и RBAC;
 * она вводится фазой «Security Hardening» плана модернизации. До неё консоль
 * закрыта локальным паролем оператора: значение сверяется ТОЛЬКО на сервере
 * (Server Action), в cookie кладётся НЕ пароль, а HMAC-токен от секрета.
 *
 * Переменные окружения (все — с dev-значениями по умолчанию):
 *  - ADMIN_UI_USER      — логин оператора (по умолчанию "admin");
 *  - ADMIN_UI_PASSWORD  — пароль (по умолчанию "metro2026");
 *  - ADMIN_SESSION_SECRET — секрет подписи cookie (в проде — обязателен).
 *
 * Токен вычисляется через Web Crypto (globalThis.crypto.subtle) — API доступен
 * в Node-runtime Server Actions и Next Proxy, поэтому оба контура считают
 * одинаковый HMAC без импорта node:crypto.
 */

export const SESSION_COOKIE = "metro-admin.session";

/** Полезная нагрузка токена фиксирована: сессия статична (без claims). */
const TOKEN_PAYLOAD = "metro-dushanbe-admin-session-v1";

const DEV_USER = "admin";
const DEV_PASSWORD = "metro2026";
const DEV_SECRET = "dev-session-secret-change-me";

export function expectedUser(): string {
  return process.env.ADMIN_UI_USER ?? DEV_USER;
}

export function expectedPassword(): string {
  return process.env.ADMIN_UI_PASSWORD ?? DEV_PASSWORD;
}

function sessionSecret(): string {
  return process.env.ADMIN_SESSION_SECRET ?? DEV_SECRET;
}

/** HMAC-SHA256(secret, payload) → hex. Работает в серверном Node runtime. */
export async function computeSessionToken(): Promise<string> {
  const enc = new TextEncoder();
  const key = await globalThis.crypto.subtle.importKey(
    "raw",
    enc.encode(sessionSecret()),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await globalThis.crypto.subtle.sign(
    "HMAC",
    key,
    enc.encode(TOKEN_PAYLOAD),
  );
  return Array.from(new Uint8Array(sig))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

/** Сравнение без ранних выходов (константное время по длине токена). */
export function tokensEqual(a: string, b: string): boolean {
  if (a.length !== b.length) {
    return false;
  }
  let diff = 0;
  for (let i = 0; i < a.length; i += 1) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}
