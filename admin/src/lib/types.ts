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

export type AiAgent = {
  code: string;
  name: I18nName;
  role: string;
  modelProvider: string;
  modelClass: string;
  status: string;
  capabilities: string[];
  signals: string[];
  nextAction: string;
};

export type AiBriefing = {
  generatedAt: string;
  posture: string;
  agents: AiAgent[];
  recommendations: string[];
};

/** GET /api/v1/admin/audit — событие журнала аудита. */
export type AuditEvent = {
  id: string;
  actor: string;
  action: string;
  entityType: string;
  entityId: string;
  before: Record<string, unknown> | null;
  after: Record<string, unknown> | null;
  at: string;
};

/** Задание импорта GeoJSON — GET/POST /api/v1/admin/imports. */
export type ImportJob = {
  id: string;
  type: string;
  status: "pending" | "running" | "success" | "partial" | "failed" | string;
  sourceName: string | null;
  sourceHash: string;
  featureCount: number;
  createdCount: number;
  updatedCount: number;
  failedCount: number;
  startedAt: string | null;
  finishedAt: string | null;
  createdAt: string;
};

export type ImportPage = {
  items: ImportJob[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type ImportError = {
  id: string;
  featureRef: string;
  message: string;
  severity: string;
  at: string;
};

/** Исключение календаря расписаний — /api/v1/admin/calendar-exceptions. */
export type CalendarException = {
  id: number;
  exceptionDate: string;
  dayType: "weekday" | "weekend" | "holiday" | string;
  descriptionTg: string | null;
  descriptionRu: string | null;
  descriptionEn: string | null;
  isRecurring: boolean;
  createdAt: string;
};

/** Управляемый runtime-флаг — /api/v1/admin/feature-flags. */
export type FeatureFlag = {
  flagKey: string;
  enabled: boolean;
  description: string | null;
  updatedAt: string;
  updatedBy: string | null;
};

export type CitizenRequestType =
  | "complaint"
  | "suggestion"
  | "incident"
  | "question"
  | "lost_item";

export type CitizenRequestStatus =
  | "new"
  | "in_progress"
  | "awaiting_info"
  | "resolved"
  | "closed"
  | "reopened";

export type CitizenRequestPriority = "low" | "normal" | "high";

/** Полное операторское представление обращения — /api/v1/admin/requests. */
export type CitizenRequestAdmin = {
  code: string;
  type: CitizenRequestType;
  priority: CitizenRequestPriority;
  status: CitizenRequestStatus;
  subject: string;
  message: string;
  contactName: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  lineCode: string | null;
  stationCode: string | null;
  response: string | null;
  assignedTo: string | null;
  responseDueAt: string;
  resolutionDueAt: string;
  responseSlaBreached: boolean;
  resolutionSlaBreached: boolean;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
};

export type IncidentCategory =
  | "safety"
  | "technical"
  | "passenger"
  | "infrastructure"
  | "other";

/**
 * Критичность инцидента — внутренняя шкала реагирования. Не путать с
 * AlertSeverity (info|warning|critical): та отвечает за громкость сообщения
 * пассажиру, эта — за срочность для оператора.
 */
export type IncidentSeverity = "low" | "medium" | "high" | "critical";

export type IncidentStatus =
  | "open"
  | "acknowledged"
  | "in_progress"
  | "resolved"
  | "closed";

/** Инцидент (GET /admin/incidents). */
export type Incident = {
  code: string;
  category: IncidentCategory;
  severity: IncidentSeverity;
  status: IncidentStatus;
  title: string;
  description: string;
  lineCode: string | null;
  stationCode: string | null;
  reportedBy: string;
  assignedTo: string | null;
  resolution: string | null;
  publicAlertCode: string | null;
  occurredAt: string;
  acknowledgedAt: string | null;
  resolvedAt: string | null;
  closedAt: string | null;
  updatedAt: string | null;
  /** Разрешённые переходы из текущего статуса — источник истины на backend. */
  allowedTransitions: IncidentStatus[];
};

/** Счётчики плиток дашборда (GET /admin/incidents/stats). */
export type IncidentStats = {
  today: number;
  open: number;
  byCategory: Record<IncidentCategory, number>;
};

/** Оператор консоли (GET /admin/users). Хеш пароля backend не отдаёт. */
export type AdminUserAccount = {
  username: string;
  displayName: string;
  role: "viewer" | "operator" | "editor" | "superadmin";
  active: boolean;
  sessionVersion: number;
  lastLoginAt: string | null;
  updatedAt: string | null;
};

export type FareRiderCategory = "all" | "adult" | "child" | "student" | "senior";

export type FareProduct = {
  code: string;
  name: I18nName;
  description: I18nName;
  amount: number;
  currency: string;
  riderCategory: FareRiderCategory;
  validityMinutes: number | null;
  active: boolean;
  updatedAt: string | null;
};

/**
 * Минимальная GeoJSON-модель сети для схемы на дашборде
 * (GET /network/geojson; полная модель — в web/src/lib/types.ts).
 */
export type NetworkFeature = {
  type: "Feature";
  id?: string | number;
  properties: {
    feature_type: "line" | "station" | string;
    code: string;
    name?: Partial<I18nName>;
    color_hex?: string;
    status?: string;
    lines?: string[];
    is_transfer?: boolean;
    sort_order?: number;
  };
  geometry:
    | { type: "LineString"; coordinates: LngLat[] }
    | { type: "Point"; coordinates: LngLat };
};

export type NetworkGeoJson = {
  type: "FeatureCollection";
  features: NetworkFeature[];
};

/** Ответ GET /actuator/health (Spring Boot). */
export type HealthStatus = {
  status: "UP" | "DOWN" | "OUT_OF_SERVICE" | "UNKNOWN" | string;
};

export type AiChatRequest = {
  agentCode: string;
  message: string;
  context?: Record<string, unknown>;
};

export type AiChatResponse = {
  agentCode: string;
  reply: string;
  sources: string[];
};
