"use server";

/**
 * Серверные действия admin-write контура (Next 16 Server Actions).
 *
 * КЛЮЧ — ТОЛЬКО СЕРВЕРНО. Заголовок X-Admin-Key читается из серверного env
 * `ADMIN_API_KEY` (fallback dev — `dev-admin-key-change-me`) и добавляется здесь,
 * на сервере. Клиентские формы вызывают эти функции как async-функции и НИКОГДА
 * не видят ключ: он не уходит в клиентский бандл (нет NEXT_PUBLIC_ для ключа).
 *
 * Каждое действие возвращает сериализуемый {@link ActionResult}: при ошибке —
 * распакованный envelope backend {error:{code,message,details?}}, чтобы форма
 * показала пользователю message/details. Наружу исключения не бросаем.
 */

import { revalidatePath } from "next/cache";
import { API_BASE } from "./api";
import { requireAdminSession } from "./server-auth";
import type {
  ActionResult,
  AlertCreateBody,
  AlertUpdateBody,
  LineCreateBody,
  LineUpdateBody,
  NewsCreateBody,
  NewsUpdateBody,
  CalendarExceptionBody,
  CitizenRequestUpdateBody,
  FareCreateBody,
  FareUpdateBody,
  StationCreateBody,
  StationUpdateBody,
} from "./admin-forms";
import type {
  AiChatRequest,
  AiChatResponse,
  Alert,
  AuditEvent,
  CalendarException,
  CitizenRequestAdmin,
  FeatureFlag,
  FareProduct,
  ImportError,
  ImportJob,
  ImportPage,
  Line,
  News,
  Station,
} from "./types";

/** Форма результата чтения для таблиц/панелей: данные либо причина ошибки. */
type ReadResult<T> = { data: T | null; error: string | null };

/** Dev-ключ по умолчанию (совпадает с app.admin.dev-key backend). */
const DEFAULT_ADMIN_KEY = "dev-admin-key-change-me";

/** Актор аудита (BR-ADM-1); в проде извлекается из JWT (ТЗ §6.1.7). */
const ADMIN_ACTOR = "admin-console";

type Method = "POST" | "PUT" | "DELETE";

/**
 * Единый серверный вызов admin-эндпоинта: подставляет X-Admin-Key/X-Admin-Actor,
 * распаковывает JSON и приводит ответ к {@link ActionResult}. Тело DELETE —
 * 204 No Content (data = null).
 */
async function adminFetch<T>(
  path: string,
  method: Method,
  body?: unknown,
  extraHeaders?: Record<string, string>,
): Promise<ActionResult<T>> {
  await requireAdminSession();
  const adminKey = process.env.ADMIN_API_KEY ?? DEFAULT_ADMIN_KEY;
  try {
    const res = await fetch(`${API_BASE}/admin${path}`, {
      method,
      cache: "no-store",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        "X-Admin-Key": adminKey,
        "X-Admin-Actor": ADMIN_ACTOR,
        ...extraHeaders,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });

    if (res.status === 204) {
      return { ok: true, data: null as T };
    }

    const text = await res.text();
    const payload = text ? safeJson(text) : null;

    if (!res.ok) {
      const envelope = payload as
        | { error?: { code?: string; message?: string; details?: unknown } }
        | null;
      const err = envelope?.error;
      return {
        ok: false,
        error: {
          code: err?.code ?? `http.${res.status}`,
          message:
            err?.message ?? `HTTP ${res.status} ${res.statusText}`.trim(),
          details: err?.details,
        },
      };
    }

    return { ok: true, data: payload as T };
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e);
    return { ok: false, error: { code: "network.error", message } };
  }
}

