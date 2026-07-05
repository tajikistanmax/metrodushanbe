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
import type {
  ActionResult,
  AlertCreateBody,
  AlertUpdateBody,
  LineCreateBody,
  LineUpdateBody,
  NewsCreateBody,
  NewsUpdateBody,
  StationCreateBody,
  StationUpdateBody,
} from "./admin-forms";
import type { Alert, Line, News, Station } from "./types";

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
): Promise<ActionResult<T>> {
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
