/**
 * Загрузка активных сервисных уведомлений (ТЗ §6.2.6) с офлайн-деградацией
 * (dev-conventions.md, §8): GET {NEXT_PUBLIC_API_BASE}/alerts с таймаутом
 * API_TIMEOUT_MS (общий хелпер fetchApiJson из lib/api.ts); при ЛЮБОЙ ошибке
 * (нет сети, не 2xx, таймаут, битый JSON) — пустой список: портал обязан
 * жить без backend.
 */

import { fetchApiJson } from "./api";
import type {
  AlertSeverity,
  AlertTarget,
  I18nName,
  ServiceAlert,
} from "./types";

/** Допустимые уровни важности по контракту API. */
const VALID_SEVERITIES: readonly AlertSeverity[] = [
  "info",
  "warning",
  "critical",
];

/** Мультиязычное название: все три языка обязательны и являются строками. */
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

/** Цель уведомления: { type: "line" | "station", code: string }. */
function isAlertTarget(value: unknown): value is AlertTarget {
  const candidate = value as AlertTarget | null;
  return (
    typeof candidate === "object" &&
    candidate !== null &&
    (candidate.type === "line" || candidate.type === "station") &&
    typeof candidate.code === "string"
  );
}

/**
 * Проверка элемента ответа по контракту ServiceAlert: рендер не должен
 * падать на данных, которые контракт не описывает. Невалидные code /
 * severity / title / body / startsAt / endsAt / targets-не-массив —
 * уведомление отбрасывается целиком (null); невалидные ЭЛЕМЕНТЫ targets
 * отбрасываются поштучно, само уведомление остаётся.
 */
function parseServiceAlert(value: unknown): ServiceAlert | null {
  const candidate = value as ServiceAlert | null;
  if (
    !candidate ||
    typeof candidate !== "object" ||
    typeof candidate.code !== "string" ||
    !(VALID_SEVERITIES as readonly string[]).includes(candidate.severity) ||
    !isI18nName(candidate.title) ||
    !isI18nName(candidate.body) ||
    typeof candidate.startsAt !== "string" ||
    (candidate.endsAt !== null && typeof candidate.endsAt !== "string") ||
    !Array.isArray(candidate.targets)
  ) {
    return null;
  }
  return {
    code: candidate.code,
    severity: candidate.severity,
    title: candidate.title,
    body: candidate.body,
    startsAt: candidate.startsAt,
    endsAt: candidate.endsAt,
    targets: candidate.targets.filter(isAlertTarget),
  };
}

/**
 * Возвращает активные уведомления в порядке, который отдаёт API
 * (critical → warning → info, внутри — starts_at по убыванию),
 * либо пустой массив при недоступном backend.
 */
export async function loadActiveAlerts(): Promise<ServiceAlert[]> {
  try {
    const payload = await fetchApiJson("/alerts");
    if (!Array.isArray(payload)) {
      throw new Error("Ответ /alerts не является массивом");
    }
    return payload
      .map(parseServiceAlert)
      .filter((alert): alert is ServiceAlert => alert !== null);
  } catch {
    // Офлайн-принцип: любая ошибка — просто нет активных уведомлений
    return [];
  }
}