/** Защищённое серверное чтение /admin/** с единым ключом и envelope ошибки. */
async function adminRead<T>(path: string): Promise<ReadResult<T>> {
  await requireAdminSession();
  const adminKey = process.env.ADMIN_API_KEY ?? DEFAULT_ADMIN_KEY;
  try {
    const res = await fetch(`${API_BASE}/admin${path}`, {
      cache: "no-store",
      headers: {
        Accept: "application/json",
        "X-Admin-Key": adminKey,
        "X-Admin-Actor": ADMIN_ACTOR,
      },
    });
    if (!res.ok) {
      return { data: null, error: `HTTP ${res.status} ${res.statusText}`.trim() };
    }
    return { data: (await res.json()) as T, error: null };
  } catch (e) {
    return { data: null, error: e instanceof Error ? e.message : String(e) };
  }
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

// --- Линии ------------------------------------------------------------------

export async function createLine(
  body: LineCreateBody,
): Promise<ActionResult<Line>> {
  const r = await adminFetch<Line>("/lines", "POST", body);
  if (r.ok) revalidatePath("/lines");
  return r;
}

export async function updateLine(
  code: string,
  body: LineUpdateBody,
): Promise<ActionResult<Line>> {
  const r = await adminFetch<Line>(`/lines/${encodeURIComponent(code)}`, "PUT", body);
  if (r.ok) revalidatePath("/lines");
  return r;
}

export async function deleteLine(code: string): Promise<ActionResult<null>> {
  const r = await adminFetch<null>(`/lines/${encodeURIComponent(code)}`, "DELETE");
  if (r.ok) revalidatePath("/lines");
  return r;
}

// --- Станции ----------------------------------------------------------------

export async function createStation(
  body: StationCreateBody,
): Promise<ActionResult<Station>> {
  const r = await adminFetch<Station>("/stations", "POST", body);
  if (r.ok) revalidatePath("/stations");
  return r;
}

export async function updateStation(
  code: string,
  body: StationUpdateBody,
): Promise<ActionResult<Station>> {
  const r = await adminFetch<Station>(
    `/stations/${encodeURIComponent(code)}`,
    "PUT",
    body,
  );
  if (r.ok) revalidatePath("/stations");
  return r;
}

export async function deleteStation(code: string): Promise<ActionResult<null>> {
  const r = await adminFetch<null>(
    `/stations/${encodeURIComponent(code)}`,
    "DELETE",
  );
  if (r.ok) revalidatePath("/stations");
  return r;
}

// --- Уведомления ------------------------------------------------------------

export async function createAlert(
  body: AlertCreateBody,
): Promise<ActionResult<Alert>> {
  const r = await adminFetch<Alert>("/alerts", "POST", body);
  if (r.ok) revalidatePath("/alerts");
  return r;
}

export async function updateAlert(
  code: string,
  body: AlertUpdateBody,
): Promise<ActionResult<Alert>> {
  const r = await adminFetch<Alert>(
    `/alerts/${encodeURIComponent(code)}`,
    "PUT",
    body,
  );
  if (r.ok) revalidatePath("/alerts");
  return r;
}

export async function publishAlert(code: string): Promise<ActionResult<Alert>> {
  const r = await adminFetch<Alert>(
    `/alerts/${encodeURIComponent(code)}/publish`,
    "POST",
  );
  if (r.ok) revalidatePath("/alerts");
  return r;
}

// --- Новости ----------------------------------------------------------------

export async function createNews(
  body: NewsCreateBody,
): Promise<ActionResult<News>> {
  const r = await adminFetch<News>("/news", "POST", body);
  if (r.ok) revalidatePath("/news");
  return r;
}

export async function updateNews(
  slug: string,
  body: NewsUpdateBody,
): Promise<ActionResult<News>> {
  const r = await adminFetch<News>(
    `/news/${encodeURIComponent(slug)}`,
    "PUT",
    body,
  );
  if (r.ok) revalidatePath("/news");
  return r;
}

export async function publishNews(slug: string): Promise<ActionResult<News>> {
  const r = await adminFetch<News>(
    `/news/${encodeURIComponent(slug)}/publish`,
    "POST",
  );
  if (r.ok) revalidatePath("/news");
  return r;
}

// --- Импорт сети ------------------------------------------------------------

export async function getImportJobs(): Promise<ReadResult<ImportPage>> {
  return adminRead<ImportPage>("/imports?page=0&size=50");
}

export async function getImportErrors(id: string): Promise<ReadResult<ImportError[]>> {
  return adminRead<ImportError[]>(`/imports/${encodeURIComponent(id)}/errors`);
}

export async function importNetworkGeoJson(
  sourceName: string,
  text: string,
): Promise<ActionResult<ImportJob>> {
  let payload: unknown;
  try {
    payload = JSON.parse(text);
  } catch {
    return {
      ok: false,
      error: { code: "import.invalid_json", message: "Invalid JSON/GeoJSON" },
    };
  }
  if (
    !payload ||
    typeof payload !== "object" ||
    (payload as { type?: unknown }).type !== "FeatureCollection"
  ) {
    return {
      ok: false,
      error: {
        code: "import.invalid_geojson",
        message: "Expected a GeoJSON FeatureCollection",
      },
    };
  }
  const safeSource = sourceName.trim().replace(/[^\x20-\x7e]/g, "_") || "admin-upload.geojson";
  const result = await adminFetch<ImportJob>("/imports", "POST", payload, {
    "X-Import-Source": safeSource,
  });
  if (result.ok) revalidatePath("/imports");
  return result;
}

// --- Календарные исключения -------------------------------------------------

export async function getCalendarExceptions(): Promise<ReadResult<CalendarException[]>> {
  return adminRead<CalendarException[]>("/calendar-exceptions");
}

export async function createCalendarException(
  body: CalendarExceptionBody,
): Promise<ActionResult<CalendarException>> {
  const result = await adminFetch<CalendarException>("/calendar-exceptions", "POST", body);
  if (result.ok) revalidatePath("/calendar");
  return result;
}

export async function updateCalendarException(
  id: number,
  body: CalendarExceptionBody,
): Promise<ActionResult<CalendarException>> {
  const result = await adminFetch<CalendarException>(
    `/calendar-exceptions/${id}`,
    "PUT",
    body,
  );
  if (result.ok) revalidatePath("/calendar");
  return result;
}

export async function deleteCalendarException(id: number): Promise<ActionResult<null>> {
  const result = await adminFetch<null>(`/calendar-exceptions/${id}`, "DELETE");
  if (result.ok) revalidatePath("/calendar");
  return result;
}

// --- Feature flags ----------------------------------------------------------

export async function getFeatureFlags(): Promise<ReadResult<FeatureFlag[]>> {
  return adminRead<FeatureFlag[]>("/feature-flags");
}

export async function setFeatureFlag(
  flagKey: string,
  enabled: boolean,
): Promise<ActionResult<null>> {
  const result = await adminFetch<null>(
    `/feature-flags/${encodeURIComponent(flagKey)}`,
    "PUT",
    { enabled },
  );
  if (result.ok) revalidatePath("/features");
  return result;
}

// --- Обращения граждан ------------------------------------------------------

export async function getCitizenRequests(): Promise<ReadResult<CitizenRequestAdmin[]>> {
  return adminRead<CitizenRequestAdmin[]>("/requests");
}

export async function updateCitizenRequest(
  code: string,
  body: CitizenRequestUpdateBody,
): Promise<ActionResult<CitizenRequestAdmin>> {
  const result = await adminFetch<CitizenRequestAdmin>(
    `/requests/${encodeURIComponent(code)}`,
    "PUT",
    body,
  );
  if (result.ok) revalidatePath("/requests");
  return result;
}

// --- Тарифы -----------------------------------------------------------------

export async function getFareProducts(): Promise<ReadResult<FareProduct[]>> {
  return adminRead<FareProduct[]>("/fares");
}

export async function createFareProduct(
  body: FareCreateBody,
): Promise<ActionResult<FareProduct>> {
  const result = await adminFetch<FareProduct>("/fares", "POST", body);
  if (result.ok) revalidatePath("/fares");
  return result;
}

export async function updateFareProduct(
  code: string,
  body: FareUpdateBody,
): Promise<ActionResult<FareProduct>> {
  const result = await adminFetch<FareProduct>(
    `/fares/${encodeURIComponent(code)}`,
    "PUT",
    body,
  );
  if (result.ok) revalidatePath("/fares");
  return result;
}

export async function deleteFareProduct(code: string): Promise<ActionResult<null>> {
  const result = await adminFetch<null>(`/fares/${encodeURIComponent(code)}`, "DELETE");
  if (result.ok) revalidatePath("/fares");
  return result;
}

// --- Аудит (чтение) ---------------------------------------------------------

/**
 * GET /admin/audit — лента событий аудита. Эндпоинт под /v1/admin/** защищён
 * X-Admin-Key (AdminKeyAuthFilter), поэтому читаем ТОЛЬКО серверно, с ключом.
 * Возвращает форму { data, error } для таблицы (не ActionResult).
 */
export async function getAuditEvents(): Promise<ReadResult<AuditEvent[]>> {
  await requireAdminSession();
  const adminKey = process.env.ADMIN_API_KEY ?? DEFAULT_ADMIN_KEY;
  try {
    const res = await fetch(`${API_BASE}/admin/audit`, {
      cache: "no-store",
      headers: {
        Accept: "application/json",
        "X-Admin-Key": adminKey,
        "X-Admin-Actor": ADMIN_ACTOR,
      },
    });
    if (!res.ok) {
      return { data: null, error: `HTTP ${res.status} ${res.statusText}`.trim() };
    }
    const payload = (await res.json()) as { items?: AuditEvent[] };
    return { data: payload.items ?? [], error: null };
  } catch (e) {
    return { data: null, error: e instanceof Error ? e.message : String(e) };
  }
}

// --- AI-чат (проксируется серверно) -----------------------------------------

/**
 * POST /ai/chat — вопрос агенту. Выполняется СЕРВЕРНО (Server Action): из браузера
 * NEXT_PUBLIC_API_BASE в контейнерном деплое указывает на внутреннее имя backend
 * (http://backend:8080), недостижимое из браузера. Клиентская панель вызывает эту
 * функцию, а fetch к backend идёт с сервера Next.
 */
export async function sendAiChat(
  request: AiChatRequest,
): Promise<ReadResult<AiChatResponse>> {
  await requireAdminSession();
  try {
    const res = await fetch(`${API_BASE}/ai/chat`, {
      method: "POST",
      cache: "no-store",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify(request),
      signal: AbortSignal.timeout(30_000),
    });
    if (!res.ok) {
      return { data: null, error: `HTTP ${res.status} ${res.statusText}`.trim() };
    }
    return { data: (await res.json()) as AiChatResponse, error: null };
  } catch (e) {
    return { data: null, error: e instanceof Error ? e.message : String(e) };
  }
}
