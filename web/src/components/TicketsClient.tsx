"use client";

/**
 * Билеты (U-CIT-08, TKT-02/03/04): покупка, пополнение, статус, возврат и
 * проверка билета.
 *
 * ДЕМО-КОНТУР — ГЛАВНОЕ ПРО ЭТОТ ЭКРАН. Реального эквайринга нет: платежи
 * имитирует DemoPaymentGateway, и каждый выпущенный билет помечен demo=true.
 * Поэтому предупреждение показывается ТРИЖДЫ и в разных местах:
 *   1) врезка над всеми действиями (её нельзя закрыть — в отличие от DemoBanner,
 *      это не «схема не утверждена», а «денег не списывается»);
 *   2) бейдж «Демо» на самом билете и в результате проверки — там, где пассажир
 *      смотрит на результат, а не на страницу;
 *   3) текст на карточке выпущенного билета.
 * Молча показать «билет куплен» недопустимо. Тот же приём, что у demo-цен в
 * FaresClient и demo-данных в DemoBanner.
 *
 * Карточные данные не собираются вообще: их не принимает и backend (см. javadoc
 * PaymentGateway). Полей карты в этой форме нет и быть не должно.
 *
 * ОФЛАЙН (dev-conventions.md §8). Покупка/пополнение/проверка/возврат — операции
 * на сервере, локального фолбэка у них быть не может: показать «билет куплен» из
 * кеша значило бы соврать. Поэтому без сети экран честно объявляет состояние и
 * блокирует действия, а не делает вид, что работает.
 */

import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from "react";
import { formatDushanbeDateTime } from "@/lib/date-time";
import { MAIN_CONTENT_ID } from "@/lib/dom-ids";
import { loadFares } from "@/lib/fare-data";
import { pickName } from "@/lib/i18n";
import {
  isTicketNotFound,
  loadTicket,
  purchaseTicket,
  refundTicket,
  TicketApiError,
  topUpTicket,
  validateTicket,
} from "@/lib/ticket-data";
import {
  forgetTicket,
  listSavedTickets,
  readTicketToken,
  rememberTicket,
  type SavedTicket,
} from "@/lib/ticket-storage";
import type {
  DataSource,
  FareProduct,
  Ticket,
  TicketPurchaseResult,
  TicketValidation,
} from "@/lib/types";
import { Alert, Badge, Button, Card, Container, Input, Select, Stack, Textarea } from "@/shared/ui";
import { useReportDataSource } from "./DataSourceProvider";
import { useI18n } from "./I18nProvider";

type Tab = "buy" | "list" | "manage" | "validate";

/**
 * «Мои билеты» — ВКЛАДКА, а не отдельная страница. Причины две.
 *
 * 1) Приватность. Отдельный маршрут вроде /tickets/my читался бы как личный
 *    кабинет, а его нет: список живёт в localStorage этого браузера, аккаунтов у
 *    портала не существует. Вкладка внутри «Билетов» честно говорит: это часть
 *    экрана билетов на ЭТОМ устройстве, а не ваш профиль на сервере.
 * 2) Демо-маркировка. Незакрываемая врезка «денег не списывается» висит над
 *    всеми вкладками разом. Отдельная страница обязана была бы повторить все три
 *    уровня пометки заново — и однажды кто-нибудь забыл бы это сделать.
 *
 * Побочная выгода: список и переходы «открыть билет» / «проверить билет»
 * остаются в одном состоянии, без прокидывания кода и токена через URL (токен в
 * адресной строке — прямая утечка одноразового секрета в историю браузера).
 * Поэтому ни sw.js, ни nav.ts не меняются: новых маршрутов нет.
 */
const TABS: readonly Tab[] = ["buy", "list", "manage", "validate"];

/** Пределы суммы пополнения — совпадают с @DecimalMin/@DecimalMax на backend. */
const TOPUP_MIN = 0.01;
const TOPUP_MAX = 10_000;

// --- Локальное хранение --------------------------------------------------

/**
 * Токен билета в localStorage — тот же приём, что с tracking-токеном обращения
 * (RequestsClient): сервер токен второй раз не отдаст, а пассажиру нужно чем-то
 * предъявлять билет. Хранение необязательно: приватный режим его отключает, и
 * тогда остаётся одноразовый показ токена на карточке. Ключи, реестр билетов и
 * миграция старого формата — в lib/ticket-storage.ts.
 */

/** Ключ непроверенного идентификатора устройства (riderRef для антифрода). */
const RIDER_REF_KEY = "metro-dushanbe.rider-ref";

/**
 * Идентификатор устройства для антифрода (TKT-06). Это НЕ персональные данные и
 * НЕ учётная запись: контура идентификации пассажиров ещё нет, а backend ждёт
 * непроверенный идентификатор приложения. Формат подогнан под @Pattern
 * (латиница, цифры, . _ : -).
 */
