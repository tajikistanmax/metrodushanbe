/**
 * Лента in-app уведомлений (NTF-01): GET {NEXT_PUBLIC_API_BASE}/notifications
 * с офлайн-деградацией (dev-conventions.md §8) — при ЛЮБОЙ ошибке пустой
 * список, как в alerts-data.ts: портал обязан жить без backend.
 *
 * Семантика фильтров совпадает с /alerts (dev-conventions.md §3): рассылки без
 * таргетов адресованы всей сети и попадают в выдачу всегда; lineCode и
 * stationCode вместе — объединение, а не пересечение.
 */

import { fetchApiJson } from "./api";
import type {
  I18nName,
  NotificationMessage,
  NotificationTarget,
  NotificationType,
} from "./types";

/** Типы рассылок по контракту API. */
const VALID_TYPES: readonly NotificationType[] = [
  "info",
  "warning",
  "incident",
  "maintenance",
  "promo",
];

/** Виды таргетов по контракту API. */
const VALID_TARGET_TYPES: readonly NotificationTarget["type"][] = [
  "line",
  "station",
  "segment",
  "role",
];

function isI18nName(value: unknown): value is I18nName {
  const candidate = value as I18nName | null;
  return (
    typeof candidate === "object" &&
    candidate !== null &&
    typeof candidate.tg === "string" &&
    typeof candidate.ru === "string" &&
    typeof candidate.en === "string"
  );
}

function isTarget(value: unknown): value is NotificationTarget {
  const candidate = value as NotificationTarget | null;
  return (
    typeof candidate === "object" &&
    candidate !== null &&
    (VALID_TARGET_TYPES as readonly string[]).includes(candidate.type) &&
    typeof candidate.code === "string"
  );
}

/**
 * Разбор элемента ответа: рендер не должен падать на данных вне контракта.
 * Невалидные code/type/title/body отбрасывают рассылку целиком; невалидные
 * ЭЛЕМЕНТЫ targets отбрасываются поштучно — тем же приёмом, что в alerts-data.
 */
function parseNotification(value: unknown): NotificationMessage | null {
  const candidate = value as NotificationMessage | null;
  if (
    !candidate ||
    typeof candidate !== "object" ||
    typeof candidate.code !== "string" ||
    !(VALID_TYPES as readonly string[]).includes(candidate.type) ||
    !isI18nName(candidate.title) ||
    !isI18nName(candidate.body) ||
    !Array.isArray(candidate.targets)
  ) {
    return null;
  }
  return {
    code: candidate.code,
    templateCode: candidate.templateCode ?? null,
    alertCode: candidate.alertCode ?? null,
    type: candidate.type,
    title: candidate.title,
    body: candidate.body,
    channels: Array.isArray(candidate.channels) ? candidate.channels : [],
    status: typeof candidate.status === "string" ? candidate.status : "sent",
    targets: candidate.targets.filter(isTarget),
    scheduledAt: typeof candidate.scheduledAt === "string" ? candidate.scheduledAt : null,
    sentAt: typeof candidate.sentAt === "string" ? candidate.sentAt : null,
    updatedAt: typeof candidate.updatedAt === "string" ? candidate.updatedAt : "",
  };
}

/**
 * Отправленные рассылки в порядке API (свежие сверху), либо пустой массив при
 * недоступном backend. Фильтры необязательны: без них — вся лента.
 */
export async function loadNotifications(filters?: {
  lineCode?: string;
  stationCode?: string;
}): Promise<NotificationMessage[]> {
  const params = new URLSearchParams();
  if (filters?.lineCode) params.set("lineCode", filters.lineCode);
  if (filters?.stationCode) params.set("stationCode", filters.stationCode);
  const query = params.toString();
  try {
    const payload = await fetchApiJson(
      `/notifications${query === "" ? "" : `?${query}`}`,
    );
    if (!Array.isArray(payload)) {
      throw new Error("Ответ /notifications не является массивом");
    }
    return payload
      .map(parseNotification)
      .filter((item): item is NotificationMessage => item !== null);
  } catch {
    // Офлайн-принцип: любая ошибка — просто пустая лента
    return [];
  }
}
