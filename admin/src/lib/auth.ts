/**
 * Сессия операционной консоли: подписанная httpOnly-cookie с claims оператора.
 *
 * По ТЗ (§6.1.7, §9) целевая схема — OAuth2/OIDC через Keycloak с MFA; она вводится
 * фазой «Security Hardening». До неё пароль проверяет backend
 * (`POST /v1/admin/auth/login`, закрытый X-Admin-Key), а Next выпускает сессию.
 *
 * Формат токена: `base64url(payloadJSON).hmacHex`. Payload несёт логин, роль и срок
 * годности — без них консоль не смогла бы различать операторов и применять RBAC.
 * Подпись покрывает payload целиком, поэтому роль нельзя поднять правкой cookie.
 *
 * Переменные окружения:
 *  - ADMIN_SESSION_SECRET — секрет подписи (в проде обязателен).
 *
 * HMAC считается через Web Crypto (globalThis.crypto.subtle) — API есть и в
 * Node-runtime Server Actions, и в Next Proxy, поэтому оба контура проверяют
 * подпись одинаково, без импорта node:crypto.
 */

export const SESSION_COOKIE = "metro-admin.session";

const DEV_SECRET = "dev-session-secret-change-me";

/** Роли консоли, по возрастанию прав. Порядок = уровень доступа. */
export const ADMIN_ROLES = ["viewer", "operator", "editor", "superadmin"] as const;

export type AdminRole = (typeof ADMIN_ROLES)[number];

/** Claims оператора внутри подписанной cookie. */
export type AdminSession = {
  username: string;
  displayName: string;
  role: AdminRole;
  /** Версия учётной записи; меняется при смене роли, пароля или блокировке. */
  accountVersion: number;
  /** Unix-время истечения в секундах. */
  exp: number;
};

export function isAdminRole(value: string): value is AdminRole {
  return (ADMIN_ROLES as readonly string[]).includes(value);
}

/**
 * Истинно, если роль сессии покрывает требуемую. Роли иерархичны: superadmin
 * включает editor, editor — operator и так далее.
 */
export function roleAtLeast(role: AdminRole, required: AdminRole): boolean {
  return ADMIN_ROLES.indexOf(role) >= ADMIN_ROLES.indexOf(required);
}

function sessionSecret(): string {
  return process.env.ADMIN_SESSION_SECRET ?? DEV_SECRET;
}

/** base64url без паддинга; UTF-8-безопасно (btoa сам по себе — только latin1). */
function base64UrlEncode(value: string): string {
  const bytes = new TextEncoder().encode(value);
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function base64UrlDecode(value: string): string {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/");
  const binary = atob(padded + "=".repeat((4 - (padded.length % 4)) % 4));
  const bytes = Uint8Array.from(binary, (ch) => ch.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

/** HMAC-SHA256(secret, payload) → hex. */
async function sign(payload: string): Promise<string> {
  const enc = new TextEncoder();
  const key = await globalThis.crypto.subtle.importKey(
    "raw",
    enc.encode(sessionSecret()),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await globalThis.crypto.subtle.sign("HMAC", key, enc.encode(payload));
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

/** Выпускает подписанный токен сессии со сроком жизни `ttlSeconds`. */
export async function createSessionToken(
  claims: Omit<AdminSession, "exp">,
  ttlSeconds: number,
): Promise<string> {
  const session: AdminSession = {
    ...claims,
    exp: Math.floor(Date.now() / 1000) + ttlSeconds,
  };
  const payload = base64UrlEncode(JSON.stringify(session));
  return `${payload}.${await sign(payload)}`;
}

/**
 * Проверяет подпись и срок годности. Возвращает claims либо null — любая
 * некорректность (формат, подпись, срок, неизвестная роль) трактуется одинаково.
 */
export async function verifySessionToken(token: string): Promise<AdminSession | null> {
  const separator = token.lastIndexOf(".");
  if (separator <= 0) {
    return null;
  }
  const payload = token.slice(0, separator);
  const signature = token.slice(separator + 1);

  if (!tokensEqual(signature, await sign(payload))) {
    return null;
  }

  let session: AdminSession;
  try {
    session = JSON.parse(base64UrlDecode(payload)) as AdminSession;
  } catch {
    return null;
  }

  const validShape =
    typeof session?.username === "string" &&
    typeof session?.displayName === "string" &&
    typeof session?.role === "string" &&
    isAdminRole(session.role) &&
    typeof session?.accountVersion === "number" &&
    typeof session?.exp === "number";
  if (!validShape || session.exp <= Math.floor(Date.now() / 1000)) {
    return null;
  }
  return session;
}
