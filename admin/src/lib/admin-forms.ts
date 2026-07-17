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

/**
 * Размер страницы админских лент по умолчанию — тот же, что у ленты импортов
 * (контракт /v1/admin/imports). Потолок (200) держит backend: клиент не место
 * для правила, которое защищает БД.
 *
 * Живёт здесь, а не в admin-actions.ts: тот модуль «use server», и экспортировать
 * из него можно только async-функции.
 */
export const ADMIN_PAGE_SIZE = 50;

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

// --- Инциденты --------------------------------------------------------------

export const INCIDENT_CATEGORIES = [
  "safety",
  "technical",
  "passenger",
  "infrastructure",
  "other",
] as const;
export type IncidentCategoryInput = (typeof INCIDENT_CATEGORIES)[number];

export const INCIDENT_SEVERITIES = ["low", "medium", "high", "critical"] as const;
export type IncidentSeverityInput = (typeof INCIDENT_SEVERITIES)[number];

export type IncidentCreateBody = {
  category: IncidentCategoryInput;
  severity: IncidentSeverityInput;
  title: string;
  description: string;
  lineCode?: string;
  stationCode?: string;
  assignedTo?: string;
  /** ISO-8601; момент возникновения, а не регистрации. */
  occurredAt: string;
};

export type IncidentUpdateBody = IncidentCreateBody;

/** Переход по workflow; resolution обязателен только для 'resolved'. */
export type IncidentTransitionBody = {
  status: string;
  resolution?: string;
};

// --- Рассылки (NTF-01…06) ---------------------------------------------------

/** NotificationType.codes(). */
export const NOTIFICATION_TYPES = [
  "info",
  "warning",
  "incident",
  "maintenance",
  "promo",
] as const;
export type NotificationTypeInput = (typeof NOTIFICATION_TYPES)[number];

/** NotificationStatus.codes(). */
export const NOTIFICATION_STATUSES = [
  "draft",
  "scheduled",
  "sending",
  "sent",
  "cancelled",
] as const;
export type NotificationStatusInput = (typeof NOTIFICATION_STATUSES)[number];

/**
 * NotificationChannel.codes(). Реален только in_app (публичный фид);
 * push/email/sms на demo-контуре имитируются — NotificationChannel.external().
 */
export const NOTIFICATION_CHANNELS = ["in_app", "push", "email", "sms"] as const;
export type NotificationChannelInput = (typeof NOTIFICATION_CHANNELS)[number];

/** Каналы без реального провайдера: доставка по ним только имитируется. */
export const SIMULATED_CHANNELS: readonly NotificationChannelInput[] = [
  "push",
  "email",
  "sms",
] as const;

/** NotificationTargetDto.type. */
export const NOTIFICATION_TARGET_TYPES = [
  "line",
  "station",
  "segment",
  "role",
] as const;
export type NotificationTargetTypeInput =
  (typeof NOTIFICATION_TARGET_TYPES)[number];

export type NotificationTargetBody = {
  type: NotificationTargetTypeInput;
  code: string;
};

/**
 * Создание рассылки. Статус не принимается: рассылка всегда стартует черновиком.
 * Заданный scheduledAt сразу переводит её в scheduled (NTF-05).
 *
 * type/title/body/channels необязательны на уровне контракта только потому, что
 * их может дать templateCode; без шаблона их требует сервис.
 */
export type NotificationCreateBody = {
  code: string;
  templateCode?: string;
  alertCode?: string;
  type?: NotificationTypeInput;
  title?: I18nInput;
  body?: I18nInput;
  channels?: NotificationChannelInput[];
  targets?: NotificationTargetBody[];
  /** ISO-8601 со смещением; отсутствие — публикация не запланирована. */
  scheduledAt?: string;
};

/** Редактирование: полная замена содержимого, тексты обязательны. */
export type NotificationUpdateBody = {
  type: NotificationTypeInput;
  title: I18nInput;
  body: I18nInput;
  channels: NotificationChannelInput[];
  targets?: NotificationTargetBody[];
  scheduledAt?: string;
};

export type NotificationTemplateCreateBody = {
  code: string;
  name: string;
  type: NotificationTypeInput;
  title: I18nInput;
  body: I18nInput;
  channels: NotificationChannelInput[];
  active: boolean;
};
export type NotificationTemplateUpdateBody = Omit<
  NotificationTemplateCreateBody,
  "code"
>;

// --- Билеты, платежи, чёрный список (TKT-03/05/06) --------------------------

