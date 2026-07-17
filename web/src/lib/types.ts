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

/**
 * Новостная статья — контракт GET /api/v1/news и /api/v1/news/{slug}
 * (ТЗ §6.2.7). Публично отдаются только опубликованные статьи, поэтому
 * статус в контракт не входит.
 */
export type NewsArticle = {
  /** Стабильный слаг статьи, например "metro-construction-launch". */
  slug: string;
  title: I18nName;
  body: I18nName;
  /** URL обложки или null, если её нет. */
  coverMediaUrl: string | null;
  /** Дата публикации, ISO-8601 UTC. */
  publishedAt: string;
};

/** Type guard: фича — линия. */
export function isLineFeature(f: NetworkFeature): f is LineFeature {
  return f.properties.feature_type === "line";
}

/** Type guard: фича — станция. */
export function isStationFeature(f: NetworkFeature): f is StationFeature {
  return f.properties.feature_type === "station";
}

// ---------------------------------------------------------------------------
// Детальная карточка станции: GET /api/v1/stations/{code}
// Контракт использует camelCase (isTransfer, isAccessible, accessibilityFeatures)
// в отличие от snake_case GeoJSON-модели сети выше.
// ---------------------------------------------------------------------------

/** Статус объекта доступности (лифт/эскалатор и т.п.) в карточке станции. */
export type AccessibilityFeatureStatus =
  | "available"
  | "out_of_service"
  | "planned";

/** Выход со станции. */
export type StationExit = {
  code: string;
  name: I18nName;
  /** Оборудован для маломобильных пассажиров. */
  isAccessible: boolean;
  /** Позиция [lng, lat] (EPSG:4326). */
  coordinates: LngLat;
};

/**
 * Объект безбарьерной среды с описанием и статусом эксплуатации.
 * `type` обычно совпадает с элементами безбарьерной среды сети, но набор
 * на backend шире (напр. "accessible_toilet"), поэтому тип открытый —
 * подпись берётся из словаря, а при неизвестном коде — из `description`.
 */
export type StationAccessibilityFeature = {
  type: AccessibilityFeature | (string & {});
  description: I18nName;
  status: AccessibilityFeatureStatus;
};

/** Детальная информация о станции из API GET /stations/{code}. */
export type StationDetail = {
  code: string;
  name: I18nName;
  status: StationStatus;
  lines: string[];
  isTransfer: boolean;
  accessibility: AccessibilityFeature[];
  /** Позиция [lng, lat] (EPSG:4326). */
  coordinates: LngLat;
  exits: StationExit[];
  accessibilityFeatures: StationAccessibilityFeature[];
};

// ---------------------------------------------------------------------------
// Оценочные прибытия: GET /api/v1/stations/{code}/arrivals?lineCode={code}
// До подключения realtime backend рассчитывает их из статического headway.
// ---------------------------------------------------------------------------

/** Одно ближайшее оценочное прибытие. */
export type Arrival = {
  /** Местное время Душанбе в формате HH:mm. */
  time: string;
  /** Число минут до прибытия; 0 означает «сейчас». */
  etaMinutes: number;
};

/** Прибытия одной линии на выбранной станции. */
export type StationArrivals = {
  stationCode: string;
  lineCode: string;
  dayType: "weekday" | "weekend" | "holiday";
  serviceActive: boolean;
  headwayMinutes: number | null;
  /** В текущем статическом контуре всегда true. */
  estimated: boolean;
  generatedAt: string;
  arrivals: Arrival[];
};

/** Данные линии вместе с фактическим источником (API или демо-fallback). */
export type StationArrivalsResult = {
  data: StationArrivals;
  source: DataSource;
};

// ---------------------------------------------------------------------------
// Обращения граждан: POST /requests и POST /requests/track.
// ---------------------------------------------------------------------------

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

