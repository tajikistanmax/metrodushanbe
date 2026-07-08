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

import type { AiBriefing, Alert, Line, News, Station } from "./types";

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

/** GET /ai/briefing - AI agent readiness (публичный, серверный вызов из agents/page). */
export function getAiBriefing(): Promise<ApiResult<AiBriefing>> {
  return getJson<AiBriefing>("/ai/briefing");
}

// Примечание: чтение аудита (getAuditEvents, нужен X-Admin-Key) и AI-чат (sendAiChat,
// проксируется серверно) вынесены в "use server"-модуль admin-actions.ts — ключ и
// серверный base URL не должны попадать в клиентский бандл (см. AgentsPanel/AuditPage).