export const TICKET_STATUSES = [
  "issued",
  "active",
  "used",
  "expired",
  "refunded",
  "blocked",
] as const;
export type TicketStatusInput = (typeof TICKET_STATUSES)[number];

export const PAYMENT_STATUSES = [
  "pending",
  "authorized",
  "captured",
  "failed",
  "refunded",
] as const;
export type PaymentStatusInput = (typeof PAYMENT_STATUSES)[number];

export const BLOCKLIST_SUBJECT_TYPES = ["ticket", "token", "rider"] as const;
export type BlocklistSubjectTypeInput =
  (typeof BLOCKLIST_SUBJECT_TYPES)[number];

/** Ручной возврат из консоли: допускает даже погашенный билет (TKT-03). */
export type TicketRefundBody = {
  reason: string;
};

/**
 * Добавление в чёрный список. Для subjectType=token в subjectValue передаётся
 * САМ токен — сервис заменит его на SHA-256 перед записью.
 */
export type BlocklistCreateBody = {
  subjectType: BlocklistSubjectTypeInput;
  subjectValue: string;
  reason: string;
};

// --- Вебхуки (ADM-06, U-OPS-04) ---------------------------------------------

/** WebhookEventType.codes(). */
export const WEBHOOK_EVENT_TYPES = [
  "alert_published",
  "alert_cleared",
  "incident_opened",
  "incident_resolved",
  "station_status_changed",
  "schedule_changed",
  "train_delayed",
] as const;
export type WebhookEventTypeInput = (typeof WEBHOOK_EVENT_TYPES)[number];

/** WebhookDeliveryStatus.codes(); dead — это DLQ. */
export const WEBHOOK_DELIVERY_STATUSES = [
  "pending",
  "sent",
  "failed",
  "dead",
] as const;
export type WebhookDeliveryStatusInput =
  (typeof WEBHOOK_DELIVERY_STATUSES)[number];

/**
 * Заведение подписчика. Секрета во входных данных нет намеренно: его
 * генерирует сервер и показывает ОДИН раз в ответе.
 */
export type WebhookCreateBody = {
  code: string;
  name: string;
  targetUrl: string;
  eventTypes: WebhookEventTypeInput[];
  active: boolean;
  rateLimitPerMinute?: number;
};
export type WebhookUpdateBody = Omit<WebhookCreateBody, "code">;

// --- Импорт (INT-04) --------------------------------------------------------

/**
 * Что выбирает оператор в форме — вид импорта, а не формат источника (`ImportFormat`
 * в types.ts): `gtfs` и `gtfs-fares` идут одним ZIP, но первый переписывает сеть,
 * второй — цены. Один пункт списка на оба означал бы, что от загруженного файла
 * зависит, что именно поменяется, — а оператор узнаёт об этом уже по факту.
 */
export const IMPORT_KINDS = ["geojson", "gtfs", "gtfs-fares", "csv"] as const;
export type ImportKindInput = (typeof IMPORT_KINDS)[number];

/**
 * Публикация импортированных тарифов. Пусто — решает не импорт: новый продукт создаётся
 * неактивным, у существующего флаг сохраняется (в GTFS признака публикации нет).
 */
export const IMPORT_FARE_ACTIVE = ["", "true", "false"] as const;
export type ImportFareActive = (typeof IMPORT_FARE_ACTIVE)[number];

/** Язык GTFS-фида; пусто — берётся из agency.txt:agency_lang. */
export const IMPORT_FEED_LANGS = ["", "tg", "ru", "en"] as const;
export type ImportFeedLang = (typeof IMPORT_FEED_LANGS)[number];

/** Статус жизненного цикла импортируемых объектов: в GTFS его нет. */
export const IMPORT_TARGET_STATUSES: readonly LineStatus[] = [
  "planned",
  "under_construction",
  "testing",
  "active",
] as const;

// --- Операторы консоли ------------------------------------------------------

export const ADMIN_USER_ROLES = ["viewer", "operator", "editor", "superadmin"] as const;
export type AdminUserRole = (typeof ADMIN_USER_ROLES)[number];

/** Минимальная длина пароля; совпадает с @Size(min = 12) на backend. */
export const ADMIN_PASSWORD_MIN_LENGTH = 12;

export type AdminUserCreateBody = {
  username: string;
  displayName: string;
  password: string;
  role: AdminUserRole;
  active: boolean;
};

/** При изменении пароль необязателен: пустое значение оставляет текущий. */
export type AdminUserUpdateBody = {
  displayName: string;
  password?: string;
  role: AdminUserRole;
  active: boolean;
};

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
