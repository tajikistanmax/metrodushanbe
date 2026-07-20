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
import { ADMIN_FETCH_TIMEOUT_MS, adminApiKey } from "./server-config";
import { requireAdminRole, requireAdminSession } from "./server-auth";
import { createActorToken } from "./actor-token";
import { ADMIN_PAGE_SIZE } from "./admin-forms";
import type {
  ActionResult,
  AdminUserCreateBody,
  AdminUserUpdateBody,
  AlertCreateBody,
  AlertUpdateBody,
  BlocklistCreateBody,
  LineCreateBody,
  LineUpdateBody,
  NewsCreateBody,
  NewsUpdateBody,
  NotificationCreateBody,
  NotificationTemplateCreateBody,
  NotificationTemplateUpdateBody,
  NotificationUpdateBody,
  CalendarExceptionBody,
  CitizenRequestUpdateBody,
  FareCreateBody,
  FareUpdateBody,
  IncidentCreateBody,
  IncidentTransitionBody,
  IncidentUpdateBody,
  StationCreateBody,
  StationUpdateBody,
  TicketRefundBody,
  WebhookCreateBody,
  WebhookUpdateBody,
} from "./admin-forms";
import type {
  AdminUserAccount,
  AiChatRequest,
  AiChatResponse,
  Alert,
  AuditEvent,
  BlocklistEntry,
  CalendarException,
  CitizenRequestAdmin,
  FeatureFlag,
  FareProduct,
  Incident,
  IncidentStats,
  ImportError,
  ImportPage,
  Line,
  News,
  Notification,
  NotificationDelivery,
  NotificationDeliveryPage,
  NotificationPage,
  NotificationTemplate,
  Payment,
  Refund,
  Station,
  Ticket,
  WebhookDelivery,
  WebhookDeliveryPage,
  WebhookSecret,
  WebhookSubscription,
} from "./types";

/** Форма результата чтения для таблиц/панелей: данные либо причина ошибки. */
type ReadResult<T> = { data: T | null; error: string | null };

