/**
 * Клиент к публичному backend API (dev-conventions.md, §3).
 *
 * Запросы выполняются НА СЕРВЕРЕ (в серверных компонентах страниц), а не в
 * браузере: dev-CORS backend разрешает только origin публичного портала
 * (http://localhost:3000), а admin работает на :3001. Серверный fetch из
 * Node не подпадает под CORS, поэтому данные тянем в Server Components и
 * передаём готовые DTO в клиентские таблицы для локализации.
 *
 * Ошибки не бросаются наружу — каждый вызов возвращает конверт
 * { data | error }, чтобы `next build` и рендер страницы не падали, если
 * backend недоступен (офлайн-принцип, §8).
 */

import type { Alert, Line, News, Station } from "./types";

/** Базовый URL API (совпадает с web/src/lib/api.ts). */
export const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080/api/v1";

/** Результат запроса: либо данные, либо человекочитаемая причина ошибки. */
export type ApiResult<T> =
  | { data: T; error: null }
  | { data: null; error: string };

async function getJson<T>(path: string): Promise<ApiResult<T>> {
  try {
    const res = await fetch(`${API_BASE}${path}`, {
      // Операционная консоль: всегда свежие данные, без кэша Next.
      cache: "no-store",
      headers: { Accept: "application/json" },
    });
    if (!res.ok) {
      return { data: null, error: `HTTP ${res.status} ${res.statusText}` };
    }
    const data = (await res.json()) as T;
    return { data, error: null };
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    return { data: null, error: message };
  }
}

/** GET /lines — список линий (фильтр ?status= поддерживается backend). */
export function getLines(): Promise<ApiResult<Line[]>> {
  return getJson<Line[]>("/lines");
}

/** GET /stations — список станций (фильтры ?lineCode=, ?status=). */
export function getStations(): Promise<ApiResult<Station[]>> {
  return getJson<Station[]>("/stations");
}

/** GET /alerts — активные сервисные уведомления. */
export function getAlerts(): Promise<ApiResult<Alert[]>> {
  return getJson<Alert[]>("/alerts");
}

/** GET /news — список новостей. */
export function getNews(): Promise<ApiResult<News[]>> {
  return getJson<News[]>("/news");
}

// TODO (следующая итерация, CRUD): admin-write эндпоинты появятся на backend
// (ТЗ §6.2.10). Тогда сюда добавятся createLine/updateLine/deleteLine и т.д.
// с аутентификацией через Keycloak (dev-conventions.md, §2). Пока API read-only.
