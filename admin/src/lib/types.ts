/**
 * Типы DTO публичного REST API backend (dev-conventions.md, §3).
 *
 * ВАЖНО: это НЕ GeoJSON-модель (web/src/lib/types.ts). Списочные REST-эндпоинты
 * (/lines, /stations, /alerts, /news) отдают camelCase-поля: `colorHex`,
 * `sortOrder`, `isTransfer`, `startsAt`, `publishedAt`. Формы сверены с живым
 * backend (GET http://localhost:8080/api/v1/...).
 */

/** Мультиязычное название: обязательные языки tg, ru, en (ТЗ §6.3.1). */
export type I18nName = {
  tg: string;
  ru: string;
  en: string;
};

/** Статусы линий (dev-conventions.md, §4). */
export type LineStatus =
  | "planned"
  | "under_construction"
  | "testing"
  | "active"
  | "suspended"
  | "decommissioned";

/** Статусы станций (dev-conventions.md, §4). */
export type StationStatus =
  | "planned"
  | "under_construction"
  | "testing"
  | "active"
  | "temporarily_closed"
  | "decommissioned";

/** Элементы безбарьерной среды станции. */
export type AccessibilityFeature =
  | "elevator"
  | "escalator"
  | "ramp"
  | "tactile"
  | "audio_assist";

/** Уровень важности сервисного уведомления. */
export type AlertSeverity = "info" | "warning" | "critical";

/** Позиция [lng, lat] (EPSG:4326). */
export type LngLat = [number, number];

/** GET /api/v1/lines — элемент списка линий. */
export type Line = {
  code: string;
  name: I18nName;
  /** Цвет линии в HEX, например "#E21B2D". */
  colorHex: string;
  status: LineStatus;
  sortOrder: number;
};

/** GET /api/v1/stations — элемент списка станций. */
export type Station = {
  code: string;
  name: I18nName;
  status: StationStatus;
  /** Коды линий, к которым относится станция. */
  lines: string[];
  isTransfer: boolean;
  accessibility: AccessibilityFeature[];
  coordinates: LngLat;
};

/** Цель уведомления: линия или станция. */
export type AlertTarget = {
  type: "line" | "station" | string;
  code: string;
};

/** GET /api/v1/alerts — активное сервисное уведомление. */
export type Alert = {
  code: string;
  severity: AlertSeverity;
  title: I18nName;
  body: I18nName;
  /** ISO-8601 UTC. */
  startsAt: string;
  /** ISO-8601 UTC; может отсутствовать у бессрочных уведомлений. */
  endsAt: string | null;
  targets: AlertTarget[];
};

/** GET /api/v1/news — новость. */
export type News = {
  slug: string;
  title: I18nName;
  body: I18nName;
  coverMediaUrl: string | null;
  /** ISO-8601 UTC. */
  publishedAt: string;
};
