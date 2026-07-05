/**
 * Типы GeoJSON-модели сети метро.
 * Совместимы со схемой канонического файла data/demo-network.geojson
 * и с ответом API GET /api/v1/network/geojson (dev-conventions.md, §3–4).
 *
 * Свойства объявлены через type-алиасы (а не interface), чтобы они были
 * структурно совместимы с GeoJsonProperties из типов MapLibre/geojson.
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

/** Позиция [lng, lat] (EPSG:4326). */
export type LngLat = [number, number];

export type LineProperties = {
  feature_type: "line";
  /** Стабильный внешний код, например "L1". */
  code: string;
  name: I18nName;
  /** Цвет линии в HEX, например "#E21B2D". */
  color_hex: string;
  status: LineStatus;
  sort_order: number;
};

export type StationProperties = {
  feature_type: "station";
  /** Стабильный внешний код, например "ST-L1-01". */
  code: string;
  name: I18nName;
  status: StationStatus;
  /** Коды линий, к которым относится станция. */
  lines: string[];
  is_transfer: boolean;
  accessibility: AccessibilityFeature[];
};

export type LineFeature = {
  type: "Feature";
  id?: string | number;
  properties: LineProperties;
  geometry: {
    type: "LineString";
    coordinates: LngLat[];
  };
};

export type StationFeature = {
  type: "Feature";
  id?: string | number;
  properties: StationProperties;
  geometry: {
    type: "Point";
    coordinates: LngLat;
  };
};

export type NetworkFeature = LineFeature | StationFeature;

/** Метаданные демо-файла (могут отсутствовать в ответе API). */
export type NetworkMetadata = {
  title?: string;
  disclaimer?: string;
  version?: string;
  crs?: string;
  languages?: string[];
};

/** FeatureCollection всей сети — контракт карты. */
export type NetworkGeoJson = {
  type: "FeatureCollection";
  metadata?: NetworkMetadata;
  features: NetworkFeature[];
};

/** Откуда получены данные: живой API или офлайн-демо из бандла. */
export type DataSource = "api" | "demo";

/** Уровень важности сервисного уведомления (ТЗ §6.2.6). */
export type AlertSeverity = "info" | "warning" | "critical";

/** Цель уведомления: линия или станция по стабильному коду. */
export type AlertTarget = {
  type: "line" | "station";
  code: string;
};

/**
 * Активное сервисное уведомление — контракт GET /api/v1/alerts.
 * Пустой массив `targets` означает уведомление на всю сеть.
 */
export type ServiceAlert = {
  /** Стабильный внешний код, например "ALERT-2026-001". */
  code: string;
  severity: AlertSeverity;
  title: I18nName;
  body: I18nName;
  /** Начало действия, ISO-8601 UTC. */
  startsAt: string;
  /** Окончание действия, ISO-8601 UTC; null — бессрочно. */
  endsAt: string | null;
  targets: AlertTarget[];
};

/** Type guard: фича — линия. */
export function isLineFeature(f: NetworkFeature): f is LineFeature {
  return f.properties.feature_type === "line";
}

/** Type guard: фича — станция. */
export function isStationFeature(f: NetworkFeature): f is StationFeature {
  return f.properties.feature_type === "station";
}
