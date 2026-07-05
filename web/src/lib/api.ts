/**
 * Общий доступ к backend API (dev-conventions.md, §8): базовый URL, таймаут
 * и GET JSON с таймаутом на AbortController. Используется загрузчиками
 * network-data.ts и alerts-data.ts; офлайн-деградацию (fallback на демо-данные
 * или пустой список) каждый загрузчик решает сам.
 */

/** База API бэкенда. */
export const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080/api/v1";

/** Таймаут запроса к API, мс. */
export const API_TIMEOUT_MS = 2000;

/**
 * GET `{API_BASE}{path}` с таймаутом API_TIMEOUT_MS (AbortController).
 * Таймаут покрывает и чтение тела; бросает при не-2xx, таймауте, битом JSON.
 */
export async function fetchApiJson(
  path: string,
  accept = "application/json",
): Promise<unknown> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), API_TIMEOUT_MS);
  try {
    const response = await fetch(`${API_BASE}${path}`, {
      signal: controller.signal,
      headers: { Accept: accept },
      cache: "no-store",
    });
    if (!response.ok) {
      throw new Error(`API вернул HTTP ${response.status}`);
    }
    return (await response.json()) as unknown;
  } finally {
    clearTimeout(timer);
  }
}