/** Единая query-строка пагинации: контракт /v1/admin/imports. */
function pageQuery(page: number, size: number): string {
  return `?page=${page}&size=${size}`;
}

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
  extraHeaders?: Record<string, string>,
): Promise<ActionResult<T>> {
  const session = await requireAdminSession();
  const adminKey = adminApiKey();
  // Подписанный токен актора (аудит-пункт 5): backend по нему подтверждает имя и
  // сверяет sessionVersion. Голый X-Admin-Actor в prod уже не принимается.
  const actorToken = await createActorToken(session.username, session.accountVersion);
  try {
    const res = await fetch(`${API_BASE}/admin${path}`, {
      method,
      cache: "no-store",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        "X-Admin-Key": adminKey,
        // Актор аудита (BR-ADM-1) — логин из подписанной сессии, а не константа:
        // в журнале должно быть видно, кто именно выполнил операцию.
        "X-Admin-Actor": session.username,
        "X-Admin-Actor-Token": actorToken,
        ...extraHeaders,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(ADMIN_FETCH_TIMEOUT_MS),
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

/** Защищённое серверное чтение /admin/** с единым ключом и envelope ошибки. */
async function adminRead<T>(path: string): Promise<ReadResult<T>> {
  const session = await requireAdminSession();
  const adminKey = adminApiKey();
  const actorToken = await createActorToken(session.username, session.accountVersion);
  try {
    const res = await fetch(`${API_BASE}/admin${path}`, {
      cache: "no-store",
      headers: {
        Accept: "application/json",
        "X-Admin-Key": adminKey,
        "X-Admin-Actor": session.username,
        "X-Admin-Actor-Token": actorToken,
      },
      signal: AbortSignal.timeout(ADMIN_FETCH_TIMEOUT_MS),
    });
    if (!res.ok) {
      return { data: null, error: `HTTP ${res.status} ${res.statusText}`.trim() };
    }
    return { data: (await res.json()) as T, error: null };
  } catch (e) {
    return { data: null, error: e instanceof Error ? e.message : String(e) };
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
  await requireAdminRole("editor");
  const r = await adminFetch<Line>("/lines", "POST", body);
  if (r.ok) revalidatePath("/lines");
  return r;
}

export async function updateLine(
  code: string,
  body: LineUpdateBody,
): Promise<ActionResult<Line>> {
  await requireAdminRole("editor");
  const r = await adminFetch<Line>(`/lines/${encodeURIComponent(code)}`, "PUT", body);
  if (r.ok) revalidatePath("/lines");
  return r;
}

export async function deleteLine(code: string): Promise<ActionResult<null>> {
  await requireAdminRole("editor");
  const r = await adminFetch<null>(`/lines/${encodeURIComponent(code)}`, "DELETE");
  if (r.ok) revalidatePath("/lines");
  return r;
}

// --- Станции ----------------------------------------------------------------

export async function createStation(
  body: StationCreateBody,
): Promise<ActionResult<Station>> {
  await requireAdminRole("editor");
  const r = await adminFetch<Station>("/stations", "POST", body);
  if (r.ok) revalidatePath("/stations");
  return r;
}

export async function updateStation(
  code: string,
  body: StationUpdateBody,
): Promise<ActionResult<Station>> {
  await requireAdminRole("editor");
  const r = await adminFetch<Station>(
    `/stations/${encodeURIComponent(code)}`,
    "PUT",
    body,
  );
  if (r.ok) revalidatePath("/stations");
  return r;
}

export async function deleteStation(code: string): Promise<ActionResult<null>> {
  await requireAdminRole("editor");
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
  await requireAdminRole("operator");
  const r = await adminFetch<Alert>("/alerts", "POST", body);
  if (r.ok) revalidatePath("/alerts");
  return r;
}

export async function updateAlert(
  code: string,
  body: AlertUpdateBody,
): Promise<ActionResult<Alert>> {
  await requireAdminRole("operator");
  const r = await adminFetch<Alert>(
    `/alerts/${encodeURIComponent(code)}`,
    "PUT",
    body,
  );
  if (r.ok) revalidatePath("/alerts");
  return r;
}

export async function publishAlert(code: string): Promise<ActionResult<Alert>> {
  await requireAdminRole("operator");
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
  await requireAdminRole("editor");
  const r = await adminFetch<News>("/news", "POST", body);
  if (r.ok) revalidatePath("/news");
  return r;
}

export async function updateNews(
  slug: string,
  body: NewsUpdateBody,
): Promise<ActionResult<News>> {
  await requireAdminRole("editor");
  const r = await adminFetch<News>(
    `/news/${encodeURIComponent(slug)}`,
    "PUT",
    body,
  );
  if (r.ok) revalidatePath("/news");
  return r;
}

export async function publishNews(slug: string): Promise<ActionResult<News>> {
  await requireAdminRole("editor");
  const r = await adminFetch<News>(
    `/news/${encodeURIComponent(slug)}/publish`,
    "POST",
  );
  if (r.ok) revalidatePath("/news");
  return r;
}

// --- Импорт сети ------------------------------------------------------------

export async function getImportJobs(
  page = 0,
  size = ADMIN_PAGE_SIZE,
): Promise<ReadResult<ImportPage>> {
  return adminRead<ImportPage>(`/imports${pageQuery(page, size)}`);
}

export async function getImportErrors(id: string): Promise<ReadResult<ImportError[]>> {
  return adminRead<ImportError[]>(`/imports/${encodeURIComponent(id)}/errors`);
}

// --- Календарные исключения -------------------------------------------------

export async function getCalendarExceptions(): Promise<ReadResult<CalendarException[]>> {
  return adminRead<CalendarException[]>("/calendar-exceptions");
}

export async function createCalendarException(
  body: CalendarExceptionBody,
): Promise<ActionResult<CalendarException>> {
  await requireAdminRole("editor");
  const result = await adminFetch<CalendarException>("/calendar-exceptions", "POST", body);
  if (result.ok) revalidatePath("/calendar");
  return result;
}

export async function updateCalendarException(
  id: number,
  body: CalendarExceptionBody,
): Promise<ActionResult<CalendarException>> {
  await requireAdminRole("editor");
  const result = await adminFetch<CalendarException>(
    `/calendar-exceptions/${id}`,
    "PUT",
    body,
  );
  if (result.ok) revalidatePath("/calendar");
  return result;
}

export async function deleteCalendarException(id: number): Promise<ActionResult<null>> {
  await requireAdminRole("editor");
  const result = await adminFetch<null>(`/calendar-exceptions/${id}`, "DELETE");
  if (result.ok) revalidatePath("/calendar");
  return result;
}

// --- Feature flags ----------------------------------------------------------

export async function getFeatureFlags(): Promise<ReadResult<FeatureFlag[]>> {
  return adminRead<FeatureFlag[]>("/feature-flags");
}

export async function setFeatureFlag(
  flagKey: string,
  enabled: boolean,
): Promise<ActionResult<null>> {
  await requireAdminRole("superadmin");
  const result = await adminFetch<null>(
    `/feature-flags/${encodeURIComponent(flagKey)}`,
    "PUT",
    { enabled },
  );
  if (result.ok) revalidatePath("/features");
  return result;
}

// --- Обращения граждан ------------------------------------------------------

export async function getCitizenRequests(): Promise<ReadResult<CitizenRequestAdmin[]>> {
  return adminRead<CitizenRequestAdmin[]>("/requests");
}

export async function updateCitizenRequest(
  code: string,
  body: CitizenRequestUpdateBody,
): Promise<ActionResult<CitizenRequestAdmin>> {
  await requireAdminRole("operator");
  const result = await adminFetch<CitizenRequestAdmin>(
    `/requests/${encodeURIComponent(code)}`,
    "PUT",
    body,
  );
  if (result.ok) revalidatePath("/requests");
  return result;
}

// --- Тарифы -----------------------------------------------------------------

export async function getFareProducts(): Promise<ReadResult<FareProduct[]>> {
  return adminRead<FareProduct[]>("/fares");
}

export async function createFareProduct(
  body: FareCreateBody,
): Promise<ActionResult<FareProduct>> {
  await requireAdminRole("editor");
  const result = await adminFetch<FareProduct>("/fares", "POST", body);
  if (result.ok) revalidatePath("/fares");
  return result;
}

export async function updateFareProduct(
  code: string,
  body: FareUpdateBody,
): Promise<ActionResult<FareProduct>> {
  await requireAdminRole("editor");
  const result = await adminFetch<FareProduct>(
    `/fares/${encodeURIComponent(code)}`,
    "PUT",
    body,
  );
  if (result.ok) revalidatePath("/fares");
  return result;
}

export async function deleteFareProduct(code: string): Promise<ActionResult<null>> {
  await requireAdminRole("editor");
  const result = await adminFetch<null>(`/fares/${encodeURIComponent(code)}`, "DELETE");
  if (result.ok) revalidatePath("/fares");
  return result;
}

// --- Инциденты --------------------------------------------------------------

/** Инциденты ведёт операционный контур: viewer их только читает. */
export async function getIncidents(
  status?: string,
): Promise<ReadResult<Incident[]>> {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  return adminRead<Incident[]>(`/incidents${query}`);
}

export async function getIncidentStats(): Promise<ReadResult<IncidentStats>> {
  return adminRead<IncidentStats>("/incidents/stats");
}

export async function createIncident(
  body: IncidentCreateBody,
): Promise<ActionResult<Incident>> {
  await requireAdminRole("operator");
  const result = await adminFetch<Incident>("/incidents", "POST", body);
  if (result.ok) revalidatePath("/incidents");
  return result;
}

export async function updateIncident(
  code: string,
  body: IncidentUpdateBody,
): Promise<ActionResult<Incident>> {
  await requireAdminRole("operator");
  const result = await adminFetch<Incident>(
    `/incidents/${encodeURIComponent(code)}`,
    "PUT",
    body,
  );
  if (result.ok) revalidatePath("/incidents");
  return result;
}

/**
 * Перевод инцидента по workflow. Допустимость перехода решает backend —
 * консоль лишь показывает allowedTransitions из карточки.
 */
export async function transitionIncident(
  code: string,
  body: IncidentTransitionBody,
): Promise<ActionResult<Incident>> {
  await requireAdminRole("operator");
  const result = await adminFetch<Incident>(
    `/incidents/${encodeURIComponent(code)}/transition`,
    "POST",
    body,
  );
  if (result.ok) revalidatePath("/incidents");
  return result;
}

// --- Рассылки (NTF-01…06) ---------------------------------------------------

/**
 * Рассылки ведёт дежурная смена: роль operator (зеркало AdminKeyAuthFilter,
 * префикс /v1/admin/notifications). Шаблоны — ниже, они редакционные (editor).
 */
/**
 * Лента рассылок (NTF-01) страницей. Фильтр по статусу считает backend, а не
 * консоль: отфильтровать уже пришедшую страницу значило бы фильтровать 50 строк
 * из тысячи и показывать оператору не ту ленту, которую он выбрал.
 */
export async function getNotifications(
  status?: string,
  page = 0,
  size = ADMIN_PAGE_SIZE,
): Promise<ReadResult<NotificationPage>> {
  const filter = status ? `&status=${encodeURIComponent(status)}` : "";
  return adminRead<NotificationPage>(`/notifications${pageQuery(page, size)}${filter}`);
}

export async function getNotificationDeliveries(
  code: string,
  page = 0,
  size = ADMIN_PAGE_SIZE,
): Promise<ReadResult<NotificationDeliveryPage>> {
  return adminRead<NotificationDeliveryPage>(
    `/notifications/${encodeURIComponent(code)}/deliveries${pageQuery(page, size)}`,
  );
}

/** Всё, что не доставлено: pending и failed (OPS-04). Выдача постраничная. */
export async function getNotificationProblemDeliveries(
  page = 0,
  size = ADMIN_PAGE_SIZE,
): Promise<ReadResult<NotificationDeliveryPage>> {
  return adminRead<NotificationDeliveryPage>(
    `/notifications/deliveries/problems${pageQuery(page, size)}`,
  );
}

export async function createNotification(
  body: NotificationCreateBody,
): Promise<ActionResult<Notification>> {
  await requireAdminRole("operator");
  const result = await adminFetch<Notification>("/notifications", "POST", body);
  if (result.ok) revalidatePath("/notifications");
  return result;
}

/**
 * Правка рассылки. Замороженную (sending/sent/cancelled) backend не даст
 * изменить — 400 notification.frozen; консоль гасит кнопку заранее, но
 * решение всё равно остаётся за сервером.
 */
export async function updateNotification(
  code: string,
  body: NotificationUpdateBody,
): Promise<ActionResult<Notification>> {
  await requireAdminRole("operator");
  const result = await adminFetch<Notification>(
    `/notifications/${encodeURIComponent(code)}`,
    "PUT",
    body,
  );
  if (result.ok) revalidatePath("/notifications");
  return result;
}

/** Перевод по карте NotificationStatus.TRANSITIONS; sent — только через send. */
export async function changeNotificationStatus(
  code: string,
  status: string,
): Promise<ActionResult<Notification>> {
  await requireAdminRole("operator");
  const result = await adminFetch<Notification>(
    `/notifications/${encodeURIComponent(code)}/status`,
    "POST",
    { status },
  );
  if (result.ok) revalidatePath("/notifications");
  return result;
}

/**
 * Отправка: draft/scheduled → sending → sent, создаёт доставку на каждого
 * получателя × канал. Реально уходит только in_app; push/email/sms имитируются.
 */
export async function sendNotification(
  code: string,
): Promise<ActionResult<Notification>> {
  await requireAdminRole("operator");
  const result = await adminFetch<Notification>(
    `/notifications/${encodeURIComponent(code)}/send`,
    "POST",
  );
  if (result.ok) revalidatePath("/notifications");
  return result;
}

/**
 * Повтор проваленной доставки: failed → pending. Повтор ставит доставку обратно
 * в очередь, а не объявляет её отправленной. {id} — uuid (своего code у неё нет).
 */
export async function retryNotificationDelivery(
  id: string,
): Promise<ActionResult<NotificationDelivery>> {
  await requireAdminRole("operator");
  const result = await adminFetch<NotificationDelivery>(
    `/notifications/deliveries/${encodeURIComponent(id)}/retry`,
    "POST",
  );
  if (result.ok) revalidatePath("/notifications");
  return result;
}

// --- Шаблоны рассылок (NTF-05) ----------------------------------------------

/**
 * Шаблоны — редакционный справочник: роль editor. Префикс
 * /v1/admin/notification-templates НЕ попадает под /v1/admin/notifications
 * (расходятся на '-' против 's'), поэтому backend требует здесь EDITOR.
 */
export async function getNotificationTemplates(): Promise<
  ReadResult<NotificationTemplate[]>
> {
  return adminRead<NotificationTemplate[]>("/notification-templates");
}

export async function createNotificationTemplate(
  body: NotificationTemplateCreateBody,
): Promise<ActionResult<NotificationTemplate>> {
  await requireAdminRole("editor");
  const result = await adminFetch<NotificationTemplate>(
    "/notification-templates",
    "POST",
    body,
  );
  if (result.ok) revalidatePath("/notifications");
  return result;
}

/** Правка шаблона не трогает уже созданные из него рассылки: их тексты — копия. */
export async function updateNotificationTemplate(
  code: string,
  body: NotificationTemplateUpdateBody,
): Promise<ActionResult<NotificationTemplate>> {
  await requireAdminRole("editor");
  const result = await adminFetch<NotificationTemplate>(
    `/notification-templates/${encodeURIComponent(code)}`,
    "PUT",
    body,
  );
  if (result.ok) revalidatePath("/notifications");
  return result;
}

export async function deleteNotificationTemplate(
  code: string,
): Promise<ActionResult<null>> {
  await requireAdminRole("editor");
  const result = await adminFetch<null>(
    `/notification-templates/${encodeURIComponent(code)}`,
    "DELETE",
  );
  if (result.ok) revalidatePath("/notifications");
  return result;
}

// --- Билеты, платежи, чёрный список (TKT-03/05/06) --------------------------

/**
 * Билеты и платежи — контур кассы и дежурного: роль operator (зеркало
 * AdminKeyAuthFilter для /tickets, /payments, /blocklist).
 */
export async function getTickets(status?: string): Promise<ReadResult<Ticket[]>> {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  return adminRead<Ticket[]>(`/tickets${query}`);
}

export async function getPayments(
  status?: string,
): Promise<ReadResult<Payment[]>> {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  return adminRead<Payment[]>(`/payments${query}`);
}

export async function getBlocklist(
  subjectType?: string,
): Promise<ReadResult<BlocklistEntry[]>> {
  const query = subjectType
    ? `?subjectType=${encodeURIComponent(subjectType)}`
    : "";
  return adminRead<BlocklistEntry[]>(`/blocklist${query}`);
}

/**
 * Ручной возврат: в отличие от публичного допускает уже использованный билет
 * (сбой турникета, решение по жалобе). Операция именная и попадает в аудит.
 */
export async function refundTicket(
  code: string,
  body: TicketRefundBody,
): Promise<ActionResult<Refund>> {
  await requireAdminRole("operator");
  const result = await adminFetch<Refund>(
    `/tickets/${encodeURIComponent(code)}/refund`,
    "POST",
    body,
  );
  if (result.ok) revalidatePath("/tickets");
  return result;
}

/**
 * Блокировка применяется немедленно: билеты субъекта переводятся в blocked.
 * Для subjectType=token сюда идёт САМ токен — сервис сохранит только SHA-256.
 */
export async function addToBlocklist(
  body: BlocklistCreateBody,
): Promise<ActionResult<BlocklistEntry>> {
  await requireAdminRole("operator");
  const result = await adminFetch<BlocklistEntry>("/blocklist", "POST", body);
  if (result.ok) revalidatePath("/tickets");
  return result;
}

/**
 * Снятие блокировки открывает субъекту покупку новых билетов, но уже
 * заблокированные билеты не восстанавливает: их токены скомпрометированы.
 */
export async function removeFromBlocklist(
  code: string,
): Promise<ActionResult<null>> {
  await requireAdminRole("operator");
  const result = await adminFetch<null>(
    `/blocklist/${encodeURIComponent(code)}`,
    "DELETE",
  );
  if (result.ok) revalidatePath("/tickets");
  return result;
}

// --- Вебхуки: подписчики и очередь доставок (ADM-06, U-OPS-04) --------------

export async function getWebhookSubscriptions(): Promise<
  ReadResult<WebhookSubscription[]>
> {
  return adminRead<WebhookSubscription[]>("/webhooks");
}

/**
 * Без фильтра backend отдаёт только требующие внимания: failed и dead (DLQ).
 * Выдача постраничная — очередь растёт как события × подписчики.
 */
export async function getWebhookDeliveries(
  status?: string,
  page = 0,
  size = ADMIN_PAGE_SIZE,
): Promise<ReadResult<WebhookDeliveryPage>> {
  const filter = status ? `&status=${encodeURIComponent(status)}` : "";
  return adminRead<WebhookDeliveryPage>(
    `/webhooks/deliveries${pageQuery(page, size)}${filter}`,
  );
}

/**
 * Подписчик — это внешний адрес, куда уходят данные сети, плюс секрет подписи
 * и лимиты: заводить их вправе только суперадмин.
 */
export async function createWebhookSubscription(
  body: WebhookCreateBody,
): Promise<ActionResult<WebhookSecret>> {
  await requireAdminRole("superadmin");
  const result = await adminFetch<WebhookSecret>("/webhooks", "POST", body);
  if (result.ok) revalidatePath("/webhooks");
  return result;
}

/** Секрет этой операцией не меняется — только ротацией. */
export async function updateWebhookSubscription(
  code: string,
  body: WebhookUpdateBody,
): Promise<ActionResult<WebhookSubscription>> {
  await requireAdminRole("superadmin");
  const result = await adminFetch<WebhookSubscription>(
    `/webhooks/${encodeURIComponent(code)}`,
    "PUT",
    body,
  );
  if (result.ok) revalidatePath("/webhooks");
  return result;
}

/**
 * Ротация: новый секрет виден ОДИН раз. Старый перестаёт действовать немедленно —
 * подписчик будет отвергать доставки, пока не обновит ключ у себя.
 */
export async function rotateWebhookSecret(
  code: string,
): Promise<ActionResult<WebhookSecret>> {
  await requireAdminRole("superadmin");
  const result = await adminFetch<WebhookSecret>(
    `/webhooks/${encodeURIComponent(code)}/secret`,
    "POST",
  );
  if (result.ok) revalidatePath("/webhooks");
  return result;
}

export async function deleteWebhookSubscription(
  code: string,
): Promise<ActionResult<null>> {
  await requireAdminRole("superadmin");
  const result = await adminFetch<null>(
    `/webhooks/${encodeURIComponent(code)}`,
    "DELETE",
  );
  if (result.ok) revalidatePath("/webhooks");
  return result;
}

/**
 * Ручной повтор доставки — рутина дежурной смены, а не владельца ключей:
 * роль operator, как и в AdminKeyAuthFilter (там /webhooks/deliveries
 * проверяется ДО /webhooks именно ради этого).
 */
export async function retryWebhookDelivery(
  code: string,
): Promise<ActionResult<WebhookDelivery>> {
  await requireAdminRole("operator");
  const result = await adminFetch<WebhookDelivery>(
    `/webhooks/deliveries/${encodeURIComponent(code)}/retry`,
    "POST",
  );
  if (result.ok) revalidatePath("/webhooks");
  return result;
}

// --- Операторы консоли ------------------------------------------------------

/**
 * Управление операторами доступно только суперадмину. Backend пропускает любой
 * вызов с корректным X-Admin-Key, поэтому роль обязана проверяться здесь —
 * requireAdminRole не пускает дальше и обычный adminFetch не вызывается.
 */
export async function getAdminUsers(): Promise<ReadResult<AdminUserAccount[]>> {
  await requireAdminRole("superadmin");
  return adminRead<AdminUserAccount[]>("/users");
}

export async function createAdminUser(
  body: AdminUserCreateBody,
): Promise<ActionResult<AdminUserAccount>> {
  await requireAdminRole("superadmin");
  const result = await adminFetch<AdminUserAccount>("/users", "POST", body);
  if (result.ok) revalidatePath("/users");
  return result;
}

export async function updateAdminUser(
  username: string,
  body: AdminUserUpdateBody,
): Promise<ActionResult<AdminUserAccount>> {
  await requireAdminRole("superadmin");
  const result = await adminFetch<AdminUserAccount>(
    `/users/${encodeURIComponent(username)}`,
    "PUT",
    body,
  );
  if (result.ok) revalidatePath("/users");
  return result;
}

export async function deleteAdminUser(
  username: string,
): Promise<ActionResult<null>> {
  await requireAdminRole("superadmin");
  const result = await adminFetch<null>(
    `/users/${encodeURIComponent(username)}`,
    "DELETE",
  );
  if (result.ok) revalidatePath("/users");
  return result;
}

// --- Аудит (чтение) ---------------------------------------------------------

/**
 * GET /admin/audit — лента событий аудита. Эндпоинт под /v1/admin/** защищён
 * X-Admin-Key (AdminKeyAuthFilter), поэтому читаем ТОЛЬКО серверно, с ключом.
 * Возвращает форму { data, error } для таблицы (не ActionResult).
 */
export async function getAuditEvents(): Promise<ReadResult<AuditEvent[]>> {
  const session = await requireAdminSession();
  const adminKey = adminApiKey();
  const actorToken = await createActorToken(session.username, session.accountVersion);
  try {
    const res = await fetch(`${API_BASE}/admin/audit`, {
      cache: "no-store",
      headers: {
        Accept: "application/json",
        "X-Admin-Key": adminKey,
        "X-Admin-Actor": session.username,
        "X-Admin-Actor-Token": actorToken,
      },
      signal: AbortSignal.timeout(ADMIN_FETCH_TIMEOUT_MS),
    });
    if (!res.ok) {
      return { data: null, error: `HTTP ${res.status} ${res.statusText}`.trim() };
    }
    const payload = (await res.json()) as { items?: AuditEvent[] };
    return { data: payload.items ?? [], error: null };
  } catch (e) {
    return { data: null, error: e instanceof Error ? e.message : String(e) };
  }
}

// --- AI-чат (проксируется серверно) -----------------------------------------

/**
 * POST /ai/chat — вопрос агенту. Выполняется СЕРВЕРНО (Server Action): из браузера
 * NEXT_PUBLIC_API_BASE в контейнерном деплое указывает на внутреннее имя backend
 * (http://backend:8080), недостижимое из браузера. Клиентская панель вызывает эту
 * функцию, а fetch к backend идёт с сервера Next.
 */
export async function sendAiChat(
  request: AiChatRequest,
): Promise<ReadResult<AiChatResponse>> {
  await requireAdminSession();
  try {
    const res = await fetch(`${API_BASE}/ai/chat`, {
      method: "POST",
      cache: "no-store",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify(request),
      signal: AbortSignal.timeout(30_000),
    });
    if (!res.ok) {
      return { data: null, error: `HTTP ${res.status} ${res.statusText}`.trim() };
    }
    return { data: (await res.json()) as AiChatResponse, error: null };
  } catch (e) {
    return { data: null, error: e instanceof Error ? e.message : String(e) };
  }
}