function riderRef(): string | undefined {
  try {
    const saved = window.localStorage.getItem(RIDER_REF_KEY);
    if (saved) return saved;
    const generated = `web-${crypto.randomUUID()}`;
    window.localStorage.setItem(RIDER_REF_KEY, generated);
    return generated;
  } catch {
    // Хранилище недоступно — покупка обойдётся без riderRef (поле необязательное)
    return undefined;
  }
}

// --- Состояние сети ------------------------------------------------------

function subscribeOnline(listener: () => void): () => void {
  window.addEventListener("online", listener);
  window.addEventListener("offline", listener);
  return () => {
    window.removeEventListener("online", listener);
    window.removeEventListener("offline", listener);
  };
}

/**
 * Есть ли сеть. SSR-снапшот — true: на сервере navigator нет, а рисовать
 * «нет соединения» при первом рендере значило бы пугать без причины.
 */
function useOnline(): boolean {
  return useSyncExternalStore(
    subscribeOnline,
    () => navigator.onLine,
    () => true,
  );
}

// --- Вспомогательное -----------------------------------------------------

/** Локализованное сообщение об ошибке по коду envelope; иначе — общий текст. */
function useErrorText(): (error: unknown) => string {
  const { dict } = useI18n();
  return (error: unknown) => {
    if (error instanceof TicketApiError) {
      return dict.tickets.errors[error.code] ?? dict.tickets.unknownError;
    }
    // TicketNetworkError и всё неопознанное: кода нет — общий текст
    return dict.tickets.unknownError;
  };
}

/** Тон бейджа статуса билета. Цвет дублирует подпись, а не заменяет её (SC 1.4.1). */
const STATUS_TONE = {
  issued: "info",
  active: "success",
  used: "neutral",
  expired: "neutral",
  refunded: "neutral",
  blocked: "critical",
} as const;

function DemoBadge() {
  const { dict } = useI18n();
  return <Badge tone="warning" dot>{dict.tickets.demoBadge}</Badge>;
}

/** Строка «подпись — значение» карточки билета. */
function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-caption font-semibold uppercase tracking-[0.08em] text-text-secondary">
        {label}
      </dt>
      <dd className="mt-1 text-small font-semibold">{children}</dd>
    </div>
  );
}

// --- Токен билета --------------------------------------------------------

/**
 * Одноразовый показ токена.
 *
 * QR (TKT-04) НЕ рисуется намеренно. Токен предназначен для QR, но внешние
 * библиотеки в проект не тянем, а собственный энкодер (Рид—Соломон, маскирование,
 * блоки формата) нечем проверить: сканеров и турникетов в контуре ещё нет, и
 * молча выдать битую картинку хуже, чем честный код текстом. Демо-билет всё
 * равно недействителен для проезда — до появления реальных турникетов ценность
 * QR нулевая. Поэтому здесь крупный моноширинный блок, а картинка появится
 * вместе с оборудованием, на котором её можно будет проверить.
 */
function TokenBlock({ token }: { token: string }) {
  const { dict } = useI18n();
  const [copied, setCopied] = useState<"idle" | "ok" | "fail">("idle");

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(token);
      setCopied("ok");
    } catch {
      setCopied("fail");
    }
  };

  return (
    <div>
      <p className="text-caption font-semibold uppercase tracking-[0.08em] text-text-secondary">
        {dict.tickets.tokenLabel}
      </p>
      <p className="mt-2 break-all rounded-control border border-[var(--border-strong)] bg-[var(--surface-sunken)] p-4 font-mono text-lead font-bold leading-relaxed">
        {token}
      </p>
      <p className="mt-2 text-caption text-text-secondary">{dict.tickets.tokenHint}</p>
      <p className="mt-1 text-caption text-text-secondary">{dict.tickets.tokenQrNote}</p>
      <Stack direction="horizontal" gap={3} align="center" wrap className="mt-3">
        <Button variant="primary" onClick={copy}>
          {copied === "ok" ? dict.tickets.copied : dict.tickets.copy}
        </Button>
        {copied === "fail" ? (
          <span role="alert" className="text-caption font-semibold text-brand-red">
            {dict.tickets.copyFailed}
          </span>
        ) : null}
      </Stack>
    </div>
  );
}

// --- Карточка выпущенного билета ----------------------------------------

function IssuedCard({
  result,
  onReset,
}: {
  result: TicketPurchaseResult;
  onReset: () => void;
}) {
  const { dict } = useI18n();
  const ticket = result.ticket;
  if (!ticket) return null;

  return (
    <Stack gap={4} className="mt-5">
      <Alert tone="success" label={dict.tickets.issuedLabel} heading={dict.tickets.issuedTitle}>
        {dict.tickets.issuedBody}
      </Alert>
      {/* Предупреждение о токене — критическим тоном: восстановить его нельзя */}
      <Alert tone="warning" label={dict.tickets.demoLabel} heading={dict.tickets.tokenWarning} live={false}>
        {dict.tickets.demoBody}
      </Alert>
      <Card heading={dict.tickets.issuedTitle} actions={<DemoBadge />}>
        <Stack gap={5}>
          <Row label={dict.tickets.ticketCode}>
            <span className="font-mono text-title-s">{ticket.code}</span>
          </Row>
          <TokenBlock token={result.token ?? ""} />
        </Stack>
      </Card>
      <div>
        <Button onClick={onReset}>{dict.tickets.buyAnother}</Button>
      </div>
    </Stack>
  );
}

