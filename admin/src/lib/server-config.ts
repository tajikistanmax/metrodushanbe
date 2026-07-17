import "server-only";

const DEV_ADMIN_KEY = "dev-admin-key-change-me";

/** Серверная база backend; внутренний URL не должен попадать в browser bundle. */
export const ADMIN_API_BASE =
  process.env.ADMIN_API_BASE ?? "http://localhost:8080/api/v1";

export const ADMIN_FETCH_TIMEOUT_MS = 10_000;
export const ADMIN_IMPORT_TIMEOUT_MS = 60_000;

/** В production отсутствие master key является ошибкой конфигурации, а не fallback. */
export function adminApiKey(): string {
  const configured = process.env.ADMIN_API_KEY;
  if (configured) {
    if (process.env.NODE_ENV === "production" && configured.length < 32) {
      throw new Error("ADMIN_API_KEY must contain at least 32 characters");
    }
    return configured;
  }
  if (process.env.NODE_ENV === "production") {
    throw new Error("ADMIN_API_KEY is required in production");
  }
  return DEV_ADMIN_KEY;
}