export type CitizenRequestPublic = {
  code: string;
  type: CitizenRequestType;
  status: CitizenRequestStatus;
  subject: string;
  response: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CitizenRequestCreateBody = {
  type: CitizenRequestType;
  subject: string;
  message: string;
  contactName?: string;
  contactEmail?: string;
  contactPhone?: string;
  lineCode?: string;
  stationCode?: string;
  consent: boolean;
};

export type CitizenRequestCreateResult = {
  request: CitizenRequestPublic;
  trackingToken: string;
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

// ---------------------------------------------------------------------------
// Билеты: POST /tickets/purchase, /tickets/{code}/topup, /tickets/validate,
// /tickets/{code}/refund, GET /tickets/{code}.
//
// ДЕМО-КОНТУР. Реального эквайринга нет: платежи имитирует DemoPaymentGateway,
// поэтому у билета, платежа и возврата есть обязательное поле `demo`. Карточных
// данных нет ни в одном запросе — backend их не принимает (см. javadoc
// PaymentGateway), и портал их не собирает.
// ---------------------------------------------------------------------------

/** Состояние выпущенного билета (TicketStatus на backend). */
export type TicketStatus =
  | "issued"
  | "active"
  | "used"
  | "expired"
  | "refunded"
  | "blocked";

/** Вид билета: разовая поездка или пополняемый проездной. */
export type TicketKind = "single" | "pass";

/** Состояние платежа (PaymentStatus на backend). */
export type PaymentStatus =
  | "pending"
  | "authorized"
  | "captured"
  | "failed"
  | "refunded";

/**
 * Билет — контракт TicketDto.
 *
 * Токена здесь нет и быть не может: он существует ровно один раз, в ответе на
 * покупку (см. TicketPurchaseResult). В системе хранится только его SHA-256.
 */
export type Ticket = {
  code: string;
  fareProductCode: string;
  kind: TicketKind;
  riderCategory: FareRiderCategory;
  status: TicketStatus;
  /** Начало окна действия, ISO-8601 UTC. */
  validFrom: string;
  /** Конец окна действия, ISO-8601 UTC. */
  validUntil: string;
  /** Цена, зафиксированная при покупке, а не текущая цена тарифа. */
  priceAmount: number;
  priceCurrency: string;
  /** Внесённый остаток проездного; null у разового билета. */
  balanceAmount: number | null;
  /** Момент погашения, ISO-8601 UTC; null — билет не гасился. */
  usedAt: string | null;
  /** За билетом не стоит реального платежа. */
  demo: boolean;
  updatedAt: string;
  allowedTransitions: TicketStatus[];
};

/** Платёж — контракт PaymentDto. Карточных данных не содержит. */
export type TicketPayment = {
  code: string;
  /** null — платёж отклонён, билет не выпускался. */
  ticketCode: string | null;
  kind: string;
  amount: number;
  currency: string;
  status: PaymentStatus;
  provider: string;
  providerRef: string | null;
  /** Причина отказа; заполнена ровно при status = "failed". */
  failureReason: string | null;
  demo: boolean;
  createdAt: string;
  updatedAt: string;
};

/** Возврат — контракт RefundDto. */
export type TicketRefund = {
  code: string;
  paymentCode: string;
  amount: number;
  currency: string;
  status: string;
  reason: string;
  failureReason: string | null;
  createdBy: string;
  demo: boolean;
  createdAt: string;
};

/**
 * Результат покупки — контракт TicketPurchaseResponse.
 *
 * `ticket === null` и `token === null` — платёж отклонён (HTTP 402). Это штатный
 * исход, а не сбой: причина лежит в `payment.failureReason`.
 */
export type TicketPurchaseResult = {
  ticket: Ticket | null;
  /** ЕДИНСТВЕННОЕ место, где существует токен. Повторно его не получить. */
  token: string | null;
  payment: TicketPayment;
  demo: boolean;
  /** Служебная пометка demo-контура от backend (RU, техническая). */
  notice: string;
};

/** Результат пополнения — контракт TicketTopUpResponse. */
export type TicketTopUpResult = {
  ticket: Ticket;
  payment: TicketPayment;
  demo: boolean;
  notice: string;
};

/** Машиночитаемая причина отказа при валидации (reason в 200-ответе). */
export type TicketValidationReason =
  | "ticket.token_unknown"
  | "ticket.blocked"
  | "ticket.already_used"
  | "ticket.expired"
  | "ticket.refunded"
  | "ticket.not_started"
  | "ticket.validation_too_soon";

/**
 * Решение по предъявленному билету — контракт TicketValidationDto.
 * Всегда HTTP 200: недействительный билет — штатный исход, а не ошибка.
 */
export type TicketValidation = {
  valid: boolean;
  /** null при valid = true. */
  reason: TicketValidationReason | null;
  /** null, если билет по токену не найден. */
  ticketCode: string | null;
  status: TicketStatus | null;
  kind: TicketKind | null;
  riderCategory: FareRiderCategory | null;
  validUntil: string | null;
  demo: boolean;
};

/** Тело POST /tickets/purchase. Платёжных данных не содержит намеренно. */
export type TicketPurchaseBody = {
  fareProductCode: string;
  /** Непроверенный идентификатор устройства для антифрода (НЕ персональные данные). */
  riderRef?: string;
  /** Код тест-сценария demo-эквайринга: approve | decline. */
  demoScenario?: string;
};

// ---------------------------------------------------------------------------
// Лента уведомлений (in-app): GET /api/v1/notifications
// Семантика таргет-фильтров — та же, что у /alerts (dev-conventions.md §3):
// рассылки без таргетов адресованы всей сети и попадают в выдачу всегда.
// ---------------------------------------------------------------------------

/** Тип рассылки (NotificationType на backend). */
export type NotificationType =
  | "info"
  | "warning"
  | "incident"
  | "maintenance"
  | "promo";

/** Таргет рассылки; segment/role — адресация по аудитории, а не по географии. */
export type NotificationTarget = {
  type: "line" | "station" | "segment" | "role";
  code: string;
};

/** Отправленная рассылка — контракт NotificationDto. */
export type NotificationMessage = {
  code: string;
  templateCode: string | null;
  /** Код связанного сервисного алерта; null — рассылка сама по себе. */
  alertCode: string | null;
  type: NotificationType;
  title: I18nName;
  body: I18nName;
  channels: string[];
  status: string;
  /** Пустой массив = рассылка на всю сеть. */
  targets: NotificationTarget[];
  scheduledAt: string | null;
  /** Момент отправки, ISO-8601 UTC; в публичном фиде всегда заполнен. */
  sentAt: string | null;
  updatedAt: string;
};

// ---------------------------------------------------------------------------
// Маршрутный поиск «откуда/куда»: GET /api/v1/routes?from={code}&to={code}
// Время в пути — ОЦЕНОЧНОЕ (до реального расписания). Несуществующий код
// станции → 404 route.station_not_found; отсутствие пути → found:false с
// пустыми legs/stops.
// ---------------------------------------------------------------------------

/** Участок маршрута в пределах одной линии (без пересадок). */
export type RouteLeg = {
  /** Код линии участка, например "L1". */
  lineCode: string;
  lineName: I18nName;
  /** Цвет линии в HEX, например "#E21B2D". */
  colorHex: string;
  /** Коды станций участка по порядку следования (вкл. точки пересадки). */
  stations: string[];
  /** Число перегонов участка. */
  segmentCount: number;
  /** Оценочное время участка, мин. */
  estimatedMinutes: number;
};

/** Остановка на маршруте в порядке следования. */
export type RouteStop = {
  code: string;
  name: I18nName;
  /** Линия, по которой пассажир проходит эту остановку. */
  lineCode: string;
  /** Остановка является точкой пересадки на маршруте. */
  transfer: boolean;
};

/** Построенный маршрут — контракт GET /api/v1/routes. */
export type Route = {
  /** Код станции отправления. */
  from: string;
  /** Код станции назначения. */
  to: string;
  /** Найден ли путь; false — legs и stops пусты. */
  found: boolean;
  /** Оценочное время всего маршрута, мин. */
  estimatedMinutes: number;
  /** Число пересадок. */
  transfers: number;
  /** Общее число перегонов. */
  segmentCount: number;
  legs: RouteLeg[];
  stops: RouteStop[];
};