// --- Деньги --------------------------------------------------------------

/** Форматирование цены по языку интерфейса. Общее для карточки и списка. */
function useMoney(): (amount: number, currency: string) => string {
  const { lang } = useI18n();
  return (amount: number, currency: string) =>
    new Intl.NumberFormat(lang === "tg" ? "tg-TJ" : lang === "ru" ? "ru-RU" : "en-GB", {
      style: "currency",
      currency,
      minimumFractionDigits: 2,
    }).format(amount);
}

// --- Карточка билета (статус) -------------------------------------------

function TicketCard({ ticket }: { ticket: Ticket }) {
  const { lang, dict } = useI18n();
  const money = useMoney();

  return (
    <Card
      heading={dict.tickets.manageTitle}
      actions={ticket.demo ? <DemoBadge /> : null}
      className="mt-5"
    >
      <Stack direction="horizontal" gap={3} align="center" wrap className="mb-4">
        <span className="font-mono text-title-s font-bold">{ticket.code}</span>
        <Badge tone={STATUS_TONE[ticket.status]} dot>
          {dict.tickets.statuses[ticket.status]}
        </Badge>
      </Stack>
      <dl className="grid gap-4 sm:grid-cols-2">
        <Row label={dict.tickets.fare}>{ticket.fareProductCode}</Row>
        <Row label={dict.tickets.kind}>{dict.tickets.kinds[ticket.kind]}</Row>
        <Row label={dict.tickets.category}>{dict.fares.categories[ticket.riderCategory]}</Row>
        <Row label={dict.tickets.price}>{money(ticket.priceAmount, ticket.priceCurrency)}</Row>
        {ticket.balanceAmount !== null ? (
          <Row label={dict.tickets.balance}>
            {money(ticket.balanceAmount, ticket.priceCurrency)}
          </Row>
        ) : null}
        <Row label={dict.tickets.validFrom}>
          {formatDushanbeDateTime(ticket.validFrom, lang)}
        </Row>
        <Row label={dict.tickets.validUntil}>
          {formatDushanbeDateTime(ticket.validUntil, lang)}
        </Row>
        {ticket.usedAt ? (
          <Row label={dict.tickets.usedAt}>
            {formatDushanbeDateTime(ticket.usedAt, lang)}
          </Row>
        ) : null}
      </dl>
    </Card>
  );
}

// --- Мои билеты ----------------------------------------------------------

/**
 * Что известно про статус билета из списка.
 *
 * «Неизвестно» — полноценное состояние, а не отсутствие данных: без сети или
 * при молчащем сервере показать вчерашний статус значило бы соврать (билет мог
 * быть погашен, просрочен, возвращён). Поэтому старый статус не хранится вообще.
 */
type ListState = "ok" | "missing" | "unreachable" | "offline";

type ListEntry = {
  saved: SavedTicket;
  state: ListState;
  /** Заполнен только при state = "ok". */
  ticket: Ticket | null;
};

/**
 * Список билетов устройства со статусами. Сам список читается из localStorage и
 * доступен всегда; статусы существуют только на сервере, поэтому офлайн честно
 * даёт state = "offline" вместо устаревшей отметки.
 */
async function loadSavedEntries(online: boolean): Promise<ListEntry[]> {
  const saved = listSavedTickets();
  if (!online) {
    return saved.map((item) => ({ saved: item, state: "offline", ticket: null }));
  }
  return Promise.all(
    saved.map(async (item): Promise<ListEntry> => {
      try {
        return { saved: item, state: "ok", ticket: await loadTicket(item.code) };
      } catch (error) {
        // Ровно то различие, которого раньше не было: 404 — билета с таким
        // номером нет; всё остальное — сервер не ответил.
        return {
          saved: item,
          state: isTicketNotFound(error) ? "missing" : "unreachable",
          ticket: null,
        };
      }
    }),
  );
}

/** Тон и подпись «статус неизвестен»: цвет не единственный носитель смысла. */
function ListStatusBadge({ entry }: { entry: ListEntry }) {
  const { dict } = useI18n();
  if (entry.state === "ok" && entry.ticket) {
    return (
      <Badge tone={STATUS_TONE[entry.ticket.status]} dot>
        {dict.tickets.statuses[entry.ticket.status]}
      </Badge>
    );
  }
  return (
    <Badge tone="neutral" dot>
      {dict.tickets.listStatusUnknown}
    </Badge>
  );
}

