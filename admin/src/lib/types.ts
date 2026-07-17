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

/**
 * Карточка AI-агента. Свободные тексты приходят полными i18n-объектами (tg/ru/en), как и у
 * остальных сущностей платформы, — язык выбирает консоль через pickName.
 * modelProvider/modelClass/status — коды: подписи статусов живут в словаре (agents.statuses).
 */
export type AiAgent = {
  code: string;
  name: I18nName;
  role: I18nName;
  modelProvider: string;
  modelClass: string;
  status: string;
  capabilities: I18nName[];
  signals: I18nName[];
  nextAction: I18nName;
};

export type AiBriefing = {
  generatedAt: string;
  posture: string;
  agents: AiAgent[];
  recommendations: I18nName[];
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

/** Формат источника импорта (ImportFormat.codes()). */
export type ImportFormat = "geojson" | "gtfs" | "csv";

/**
 * Вид импорта (ImportJob.TYPE_*): что именно импортировали. Отличается от формата —
 * `network_gtfs` и `fare_gtfs` приходят одним контейнером (format=gtfs), но меняют разное:
 * первый — топологию сети, второй — цены. В ленте они обязаны быть различимы.
 */
export type ImportJobType =
  | "network_geojson"
  | "network_gtfs"
  | "network_csv"
  | "fare_gtfs";

/**
 * Что выбирает оператор в форме загрузки. Это не формат: `gtfs` и `gtfs-fares` —
 * один формат (ZIP), но разные эндпоинты, наборы полей и последствия.
 */
export type ImportKind = "geojson" | "gtfs" | "csv" | "gtfs-fares";

/** Задание импорта — GET/POST /api/v1/admin/imports[/gtfs|/gtfs-fares|/csv]. */
export type ImportJob = {
  id: string;
  type: ImportJobType | string;
  /** Формат источника: geojson | gtfs | csv (INT-04). */
  format: ImportFormat | string;
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

/**
 * Постраничная выдача admin-контура. Форма едина для всех лент: у backend один
 * контракт пагинации (ImportPageDto), и второй, отличающийся только именами
 * полей, консоли ничего не даёт.
 */
export type AdminPage<T> = {
  items: T[];
  /** Номер текущей страницы, с нуля. */
  page: number;
  /** Запрошенный размер страницы (backend зажимает его в 1..200). */
  size: number;
  totalElements: number;
  totalPages: number;
};

export type ImportPage = AdminPage<ImportJob>;

/**
 * Уровень записи в отчёте импорта. `warning` — не сбой: так GTFS-парсер
 * сообщает о недостающих переводах (язык фида подставлен вместо tg/ru/en).
 * Консоль обязана показывать их отдельно от `error`, иначе рабочий список
 * «что перевести» читается как список поломок.
 */
export type ImportErrorSeverity = "error" | "warning";

export type ImportError = {
  id: string;
  featureRef: string;
  message: string;
  severity: ImportErrorSeverity | string;
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

// --- Рассылки (NTF-01…06) ---------------------------------------------------

export type NotificationType =
  | "info"
  | "warning"
  | "incident"
  | "maintenance"
  | "promo";

export type NotificationStatus =
  | "draft"
  | "scheduled"
  | "sending"
  | "sent"
  | "cancelled";

/** Каналы доставки. Реален только in_app; остальные имитируются (см. `simulated`). */
export type NotificationChannel = "in_app" | "push" | "email" | "sms";

/** Тип адресации рассылки; пустой список targets = вся сеть. */
export type NotificationTargetType = "line" | "station" | "segment" | "role";

export type NotificationTarget = {
  type: NotificationTargetType;
  code: string;
};

/**
 * Рассылка (GET /admin/notifications). title/body — полные i18n-объекты:
 * backend не резолвит ?lang=, выбор языка делает консоль (pickName).
 */
export type Notification = {
  code: string;
  /** Шаблон-источник; тексты уже скопированы и от шаблона не зависят. */
  templateCode: string | null;
  alertCode: string | null;
  type: NotificationType;
  title: I18nName;
  body: I18nName;
  channels: NotificationChannel[];
  status: NotificationStatus;
  targets: NotificationTarget[];
  scheduledAt: string | null;
  sentAt: string | null;
  updatedAt: string | null;
  /**
   * Куда рассылку можно перевести из текущего состояния — считает backend
   * (NotificationStatus.TRANSITIONS), как и у Incident/WebhookDelivery.
   * Держать копию карты здесь нельзя: две копии одного правила разъедутся,
   * и консоль начнёт предлагать переход, который backend отклонит 400.
   */
  allowedTransitions: NotificationStatus[];
  /** NotificationStatus.frozen(): доставка начата — содержимое уже неизменно. */
  frozen: boolean;
};

/**
 * Страница ленты рассылок (GET /admin/notifications). Лента растёт без потолка,
 * а каждая строка тянет за собой таргеты — поэтому целиком не отдаётся.
 */
export type NotificationPage = AdminPage<Notification>;

/** Статус одной доставки (DeliveryStatus). */
export type NotificationDeliveryStatus =
  | "pending"
  | "sent"
  | "delivered"
  | "failed";

/**
 * Доставка по одному каналу одному получателю (NTF-06).
 * `simulated` — у канала нет реального провайдера, на demo-контуре доставка
 * только имитируется. Скрывать этот признак нельзя: «доставлено» там, где
 * ничего не ушло, дезинформирует оператора.
 */
export type NotificationDelivery = {
  /** uuid; своего code у доставки нет — повтор идёт по нему. */
  id: string;
  messageCode: string;
  channel: NotificationChannel;
  recipient: string;
  status: NotificationDeliveryStatus;
  attempts: number;
  lastError: string | null;
  simulated: boolean;
  sentAt: string | null;
  deliveredAt: string | null;
  updatedAt: string | null;
};

/**
 * Страница очереди доставок (GET /admin/notifications/{code}/deliveries и
 * /admin/notifications/deliveries/problems). Очередь растёт как рассылки ×
 * получатели × каналы, поэтому целиком не отдаётся.
 */
export type NotificationDeliveryPage = AdminPage<NotificationDelivery>;

/** Заготовка текста рассылки (NTF-05). */
export type NotificationTemplate = {
  code: string;
  name: string;
  type: NotificationType;
  title: I18nName;
  body: I18nName;
  channels: NotificationChannel[];
  active: boolean;
  updatedAt: string | null;
};

// --- Билеты и платежи (TKT-01…06) -------------------------------------------

export type TicketKind = "single" | "pass";

export type TicketStatus =
  | "issued"
  | "active"
  | "used"
  | "expired"
  | "refunded"
  | "blocked";

/**
 * Билет (GET /admin/tickets). Токена QR здесь нет и быть не может — в системе
 * хранится только его хеш. `demo === true` — за билетом нет реального платежа.
 */
export type Ticket = {
  code: string;
  fareProductCode: string;
  kind: TicketKind;
  riderCategory: FareRiderCategory;
  status: TicketStatus;
  validFrom: string;
  validUntil: string | null;
  /** Цена, зафиксированная при покупке, а не текущая цена тарифа. */
  priceAmount: number;
  priceCurrency: string;
  balanceAmount: number | null;
  usedAt: string | null;
  demo: boolean;
  updatedAt: string | null;
  allowedTransitions: TicketStatus[];
};

export type PaymentKind = "purchase" | "topup";

export type PaymentStatus =
  | "pending"
  | "authorized"
  | "captured"
  | "failed"
  | "refunded";

/** Платёж (GET /admin/payments). Карточных данных нет — их нет и в модели. */
export type Payment = {
  code: string;
  /** null — платёж отклонён, билет не выпускался. */
  ticketCode: string | null;
  kind: PaymentKind;
  amount: number;
  currency: string;
  status: PaymentStatus;
  provider: string;
  providerRef: string | null;
  /** Заполнена ровно при status=failed. */
  failureReason: string | null;
  demo: boolean;
  createdAt: string;
  updatedAt: string | null;
};

/** Результат ручного возврата (POST /admin/tickets/{code}/refund). */
export type Refund = {
  code: string;
  paymentCode: string;
  amount: number;
  currency: string;
  status: PaymentStatus;
  reason: string;
  failureReason: string | null;
  createdBy: string;
  demo: boolean;
  createdAt: string;
};

export type BlocklistSubjectType = "ticket" | "token" | "rider";

/** Запись чёрного списка (TKT-06); для token в subjectCode — SHA-256. */
export type BlocklistEntry = {
  code: string;
  subjectType: BlocklistSubjectType;
  subjectCode: string;
  reason: string;
  createdBy: string;
  createdAt: string;
};

// --- Интеграции: вебхуки (INT-02/03/05, ADM-06, U-OPS-04) -------------------

export type WebhookEventType =
  | "alert_published"
  | "alert_cleared"
  | "incident_opened"
  | "incident_resolved"
  | "station_status_changed"
  | "schedule_changed"
  | "train_delayed";

/**
 * Подписчик вебхуков (GET /admin/webhooks). Секрета здесь нет и не будет —
 * только `secretFingerprint` (первые 8 hex хеша) для сверки после ротации.
 */
export type WebhookSubscription = {
  code: string;
  name: string;
  targetUrl: string;
  eventTypes: WebhookEventType[];
  active: boolean;
  rateLimitPerMinute: number;
  secretFingerprint: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string | null;
};

/**
 * Ответ операций, порождающих секрет (создание и ротация). Единственное место
 * во всём API, где виден плейнтекст: показать интегратору и не сохранять.
 */
export type WebhookSecret = {
  subscription: WebhookSubscription;
  secret: string;
};

/** pending|sent|failed|dead; dead — это DLQ. */
export type WebhookDeliveryStatus = "pending" | "sent" | "failed" | "dead";

export type WebhookDelivery = {
  code: string;
  eventId: string;
  eventType: WebhookEventType;
  aggregateType: string;
  aggregateCode: string;
  subscriptionCode: string;
  status: WebhookDeliveryStatus;
  attempts: number;
  responseStatus: number | null;
  lastError: string | null;
  traceId: string | null;
  nextAttemptAt: string | null;
  createdAt: string;
  updatedAt: string | null;
  /** Решает backend; консоль по нему рисует кнопку «Повторить». */
  retryable: boolean;
  allowedTransitions: WebhookDeliveryStatus[];
};

/**
 * Страница очереди доставок вебхуков (GET /admin/webhooks/deliveries).
 * Очередь растёт как события × подписчики — полная выдача на проде означала бы
 * вычитывание всей таблицы на каждый заход в раздел.
 */
export type WebhookDeliveryPage = AdminPage<WebhookDelivery>;

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
    // Многосегментная трасса (ветки): backend отдаёт MultiLineString только
    // когда сегментов больше одного — GeoJsonBuilder.lineGeometry.
    | { type: "MultiLineString"; coordinates: LngLat[][] }
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
