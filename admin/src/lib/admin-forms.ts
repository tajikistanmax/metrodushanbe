/**
 * Общие типы и константы для admin-write форм (CRUD поверх /api/v1/admin/**).
 *
 * Здесь — только сериализуемые типы тел запросов (зеркало backend DTO),
 * перечни допустимых значений (зеркало серверной валидации) и тип результата
 * действия. Никакого «use server»/«use client»: модуль импортируется и
 * серверными действиями (admin-actions.ts), и клиентскими формами.
 */

import type {
  AccessibilityFeature,
  AlertSeverity,
  LineStatus,
  StationStatus,
} from "./types";

/** i18n-значение формы: три обязательных языка (BR-NET-4/BR-CMS-1). */
export type I18nInput = { tg: string; ru: string; en: string };

// --- Перечни допустимых значений (зеркало серверных множеств) --------------

/** Статусы линии (NetworkService.LINE_STATUSES). */
export const LINE_STATUSES: readonly LineStatus[] = [
  "planned",
  "under_construction",
  "testing",
  "active",
  "suspended",
  "decommissioned",
] as const;

/** Статусы станции (NetworkService.STATION_STATUSES). */
export const STATION_STATUSES: readonly StationStatus[] = [
  "planned",
  "under_construction",
  "testing",
  "active",
  "temporarily_closed",
  "decommissioned",
] as const;

/** Уровни важности уведомления. */
export const ALERT_SEVERITIES: readonly AlertSeverity[] = [
  "info",
  "warning",
  "critical",
] as const;

/** Теги доступности станции (StationCreateRequest.accessibility). */
export const ACCESSIBILITY_FEATURES: readonly AccessibilityFeature[] = [
  "elevator",
  "escalator",
  "ramp",
  "tactile",
  "audio_assist",
] as const;

/** Тип таргета уведомления. */
export const TARGET_TYPES = ["line", "station"] as const;
export type TargetType = (typeof TARGET_TYPES)[number];

// --- Тела запросов (зеркало backend admin.web.dto) -------------------------

export type LineCreateBody = {
  code: string;
  name: I18nInput;
  colorHex: string;
  status: string;
  sortOrder?: number;
  path?: number[][];
};
export type LineUpdateBody = Omit<LineCreateBody, "code">;

export type StationCreateBody = {
  code: string;
  name: I18nInput;
  status: string;
  coordinates: [number, number];
  isTransfer?: boolean;
  accessibility?: string[];
  description?: I18nInput | null;
};
export type StationUpdateBody = Omit<StationCreateBody, "code"> & {
  coordinates?: [number, number];
};

export type AlertTargetBody = { type: string; code: string };

export type AlertCreateBody = {
  code: string;
  severity: string;
  title: I18nInput;
  body: I18nInput;
  /** ISO-8601 UTC (Instant). */
  startsAt: string;
  endsAt?: string;
  targets?: AlertTargetBody[];
};
export type AlertUpdateBody = Omit<AlertCreateBody, "code">;

export type NewsCreateBody = {
  slug: string;
  title: I18nInput;
  body: I18nInput;
  coverMediaUrl?: string;
};
export type NewsUpdateBody = Omit<NewsCreateBody, "slug">;

export const SCHEDULE_DAY_TYPES = ["weekday", "weekend", "holiday"] as const;
export type ScheduleDayType = (typeof SCHEDULE_DAY_TYPES)[number];

export type CalendarExceptionBody = {
  exceptionDate: string;
  dayType: ScheduleDayType;
  descriptionTg?: string;
  descriptionRu?: string;
  descriptionEn?: string;
  isRecurring: boolean;
};

export type CitizenRequestUpdateBody = {
  status: string;
  response?: string;
  assignedTo?: string;
};

export const FARE_RIDER_CATEGORIES = ["all", "adult", "child", "student", "senior"] as const;
export type FareRiderCategory = (typeof FARE_RIDER_CATEGORIES)[number];

export type FareCreateBody = {
  code: string;
  name: I18nInput;
  description: I18nInput;
  amount: number;
  currency: string;
  riderCategory: FareRiderCategory;
  validityMinutes?: number;
  active: boolean;
};
export type FareUpdateBody = Omit<FareCreateBody, "code">;

// --- Результат серверного действия -----------------------------------------

/** Ошибка из единого envelope backend {error:{code,message,details?},requestId}. */
export type ActionError = {
  code: string;
  message: string;
  details?: unknown;
};

/** Дискриминированный результат мутации: успех с данными или ошибка. */
export type ActionResult<T> =
  | { ok: true; data: T }
  | { ok: false; error: ActionError };

/** Пустой i18n-объект для инициализации форм. */
export const EMPTY_I18N: I18nInput = { tg: "", ru: "", en: "" };