function SavedTicketCard({
  entry,
  online,
  onOpen,
  onCheck,
  onForget,
}: {
  entry: ListEntry;
  online: boolean;
  onOpen: (code: string) => void;
  onCheck: (code: string) => void;
  onForget: (code: string) => void;
}) {
  const { lang, dict } = useI18n();
  const money = useMoney();
  const [confirming, setConfirming] = useState(false);
  const ticket = entry.ticket;

  /** Почему статуса нет. Каждое состояние объясняется своим текстом. */
  const explanation =
    entry.state === "offline"
      ? dict.tickets.listStatusOffline
      : entry.state === "unreachable"
        ? dict.tickets.listStatusUnreachable
        : entry.state === "missing"
          ? dict.tickets.listStatusMissing
          : null;

  return (
    <Card as="li" padding="lg">
      <Stack gap={4}>
        <Stack direction="horizontal" gap={3} align="center" justify="between" wrap>
          <span className="font-mono text-title-s font-bold">{entry.saved.code}</span>
          <Stack direction="horizontal" gap={2} align="center" wrap>
            <ListStatusBadge entry={entry} />
            {/* Бейдж «Демо» — безусловно: другого контура у портала нет, и
                билет обязан читаться как демонстрационный даже до ответа
                сервера, когда ticket.demo ещё неизвестен. */}
            <DemoBadge />
          </Stack>
        </Stack>

        {ticket ? (
          <dl className="grid gap-4 sm:grid-cols-2">
            <Row label={dict.tickets.kind}>{dict.tickets.kinds[ticket.kind]}</Row>
            <Row label={dict.tickets.price}>
              {money(ticket.priceAmount, ticket.priceCurrency)}
            </Row>
            <Row label={dict.tickets.validUntil}>
              {formatDushanbeDateTime(ticket.validUntil, lang)}
            </Row>
            {ticket.balanceAmount !== null ? (
              <Row label={dict.tickets.balance}>
                {money(ticket.balanceAmount, ticket.priceCurrency)}
              </Row>
            ) : null}
          </dl>
        ) : null}

        {explanation ? (
          <Alert
            tone={entry.state === "missing" ? "warning" : "info"}
            label={dict.tickets.listStatusUnknown}
            live={false}
          >
            {explanation}
          </Alert>
        ) : null}

        <p className="text-caption text-text-secondary">
          {dict.tickets.listSavedAt}:{" "}
          {entry.saved.savedAt
            ? formatDushanbeDateTime(entry.saved.savedAt, lang)
            : dict.tickets.listSavedAtUnknown}
        </p>
        <p className="text-caption text-text-secondary">{dict.tickets.listDemoNote}</p>

        {confirming ? (
          // Удаление объясняется до, а не после: «убрать» и «вернуть» —
          // разные операции, и путать их пассажиру дорого.
          <Alert tone="warning" label={dict.tickets.listRemove} heading={dict.tickets.listRemoveTitle}>
            <p>{dict.tickets.listRemoveBody}</p>
            <Stack direction="horizontal" gap={3} align="center" wrap className="mt-3">
              <Button variant="danger" size="sm" onClick={() => onForget(entry.saved.code)}>
                {dict.tickets.listRemoveConfirm}
              </Button>
              <Button size="sm" onClick={() => setConfirming(false)}>
                {dict.tickets.listRemoveCancel}
              </Button>
            </Stack>
          </Alert>
        ) : (
          <Stack direction="horizontal" gap={3} align="center" wrap>
            <Button variant="primary" size="sm" disabled={!online} onClick={() => onOpen(entry.saved.code)}>
              {dict.tickets.listOpen}
            </Button>
            <Button
              size="sm"
              disabled={!online || !entry.saved.hasToken}
              onClick={() => onCheck(entry.saved.code)}
            >
              {dict.tickets.listCheck}
            </Button>
            <Button variant="ghost" size="sm" onClick={() => setConfirming(true)}>
              {dict.tickets.listRemove}
            </Button>
          </Stack>
        )}

        {!entry.saved.hasToken ? (
          <p className="text-caption text-text-secondary">{dict.tickets.listNoToken}</p>
        ) : null}
      </Stack>
    </Card>
  );
}

