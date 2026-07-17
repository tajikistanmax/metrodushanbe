/**
 * Доступ к контуру билетов (U-CIT-08, TKT-02/03/04).
 *
 * ДЕМО-КОНТУР. Реального эквайринга нет — платежи имитирует DemoPaymentGateway.
 * Ни одна функция здесь не принимает карточных данных: их не принимает и
 * backend (см. javadoc PaymentGateway). Если такой параметр появится в этом
 * файле — это ошибка, а не фича.
 *
 * Офлайн-деградации в стиле fare-data.ts здесь НЕТ намеренно: покупка билета —
 * операция записи. Показать пассажиру «билет куплен» из локального фолбэка
 * значило бы соврать, поэтому без сети операция обязана честно провалиться, а
 * экран — объяснить почему (см. TicketsClient).
 */

import { ApiHttpError, API_BASE, API_TIMEOUT_MS, fetchApiJson } from "./api";
import type {
  Ticket,
  TicketPurchaseBody,
  TicketPurchaseResult,
  TicketRefund,
  TicketTopUpResult,
  TicketValidation,
} from "./types";

/** Таймаут операций записи: втрое больше чтения — как в request-data.ts. */
const WRITE_TIMEOUT_MS = API_TIMEOUT_MS * 3;

/**
 * HTTP 402: платёж отклонён провайдером. Это штатный ответ с телом, а не сбой,
 * поэтому он не превращается в ошибку — вызывающий код разбирает payment.
 */
const HTTP_PAYMENT_REQUIRED = 402;

/**
 * Ошибка API с машиночитаемым кодом из единого envelope
 * ({error:{code, message}}, dev-conventions.md §3). Код нужен экрану, чтобы
 * показать локализованное сообщение, а не транслировать текст backend: тексты
 * envelope только на русском.
 */
export class TicketApiError extends Error {
  constructor(
    /** Например «ticketing.fare_inactive», «ticket.not_found». */
    readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "TicketApiError";
  }
}

/** Ошибка транспорта: сети нет, таймаут, битый JSON. Кода у неё нет. */
export class TicketNetworkError extends Error {
  constructor() {
    super("Ticketing API unreachable");
    this.name = "TicketNetworkError";
  }
}

/**
 * POST с таймаутом. Не-2xx превращается в TicketApiError с кодом из envelope,
 * КРОМЕ 402: его тело — валидный ответ об отклонённом платеже.
 */
async function postJson<T>(path: string, body: unknown): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), WRITE_TIMEOUT_MS);
  let response: Response;
  let payload: unknown;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method: "POST",
      cache: "no-store",
      signal: controller.signal,
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify(body),
    });
    payload = (await response.json()) as unknown;
  } catch {
    // Сеть/таймаут/битый JSON — код ошибки назвать нечем
    throw new TicketNetworkError();
  } finally {
    clearTimeout(timer);
  }

  if (!response.ok && response.status !== HTTP_PAYMENT_REQUIRED) {
    const envelope = payload as { error?: { code?: string; message?: string } };
    throw new TicketApiError(
      envelope.error?.code ?? `http.${response.status}`,
      envelope.error?.message ?? `HTTP ${response.status}`,
    );
  }
  return payload as T;
}

/**
 * Покупка билета. Токен возвращается ровно один раз — в поле token результата;
 * восстановить его потом невозможно.
 *
 * Отказ платежа (HTTP 402) — обычный результат: ticket и token равны null,
 * payment.status = "failed", причина в payment.failureReason.
 */
export function purchaseTicket(
  body: TicketPurchaseBody,
): Promise<TicketPurchaseResult> {
  return postJson<TicketPurchaseResult>("/tickets/purchase", body);
}

/** Пополнение проездного. Отказ платежа — тоже штатный результат (402). */
export function topUpTicket(
  code: string,
  amount: number,
  demoScenario?: string,
): Promise<TicketTopUpResult> {
  return postJson<TicketTopUpResult>(
    `/tickets/${encodeURIComponent(code.trim())}/topup`,
    { amount, demoScenario },
  );
}

/**
 * Предъявление билета по токену. Всегда 200: недействительный билет — это
 * valid=false с причиной в reason, а не ошибка запроса.
 */
export function validateTicket(token: string): Promise<TicketValidation> {
  return postJson<TicketValidation>("/tickets/validate", { token: token.trim() });
}

/** Возврат по заявлению пассажира. Основание обязательно. */
export function refundTicket(
  code: string,
  reason: string,
): Promise<TicketRefund> {
  return postJson<TicketRefund>(
    `/tickets/${encodeURIComponent(code.trim())}/refund`,
    { reason: reason.trim() },
  );
}

/** Код envelope, которым backend отвечает на неизвестный номер билета. */
const TICKET_NOT_FOUND = "ticket.not_found";

/**
 * Билета с таким номером нет — в отличие от «сервер не ответил». Разные беды с
 * разными действиями пассажира: тут — проверить номер, там — повторить позже.
 */
export function isTicketNotFound(error: unknown): boolean {
  return error instanceof TicketApiError && error.code === TICKET_NOT_FOUND;
}

/**
 * Публичный статус билета по коду. Токен здесь не возвращается никогда.
 *
 * 404 и обрыв сети РАЗВЕДЕНЫ. Раньше здесь всё сваливалось в
 * TicketNetworkError, и пассажир получал один текст «проверьте номер и
 * соединение» — бесполезный в обоих случаях. Статус берётся из ApiHttpError,
 * который fetchApiJson бросает и без того (общий хелпер не менялся — его читают
 * и загрузчики сети, новостей, тарифов). Тела ошибки у GET нет, поэтому код
 * envelope восстанавливается по статусу; не-404 остаётся `http.{status}` и
 * показывается общим текстом.
 */
export async function loadTicket(code: string): Promise<Ticket> {
  try {
    return (await fetchApiJson(
      `/tickets/${encodeURIComponent(code.trim())}`,
    )) as Ticket;
  } catch (error) {
    if (error instanceof ApiHttpError) {
      throw new TicketApiError(
        error.status === 404 ? TICKET_NOT_FOUND : `http.${error.status}`,
        error.message,
      );
    }
    // Сеть, таймаут, битый JSON — сервер не ответил, про билет ничего не известно
    throw new TicketNetworkError();
  }
}
