import "server-only";

/**
 * Короткоживущий подписанный токен актора (аудит-пункт 5).
 *
 * Backend (`AdminKeyAuthFilter` + `AdminActorTokenService`) больше не доверяет
 * голому заголовку `X-Admin-Actor`: обладатель общего `X-Admin-Key` мог назваться
 * любым оператором, и в аудит попадало бы чужое имя. Консоль вместо имени присылает
 * этот токен — backend проверяет подпись и сверяет `sessionVersion` с учётной
 * записью, поэтому подделать имя без секрета нельзя, а отозванная сессия перестаёт
 * работать сразу.
 *
 * Формат (обязан совпадать с `AdminActorTokenService`):
 *   token   = base64url(payload) "." hex(HMAC-SHA256(secret, base64url(payload)))
 *   payload = username "|" sessionVersion "|" issuedAtEpochSeconds "|" nonce
 *
 * ЧЕГО ЭТО НЕ ДАЁТ: секрет подписи — общий симметричный секрет консоли и backend,
 * это НЕ OIDC. Кто получил секрет (компрометация хоста, утечка env), выпускает токен
 * от имени кого угодно. Переход на внешнего эмитента — отдельный открытый P0.
 *
 * Секрет читается из серверного env `ADMIN_ACTOR_TOKEN_SECRET` и в браузер не
 * попадает. HMAC считается через Web Crypto — тем же способом, что подпись сессии
 * (lib/auth.ts), без импорта node:crypto.
 */

const DEV_ACTOR_SECRET = "dev-actor-token-secret-change-me";

function actorSecret(): string {
  const configured = process.env.ADMIN_ACTOR_TOKEN_SECRET;
  if (configured) {
    if (process.env.NODE_ENV === "production" && configured.length < 32) {
      throw new Error("ADMIN_ACTOR_TOKEN_SECRET must contain at least 32 characters");
    }
    return configured;
  }
  if (process.env.NODE_ENV === "production") {
    throw new Error("ADMIN_ACTOR_TOKEN_SECRET is required in production");
  }
  return DEV_ACTOR_SECRET;
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

/** HMAC-SHA256(secret, message) → hex — как AdminActorTokenService на backend. */
async function signHex(message: string): Promise<string> {
  const enc = new TextEncoder();
  const key = await globalThis.crypto.subtle.importKey(
    "raw",
    enc.encode(actorSecret()),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await globalThis.crypto.subtle.sign("HMAC", key, enc.encode(message));
  return Array.from(new Uint8Array(sig))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

/**
 * Выпускает токен актора для операции текущего оператора. `issuedAt` — момент
 * выпуска; backend отвергает токен старше своего TTL (по умолчанию 2 минуты),
 * поэтому токен создаётся заново на каждый запрос, а не кэшируется.
 */
export async function createActorToken(
  username: string,
  sessionVersion: number,
): Promise<string> {
  const issuedAt = Math.floor(Date.now() / 1000);
  const nonce = globalThis.crypto.randomUUID().replace(/-/g, "");
  const payload = `${username}|${sessionVersion}|${issuedAt}|${nonce}`;
  const encoded = base64UrlEncode(payload);
  return `${encoded}.${await signHex(encoded)}`;
}