function MyTicketsPanel({
  online,
  onOpen,
  onCheck,
}: {
  online: boolean;
  onOpen: (code: string) => void;
  onCheck: (code: string) => void;
}) {
  const { dict } = useI18n();
  /** null — список ещё не прочитан (localStorage есть только после монтирования). */
  const [entries, setEntries] = useState<ListEntry[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [removed, setRemoved] = useState("");

  // Перечитываем и при появлении сети: сам список офлайн валиден, а вот
  // статусы к этому моменту неизвестны и должны быть запрошены заново.
  useEffect(() => {
    let cancelled = false;
    loadSavedEntries(online).then((loaded) => {
      if (!cancelled) setEntries(loaded);
    });
    return () => {
      cancelled = true;
    };
  }, [online]);

  /** Перечитать по кнопке или после удаления билета с устройства. */
  const refresh = useCallback(() => {
    setBusy(true);
    return loadSavedEntries(online).then((loaded) => {
      setEntries(loaded);
      setBusy(false);
    });
  }, [online]);

  const forget = (code: string) => {
    forgetTicket(code);
    setRemoved(dict.tickets.listRemoved);
    void refresh();
  };

  return (
    <Stack gap={4} className="mt-5">
      <Card heading={dict.tickets.listTitle} description={dict.tickets.listIntro}>
        <Stack gap={4}>
          {/* Приватность: список — это браузер, а не учётная запись */}
          <Alert
            tone="info"
            label={dict.tickets.listDeviceLabel}
            heading={dict.tickets.listDeviceTitle}
            live={false}
          >
            {dict.tickets.listDeviceBody}
          </Alert>
          <Stack direction="horizontal" gap={3} align="center" wrap>
            <Button size="sm" disabled={busy || !online} onClick={() => void refresh()}>
              {busy ? dict.tickets.listRefreshing : dict.tickets.listRefresh}
            </Button>
            {entries !== null && entries.length > 0 ? (
              <span className="text-caption text-text-secondary">
                {entries.length} · {dict.tickets.listCount}
              </span>
            ) : null}
          </Stack>
        </Stack>
      </Card>

      {/* Результат удаления объявляется отдельно от списка: сам список
          перерисовывается целиком и как live-регион был бы слишком болтлив. */}
      <div aria-live="polite">
        {removed ? (
          <Alert tone="success" label={dict.tickets.listRemovedLabel} live={false}>
            {removed}
          </Alert>
        ) : null}
      </div>

      <div aria-live="polite" role="region" aria-label={dict.tickets.listRegionLabel}>
        {entries !== null && entries.length > 0 ? (
          <ul className="grid gap-4">
            {entries.map((entry) => (
              <SavedTicketCard
                key={entry.saved.code}
                entry={entry}
                online={online}
                onOpen={onOpen}
                onCheck={onCheck}
                onForget={forget}
              />
            ))}
          </ul>
        ) : null}
        {entries !== null && entries.length === 0 ? (
          <Card heading={dict.tickets.listEmptyTitle}>
            <p className="max-w-[65ch] text-small leading-relaxed text-text-secondary">
              {storageAvailable() ? dict.tickets.listEmptyBody : dict.tickets.listStorageBlocked}
            </p>
          </Card>
        ) : null}
        {entries === null ? (
          <p className="text-small text-text-secondary">{dict.tickets.listStatusLoading}</p>
        ) : null}
      </div>
    </Stack>
  );
}

/** Разрешает ли браузер хранить данные: пустой список объясняется по-разному. */
function storageAvailable(): boolean {
  try {
    const probe = "metro-dushanbe.probe";
    window.localStorage.setItem(probe, "1");
    window.localStorage.removeItem(probe);
    return true;
  } catch {
    return false;
  }
}

// --- Экран ---------------------------------------------------------------

export default function TicketsClient() {
  const { lang, dict } = useI18n();
  const online = useOnline();
  const errorText = useErrorText();

  const [tab, setTab] = useState<Tab>("buy");
  const [fares, setFares] = useState<FareProduct[]>([]);
  const [source, setSource] = useState<DataSource | null>(null);
  const [fareCode, setFareCode] = useState("");
  const [scenario, setScenario] = useState("approve");
  const [buyBusy, setBuyBusy] = useState(false);
  const [buyError, setBuyError] = useState("");
  const [purchase, setPurchase] = useState<TicketPurchaseResult | null>(null);

  const [lookupCode, setLookupCode] = useState("");
  const [lookupBusy, setLookupBusy] = useState(false);
  const [lookupError, setLookupError] = useState("");
  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [topUpAmount, setTopUpAmount] = useState("");
  const [topUpBusy, setTopUpBusy] = useState(false);
  const [topUpError, setTopUpError] = useState("");
  const [topUpDone, setTopUpDone] = useState(false);
  const [refundReason, setRefundReason] = useState("");
  const [refundBusy, setRefundBusy] = useState(false);
  const [refundError, setRefundError] = useState("");
  const [refundDone, setRefundDone] = useState(false);

  const [token, setToken] = useState("");
  const [validateBusy, setValidateBusy] = useState(false);
  const [validateError, setValidateError] = useState("");
  const [validation, setValidation] = useState<TicketValidation | null>(null);

  useEffect(() => {
    let cancelled = false;
    loadFares().then((result) => {
      if (cancelled) return;
      const active = result.data.filter((fare) => fare.active);
      setFares(active);
      setSource(result.source);
      setFareCode((current) => current || (active[0]?.code ?? ""));
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const fareOptions = useMemo(
    () =>
      fares.map((fare) => ({
        value: fare.code,
        label: `${pickName(fare.name, lang)} · ${fare.amount} ${fare.currency}`,
      })),
    [fares, lang],
  );

  const buy = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (fareCode === "") {
      setBuyError(dict.tickets.required);
      return;
    }
    setBuyBusy(true);
    setBuyError("");
    try {
      const result = await purchaseTicket({
        fareProductCode: fareCode,
        riderRef: riderRef(),
        demoScenario: scenario,
      });
      setPurchase(result);
      // Отказ платежа (402) — тоже результат: ticket = null, причина в payment
      if (result.ticket && result.token) {
        setLookupCode(result.ticket.code);
        setToken(result.token);
        // Токен + запись в реестре устройства: билет попадёт в «мои билеты»
        rememberTicket(result.ticket.code, result.token);
      }
    } catch (error) {
      setBuyError(errorText(error));
    } finally {
      setBuyBusy(false);
    }
  };

  /**
   * Загрузка билета по коду. Вынесена из обработчика формы, потому что список
   * «мои билеты» открывает билет тем же путём, но без submit.
   */
  const runLookup = async (code: string) => {
    if (code.trim() === "") {
      setLookupError(dict.tickets.required);
      return;
    }
    setLookupBusy(true);
    setLookupError("");
    setTopUpDone(false);
    setRefundDone(false);
    setTopUpError("");
    setRefundError("");
    try {
      setTicket(await loadTicket(code));
    } catch (error) {
      setTicket(null);
      // 404 и обрыв сети — разные беды: «проверьте номер» против «повторите
      // позже». Один общий текст не помогал ни в одном из случаев.
      setLookupError(
        isTicketNotFound(error)
          ? dict.tickets.lookupNotFound
          : dict.tickets.lookupUnreachable,
      );
    } finally {
      setLookupBusy(false);
    }
  };

  const lookup = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await runLookup(lookupCode);
  };

  /** Открыть билет из списка: код в поле, вкладка статуса, запрос сразу. */
  const openFromList = (code: string) => {
    setLookupCode(code);
    setTab("manage");
    void runLookup(code);
  };

  /**
   * Проверить билет из списка. Токен берётся из хранилища и попадает только в
   * поле формы проверки — в самом списке он не показывается никогда.
   */
  const checkFromList = (code: string) => {
    const saved = readTicketToken(code);
    setTab("validate");
    setValidation(null);
    setValidateError("");
    if (saved === null) return;
    setToken(saved);
    void runCheck(saved);
  };

  const topUp = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!ticket) return;
    const amount = Number(topUpAmount.replace(",", "."));
    if (!Number.isFinite(amount) || amount < TOPUP_MIN || amount > TOPUP_MAX) {
      setTopUpError(dict.tickets.amountInvalid);
      return;
    }
    setTopUpBusy(true);
    setTopUpError("");
    setTopUpDone(false);
    try {
      const result = await topUpTicket(ticket.code, amount, scenario);
      setTicket(result.ticket);
      if (result.payment.status === "failed") {
        // 402: штатный отказ демо-эквайринга, а не сбой
        setTopUpError(dict.tickets.declinedTitle);
      } else {
        setTopUpDone(true);
        setTopUpAmount("");
      }
    } catch (error) {
      setTopUpError(errorText(error));
    } finally {
      setTopUpBusy(false);
    }
  };

  const refund = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!ticket) return;
    if (refundReason.trim() === "") {
      setRefundError(dict.tickets.required);
      return;
    }
    setRefundBusy(true);
    setRefundError("");
    setRefundDone(false);
    try {
      await refundTicket(ticket.code, refundReason);
      setRefundDone(true);
      setRefundReason("");
      // Возврат меняет статус билета — перечитываем публичную карточку
      try {
        setTicket(await loadTicket(ticket.code));
      } catch {
        // Билет уже показан; несостоявшееся обновление статуса не отменяет возврат
      }
    } catch (error) {
      setRefundError(errorText(error));
    } finally {
      setRefundBusy(false);
    }
  };

  async function runCheck(value: string) {
    if (value.trim() === "") {
      setValidateError(dict.tickets.required);
      return;
    }
    setValidateBusy(true);
    setValidateError("");
    setValidation(null);
    try {
      setValidation(await validateTicket(value));
    } catch (error) {
      setValidateError(errorText(error));
    } finally {
      setValidateBusy(false);
    }
  }

  const check = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await runCheck(token);
  };

  const declined = purchase !== null && purchase.ticket === null;
  const topUpAllowed = ticket?.kind === "pass";

  useReportDataSource(source);

  return (
      <main id={MAIN_CONTENT_ID} className="flex-1 py-8 sm:py-12">
        <Container width="narrow">
          <h1 className="text-title-l font-bold sm:text-title-xl">
            {dict.tickets.heading}
          </h1>
          <p className="mt-3 max-w-[65ch] text-lead text-text-secondary">
            {dict.tickets.intro}
          </p>

          {/* Пометка demo-контура над всеми действиями. Не закрывается: это не
              «схема не утверждена», а «денег не списывается». */}
          <Alert
            tone="warning"
            label={dict.tickets.demoLabel}
            heading={dict.tickets.demoTitle}
            live={false}
            className="mt-5"
          >
            {dict.tickets.demoBody}
          </Alert>

          {!online ? (
            <Alert
              tone="critical"
              label={dict.tickets.offlineLabel}
              heading={dict.tickets.offlineTitle}
              className="mt-4"
            >
              {dict.tickets.offlineBody}
            </Alert>
          ) : null}

          <div
            role="tablist"
            aria-label={dict.tickets.heading}
            className="mt-6 inline-flex gap-1 rounded-control border border-[var(--border-subtle)] p-1"
          >
            {TABS.map((value) => (
              <Button
                key={value}
                role="tab"
                aria-selected={tab === value}
                variant={tab === value ? "primary" : "ghost"}
                size="sm"
                onClick={() => setTab(value)}
              >
                {dict.tickets.tabs[value]}
              </Button>
            ))}
          </div>

          {tab === "buy" && !purchase && (
            <Card heading={dict.tickets.buyTitle} description={dict.tickets.buyIntro} className="mt-5">
              <form onSubmit={buy}>
                <Stack gap={4}>
                  <Select
                    label={dict.tickets.fareLabel}
                    required
                    value={fareCode}
                    onChange={(event) => setFareCode(event.target.value)}
                    options={
                      fareOptions.length > 0
                        ? fareOptions
                        : [{ value: "", label: source === null ? dict.tickets.faresLoading : dict.tickets.faresEmpty }]
                    }
                  />
                  {/* Сценарий существует только в демо-контуре и исчезнет вместе
                      с DemoPaymentGateway — отсюда явная подпись «только в демо». */}
                  <Select
                    label={dict.tickets.scenarioLabel}
                    hint={dict.tickets.scenarioHint}
                    value={scenario}
                    onChange={(event) => setScenario(event.target.value)}
                    options={[
                      { value: "approve", label: dict.tickets.scenarios.approve },
                      { value: "decline", label: dict.tickets.scenarios.decline },
                    ]}
                  />
                  {/* Полей карты здесь нет намеренно — см. javadoc PaymentGateway */}
                  <p className="text-caption text-text-secondary">{dict.tickets.noCardNotice}</p>
                  {buyError ? (
                    <p role="alert" className="text-small font-semibold text-brand-red">
                      {buyError}
                    </p>
                  ) : null}
                  <Button
                    type="submit"
                    variant="danger"
                    size="lg"
                    block
                    disabled={buyBusy || !online || fareOptions.length === 0}
                  >
                    {buyBusy ? dict.tickets.buying : dict.tickets.buySubmit}
                  </Button>
                </Stack>
              </form>
            </Card>
          )}

          {tab === "buy" && purchase && !declined && (
            <IssuedCard result={purchase} onReset={() => setPurchase(null)} />
          )}

          {tab === "buy" && declined && purchase && (
            <Stack gap={4} className="mt-5">
              {/* HTTP 402 — штатный сценарий, а не сбой: объясняем, а не пугаем */}
              <Alert
                tone="warning"
                label={dict.tickets.declinedLabel}
                heading={dict.tickets.declinedTitle}
              >
                {dict.tickets.declinedBody}
              </Alert>
              {purchase.payment.failureReason ? (
                <Card>
                  <Row label={dict.tickets.declinedReason}>
                    {purchase.payment.failureReason}
                  </Row>
                </Card>
              ) : null}
              <div>
                <Button onClick={() => setPurchase(null)}>{dict.tickets.buyAnother}</Button>
              </div>
            </Stack>
          )}

          {tab === "list" && (
            <MyTicketsPanel online={online} onOpen={openFromList} onCheck={checkFromList} />
          )}

          {tab === "manage" && (
            <>
              <Card heading={dict.tickets.manageTitle} description={dict.tickets.manageIntro} className="mt-5">
                <form onSubmit={lookup}>
                  <Stack gap={4}>
                    <Input
                      label={dict.tickets.ticketCode}
                      required
                      mono
                      autoComplete="off"
                      value={lookupCode}
                      onChange={(event) => setLookupCode(event.target.value)}
                      error={lookupError || undefined}
                    />
                    <Button type="submit" variant="primary" size="lg" block disabled={lookupBusy || !online}>
                      {lookupBusy ? dict.tickets.lookingUp : dict.tickets.lookupSubmit}
                    </Button>
                  </Stack>
                </form>
              </Card>

              {ticket && <TicketCard ticket={ticket} />}

              {ticket && (
                <Card
                  heading={dict.tickets.topUpTitle}
                  description={dict.tickets.topUpIntro}
                  className="mt-4"
                >
                  {topUpAllowed ? (
                    <form onSubmit={topUp}>
                      <Stack gap={4}>
                        <Input
                          label={dict.tickets.topUpAmount}
                          required
                          inputMode="decimal"
                          value={topUpAmount}
                          onChange={(event) => setTopUpAmount(event.target.value)}
                          error={topUpError || undefined}
                        />
                        <Button type="submit" variant="primary" disabled={topUpBusy || !online}>
                          {topUpBusy ? dict.tickets.topUpBusy : dict.tickets.topUpSubmit}
                        </Button>
                        {topUpDone ? (
                          <Alert tone="success" label={dict.tickets.topUpDoneLabel}>
                            {dict.tickets.topUpDone}
                          </Alert>
                        ) : null}
                      </Stack>
                    </form>
                  ) : (
                    <p className="text-small text-text-secondary">
                      {dict.tickets.topUpUnavailable}
                    </p>
                  )}
                </Card>
              )}

              {ticket && (
                <Card
                  heading={dict.tickets.refundTitle}
                  description={dict.tickets.refundIntro}
                  className="mt-4"
                >
                  <form onSubmit={refund}>
                    <Stack gap={4}>
                      <Textarea
                        label={dict.tickets.refundReason}
                        required
                        rows={3}
                        maxLength={500}
                        placeholder={dict.tickets.refundReasonPlaceholder}
                        value={refundReason}
                        onChange={(event) => setRefundReason(event.target.value)}
                        error={refundError || undefined}
                      />
                      {/* danger + глагол в подписи: цвет не единственный сигнал */}
                      <Button type="submit" variant="danger" disabled={refundBusy || !online}>
                        {refundBusy ? dict.tickets.refundBusy : dict.tickets.refundSubmit}
                      </Button>
                      {refundDone ? (
                        <Alert tone="success" label={dict.tickets.refundDoneLabel}>
                          {dict.tickets.refundDone}
                        </Alert>
                      ) : null}
                    </Stack>
                  </form>
                </Card>
              )}
            </>
          )}

          {tab === "validate" && (
            <>
              <Card heading={dict.tickets.validateTitle} description={dict.tickets.validateIntro} className="mt-5">
                <form onSubmit={check}>
                  <Stack gap={4}>
                    <Input
                      label={dict.tickets.validateToken}
                      required
                      mono
                      autoComplete="off"
                      value={token}
                      onChange={(event) => setToken(event.target.value)}
                      error={validateError || undefined}
                    />
                    <Button type="submit" variant="primary" size="lg" block disabled={validateBusy || !online}>
                      {validateBusy ? dict.tickets.validating : dict.tickets.validateSubmit}
                    </Button>
                  </Stack>
                </form>
              </Card>

              {/* Результат проверки объявляется скринридеру: решение турникета —
                  ровно то, ради чего пассажир нажал кнопку (A11Y-07). Регион
                  присутствует в DOM всегда, иначе aria-live не сработает. */}
              <div
                aria-live="polite"
                aria-label={dict.tickets.validateResultRegion}
                role="region"
                className="mt-4"
              >
                {validation && (
                  <Stack gap={4}>
                    <Alert
                      tone={validation.valid ? "success" : "critical"}
                      label={validation.valid ? dict.tickets.validLabel : dict.tickets.invalidLabel}
                      heading={validation.valid ? dict.tickets.validTitle : dict.tickets.invalidTitle}
                      live={false}
                    >
                      {/* reason приходит в 200-ответе и НЕ является ошибкой запроса */}
                      {validation.reason
                        ? dict.tickets.reasons[validation.reason]
                        : dict.tickets.demoBody /* билет действителен, но демонстрационный */}
                    </Alert>
                    {validation.ticketCode ? (
                      <Card actions={validation.demo ? <DemoBadge /> : null}>
                        <dl className="grid gap-4 sm:grid-cols-2">
                          <Row label={dict.tickets.ticketCode}>
                            <span className="font-mono">{validation.ticketCode}</span>
                          </Row>
                          {validation.status ? (
                            <Row label={dict.tickets.status}>
                              <Badge tone={STATUS_TONE[validation.status]} dot>
                                {dict.tickets.statuses[validation.status]}
                              </Badge>
                            </Row>
                          ) : null}
                          {validation.kind ? (
                            <Row label={dict.tickets.kind}>{dict.tickets.kinds[validation.kind]}</Row>
                          ) : null}
                          {validation.validUntil ? (
                            <Row label={dict.tickets.validUntil}>
                              {formatDushanbeDateTime(validation.validUntil, lang)}
                            </Row>
                          ) : null}
                        </dl>
                      </Card>
                    ) : null}
                  </Stack>
                )}
              </div>
            </>
          )}
        </Container>
      </main>
  );
}
