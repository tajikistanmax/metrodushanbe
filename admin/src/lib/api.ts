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

import "server-only";

import type {
  AiBriefing,
  Alert,
  HealthStatus,
  Line,
  NetworkGeoJson,
  News,
  Station,
} from "./types";
import { ADMIN_API_BASE, ADMIN_FETCH_TIMEOUT_MS } from "./server-config";

/** Базовый URL API (совпадает с web/src/lib/api.ts). */
export const API_BASE = ADMIN_API_BASE;

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
      signal: AbortSignal.timeout(ADMIN_FETCH_TIMEOUT_MS),
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

/**
 * GET /network/geojson — геометрия сети для схемы на дашборде.
 * При недоступном backend деградирует к бандл-копии демо-данных
 * (src/lib/demo-network.json) — офлайн-принцип §8. `source` сообщает,
 * какие данные показаны.
 */
export async function getNetworkGeoJson(): Promise<{
  data: NetworkGeoJson;
  source: "api" | "demo";
}> {
  const res = await getJson<NetworkGeoJson>("/network/geojson");
  if (res.data && res.data.type === "FeatureCollection") {
    return { data: res.data, source: "api" };
  }
  const demo = (await import("./demo-network.json")) as unknown as {
    default: NetworkGeoJson;
  };
  return { data: demo.default, source: "demo" };
}

/**
 * GET /actuator/health — живость backend (management-эндпоинт вне /v1).
 * Возвращает статус или "UNREACHABLE", если backend недоступен.
 */
export async function getBackendHealth(): Promise<string> {
  const base = API_BASE.replace(/\/v1\/?$/, "");
  try {
    const res = await fetch(`${base}/actuator/health`, {
      cache: "no-store",
      headers: { Accept: "application/json" },
      signal: AbortSignal.timeout(3_000),
    });
    if (!res.ok) {
      return "DOWN";
    }
    const payload = (await res.json()) as HealthStatus;
    return payload.status ?? "UNKNOWN";
  } catch {
    return "UNREACHABLE";
  }
}

// Примечание: чтение аудита (getAuditEvents, нужен X-Admin-Key) и AI-чат (sendAiChat,
// проксируется серверно) вынесены в "use server"-модуль admin-actions.ts — ключ и
// серверный base URL не должны попадать в клиентский бандл (см. AgentsPanel/AuditPage).
