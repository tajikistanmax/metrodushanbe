"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import {
  createWebhookSubscription,
  deleteWebhookSubscription,
  getWebhookDeliveries,
  retryWebhookDelivery,
  rotateWebhookSecret,
  updateWebhookSubscription,
} from "@/lib/admin-actions";
import {
  WEBHOOK_DELIVERY_STATUSES,
  WEBHOOK_EVENT_TYPES,
  type ActionError,
  type WebhookEventTypeInput,
} from "@/lib/admin-forms";
import { formatDateTime } from "@/lib/i18n";
import type {
  WebhookDelivery,
  WebhookDeliveryPage,
  WebhookDeliveryStatus,
  WebhookSubscription,
} from "@/lib/types";
import DataTable, { type Column } from "../DataTable";
import { useI18n } from "../I18nProvider";
import StateNotice from "../StateNotice";
import ConfirmDialog from "../admin/ConfirmDialog";
import Modal from "../admin/Modal";
import Pager from "../admin/Pager";
import Toolbar from "../admin/Toolbar";
import { FormActions, ServerError, TextField } from "../admin/fields";
import { useToast } from "../admin/ToastProvider";
import { Alert, Badge, Button, Card } from "@/shared/ui";

type Tab = "subscribers" | "deliveries";

/** "attention" — запрос без ?status=: backend отдаёт failed и dead. */
type DeliveryFilter = "attention" | WebhookDeliveryStatus;

type SubscriberDraft = {
  code: string;
  name: string;
  targetUrl: string;
  eventTypes: WebhookEventTypeInput[];
  active: boolean;
  /** Строка: пустое поле = «значение сервиса по умолчанию», а не 0. */
  rateLimitPerMinute: string;
};

type SubscriberEditor = {
  originalCode: string | null;
  draft: SubscriberDraft;
};

type WebhooksManagerProps = {
  subscriptions: WebhookSubscription[] | null;
  subscriptionsError: string | null;
  /** Первая страница очереди, отданная сервером; дальше листает клиент. */
  deliveries: WebhookDeliveryPage | null;
  deliveriesError: string | null;
  /** Роль superadmin: подписчики, секреты, лимиты. */
  canManage: boolean;
  /** Роль operator: очередь доставок и ручной повтор (U-OPS-04). */
  canRetry: boolean;
};

const DELIVERY_TONE: Record<
  WebhookDeliveryStatus,
  "neutral" | "info" | "success" | "warning" | "critical"
> = {
  pending: "info",
  sent: "success",
  failed: "warning",
  dead: "critical",
};

function emptyDraft(): SubscriberDraft {
  return {
    code: "",
    name: "",
    targetUrl: "",
    eventTypes: [],
    active: true,
    rateLimitPerMinute: "",
  };
}

function draftFromRow(row: WebhookSubscription): SubscriberDraft {
  return {
    code: row.code,
    name: row.name,
    targetUrl: row.targetUrl,
    eventTypes: [...row.eventTypes],
    active: row.active,
    rateLimitPerMinute: String(row.rateLimitPerMinute),
  };
}

export default function WebhooksManager({
  subscriptions,
  subscriptionsError,
  deliveries,
  deliveriesError,
  canManage,
  canRetry,
}: WebhooksManagerProps) {
  const { lang, dict } = useI18n();
  const router = useRouter();
  const toast = useToast();
  const t = dict.webhooks;

  const [tab, setTab] = useState<Tab>("deliveries");
  const [editor, setEditor] = useState<SubscriberEditor | null>(null);
  const [rotateTarget, setRotateTarget] = useState<WebhookSubscription | null>(null);
  const [toDelete, setToDelete] = useState<string | null>(null);
  /** Плейнтекст секрета живёт только здесь и только до закрытия модалки. */
  const [secret, setSecret] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [filter, setFilter] = useState<DeliveryFilter>("attention");
  const [queue, setQueue] = useState<{
    page: WebhookDeliveryPage | null;
    error: string | null;
    loading: boolean;
  }>({ page: deliveries, error: deliveriesError, loading: false });
  const [busy, setBusy] = useState(false);
  const [serverError, setServerError] = useState<ActionError | null>(null);

  const patch = (value: Partial<SubscriberDraft>) =>
    setEditor((current) =>
      current ? { ...current, draft: { ...current.draft, ...value } } : current,
    );

  /**
   * Перечитывает страницу очереди. Смена фильтра всегда возвращает на первую
   * страницу: «страница 4» прежнего фильтра в новом означает другие строки, а
   * чаще всего не существует вовсе.
   */
  const reloadQueue = async (next: DeliveryFilter, page = 0) => {
    setFilter(next);
    setQueue((current) => ({ ...current, loading: true }));
    const result = await getWebhookDeliveries(
      next === "attention" ? undefined : next,
      page,
    );
    setQueue({ page: result.data, error: result.error, loading: false });
  };

  const submitSubscriber = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!editor) return;
    const { draft, originalCode } = editor;
    if (
      !draft.name.trim() ||
      !draft.targetUrl.trim() ||
      draft.eventTypes.length === 0 ||
      (originalCode === null && !draft.code.trim())
    ) {
      setServerError({ code: "form.invalid", message: dict.form.fixErrors });
      return;
    }
    const parsedLimit = draft.rateLimitPerMinute.trim()
      ? Number(draft.rateLimitPerMinute)
      : undefined;
    if (
      parsedLimit !== undefined &&
      (!Number.isFinite(parsedLimit) || parsedLimit <= 0)
    ) {
      setServerError({ code: "form.invalid", message: dict.form.errNumber });
      return;
    }
    setBusy(true);
    setServerError(null);
    const payload = {
      name: draft.name.trim(),
      targetUrl: draft.targetUrl.trim(),
      eventTypes: draft.eventTypes,
      active: draft.active,
      rateLimitPerMinute: parsedLimit,
    };
    if (originalCode === null) {
      const result = await createWebhookSubscription({
        code: draft.code.trim(),
        ...payload,
      });
      setBusy(false);
      if (!result.ok) {
        setServerError(result.error);
        return;
      }
      toast.success(dict.toast.created);
      setEditor(null);
      // Секрет виден ровно здесь и больше нигде — модалку показываем сразу,
      // до router.refresh(), чтобы обновление не смахнуло единственный показ.
      setCopied(false);
      setSecret(result.data.secret);
      router.refresh();
      return;
    }
    const result = await updateWebhookSubscription(originalCode, payload);
    setBusy(false);
    if (!result.ok) {
      setServerError(result.error);
      return;
    }
    toast.success(dict.toast.updated);
    setEditor(null);
    router.refresh();
  };

  const confirmRotate = async () => {
    if (!rotateTarget) return;
    setBusy(true);
    setServerError(null);
    const result = await rotateWebhookSecret(rotateTarget.code);
    setBusy(false);
    if (!result.ok) {
      setServerError(result.error);
      return;
    }
    setRotateTarget(null);
    setCopied(false);
    setSecret(result.data.secret);
    router.refresh();
  };

  const confirmDelete = async () => {
    if (!toDelete) return;
    setBusy(true);
    setServerError(null);
    const result = await deleteWebhookSubscription(toDelete);
    setBusy(false);
    if (!result.ok) {
      setServerError(result.error);
      return;
    }
    toast.success(dict.toast.deleted);
    setToDelete(null);
    router.refresh();
  };

  const copySecret = async () => {
    if (!secret) return;
    try {
      await navigator.clipboard.writeText(secret);
      setCopied(true);
    } catch {
      toast.error(t.secretCopyFailed);
    }
  };

  const retry = async (delivery: WebhookDelivery) => {
    setBusy(true);
    const result = await retryWebhookDelivery(delivery.code);
    setBusy(false);
    if (!result.ok) {
      toast.error(`${result.error.message} (${result.error.code})`);
      return;
    }
    toast.success(t.retryDone);
    // Остаёмся на той же странице: оператор чинит очередь построчно, и
    // прыжок в начало после каждого повтора терял бы его место.
    await reloadQueue(filter, queue.page?.page ?? 0);
    router.refresh();
  };

  const toggleEvent = (
    value: WebhookEventTypeInput,
    current: WebhookEventTypeInput[],
  ): WebhookEventTypeInput[] =>
    current.includes(value)
      ? current.filter((item) => item !== value)
      : [...current, value];

  const subscriberColumns: Column<WebhookSubscription>[] = [
    {
      key: "code",
      header: t.fieldCode,
      rowHeader: true,
      cell: (row) => <span className="font-mono text-caption">{row.code}</span>,
    },
    { key: "name", header: t.colName, cell: (row) => row.name },
    {
      key: "url",
      header: t.colUrl,
      cell: (row) => (
        <span className="break-all font-mono text-caption">{row.targetUrl}</span>
      ),
    },
    {
      key: "events",
      header: t.colEvents,
      cell: (row) => (
        <ul className="grid gap-0.5 text-caption">
          {row.eventTypes.map((type) => (
            <li key={type}>{t.eventTypes[type]}</li>
          ))}
        </ul>
      ),
    },
    {
      key: "state",
      header: t.colState,
      cell: (row) => (
        <Badge tone={row.active ? "success" : "neutral"} dot>
          {row.active ? dict.operations.enabled : dict.operations.disabled}
        </Badge>
      ),
    },
    {
      key: "limit",
      header: t.colRateLimit,
      align: "right",
      cell: (row) => (
        <span className="tabular-nums">
          {row.rateLimitPerMinute} {t.perMinute}
        </span>
      ),
    },
    {
      key: "fingerprint",
      header: t.colFingerprint,
      cell: (row) => (
        <span className="font-mono text-caption">{row.secretFingerprint}</span>
      ),
    },
    {
      key: "actions",
      header: dict.colActions,
      align: "right",
      cell: (row) => {
        if (!canManage) {
          return <span className="text-text-secondary">{dict.none}</span>;
        }
        return (
          <div className="flex flex-wrap justify-end gap-1.5">
            <Button
              size="sm"
              onClick={() => {
                setServerError(null);
                setEditor({ originalCode: row.code, draft: draftFromRow(row) });
              }}
            >
              {dict.actions.edit}
            </Button>
            <Button
              size="sm"
              onClick={() => {
                setServerError(null);
                setRotateTarget(row);
              }}
            >
              {t.rotate}
            </Button>
            <Button
              size="sm"
              variant="danger"
              onClick={() => {
                setServerError(null);
                setToDelete(row.code);
              }}
            >
              {dict.actions.delete}
            </Button>
          </div>
        );
      },
    },
  ];

  const deliveryColumns: Column<WebhookDelivery>[] = [
    {
      key: "event",
      header: t.colEvent,
      rowHeader: true,
      cell: (row) => (
        <div className="max-w-[16rem]">
          <p className="font-semibold">{t.eventTypes[row.eventType]}</p>
          <p className="mt-0.5 font-mono text-caption text-text-secondary">
            {row.code}
          </p>
        </div>
      ),
    },
    {
      key: "subject",
      header: t.colSubject,
      cell: (row) => (
        <span className="font-mono text-caption">
          {row.aggregateType}/{row.aggregateCode}
        </span>
      ),
    },
    {
      key: "subscriber",
      header: t.colSubscriber,
      cell: (row) => (
        <span className="font-mono text-caption">{row.subscriptionCode}</span>
      ),
    },
    {
      key: "status",
      header: dict.colStatus,
      cell: (row) => (
        <Badge tone={DELIVERY_TONE[row.status]} dot>
          {t.deliveryStatuses[row.status]}
        </Badge>
      ),
    },
    {
      key: "attempts",
      header: t.colAttempts,
      align: "right",
      cell: (row) => <span className="tabular-nums">{row.attempts}</span>,
    },
    {
      key: "response",
      header: t.colResponse,
      align: "right",
      cell: (row) =>
        row.responseStatus === null ? (
          <span className="text-text-secondary">{dict.none}</span>
        ) : (
          <span className="tabular-nums">{row.responseStatus}</span>
        ),
    },
    {
      key: "error",
      header: t.colError,
      cell: (row) =>
        row.lastError ? (
          <span className="text-caption">{row.lastError}</span>
        ) : (
          <span className="text-text-secondary">{dict.none}</span>
        ),
    },
    {
      key: "next",
      header: t.colNextAttempt,
      cell: (row) =>
        row.nextAttemptAt ? formatDateTime(row.nextAttemptAt, lang) : dict.none,
    },
    {
      key: "trace",
      header: t.colTrace,
      cell: (row) =>
        row.traceId ? (
          <span className="font-mono text-caption">{row.traceId}</span>
        ) : (
          <span className="text-text-secondary">{dict.none}</span>
        ),
    },
    {
      key: "actions",
      header: dict.colActions,
      align: "right",
      cell: (row) => {
        // retryable решает backend — консоль не изобретает своё правило и не
        // показывает действие, которое всё равно вернёт 400.
        if (!canRetry || !row.retryable) {
          return <span className="text-text-secondary">{t.notRetryable}</span>;
        }
        return (
          <Button size="sm" disabled={busy} onClick={() => retry(row)}>
            {t.retry}
          </Button>
        );
      },
    },
  ];

  // Разделение на DLQ и остальное — в пределах ТЕКУЩЕЙ страницы: страница и есть
  // то, что оператор сейчас видит, а итоги по всей очереди показывает Pager.
  const rows = queue.page?.items ?? [];
  const dlqRows = rows.filter((row) => row.status === "dead");
  const otherRows = rows.filter((row) => row.status !== "dead");

  const filters: { key: DeliveryFilter; label: string }[] = [
    { key: "attention", label: t.filterAttention },
    ...WEBHOOK_DELIVERY_STATUSES.map((status) => ({
      key: status as DeliveryFilter,
      label: t.deliveryStatuses[status],
    })),
  ];

  const tabs: { key: Tab; label: string }[] = [
    { key: "deliveries", label: t.tabDeliveries },
    { key: "subscribers", label: t.tabSubscribers },
  ];

  return (
    <div className="grid gap-5">
      <div role="tablist" aria-label={t.title} className="flex flex-wrap gap-1.5">
        {tabs.map(({ key, label }) => (
          <button
            key={key}
            type="button"
            role="tab"
            id={`wh-tab-${key}`}
            aria-selected={tab === key}
            aria-controls={`wh-panel-${key}`}
            onClick={() => setTab(key)}
            className={
              tab === key
                ? "rounded-control bg-brand-navy px-4 py-2 text-small font-bold text-surface-light"
                : "rounded-control border border-[var(--border-strong)] px-4 py-2 text-small font-semibold transition-colors hover:bg-[var(--surface-hover)]"
            }
          >
            {label}
          </button>
        ))}
      </div>

      {tab === "deliveries" ? (
        <section
          id="wh-panel-deliveries"
          role="tabpanel"
          aria-labelledby="wh-tab-deliveries"
          className="grid gap-4"
        >
          <p className="text-small text-text-secondary">{t.deliveriesLead}</p>
          <div
            role="group"
            aria-label={dict.colStatus}
            className="flex flex-wrap gap-1.5"
          >
            {filters.map(({ key, label }) => (
              <button
                key={key}
                type="button"
                aria-pressed={filter === key}
                disabled={queue.loading}
                onClick={() => reloadQueue(key)}
                className={
                  filter === key
                    ? "rounded-control bg-brand-navy px-3 py-1.5 text-caption font-bold text-surface-light disabled:opacity-50"
                    : "rounded-control border border-[var(--border-strong)] px-3 py-1.5 text-caption font-semibold transition-colors hover:bg-[var(--surface-hover)] disabled:opacity-50"
                }
              >
                {label}
              </button>
            ))}
          </div>

          <div aria-live="polite" className="grid gap-4">
            {queue.loading ? (
              <p role="status" className="text-small text-text-secondary">
                {dict.loading}
              </p>
            ) : queue.error ? (
              <StateNotice kind="error" detail={queue.error} />
            ) : rows.length === 0 ? (
              <StateNotice kind="empty" />
            ) : filter === "attention" ? (
              <>
                {/* DLQ отделён намеренно: dead — это не «ещё одна ошибка», а
                    то, что автоматика уже не подберёт. */}
                <Card
                  heading={t.dlqTitle}
                  description={t.dlqLead}
                  padding="md"
                  className="border-l-4 border-l-brand-red"
                >
                  {dlqRows.length === 0 ? (
                    <p className="text-small text-text-secondary">{t.dlqEmpty}</p>
                  ) : (
                    <DataTable
                      caption={t.dlqTitle}
                      columns={deliveryColumns}
                      rows={dlqRows}
                      rowKey={(row) => row.code}
                      totalLabel={`${dict.total}: ${dlqRows.length}`}
                    />
                  )}
                </Card>
                {otherRows.length > 0 ? (
                  <Card heading={t.deliveriesTitle} padding="md">
                    <DataTable
                      caption={t.deliveriesTitle}
                      columns={deliveryColumns}
                      rows={otherRows}
                      rowKey={(row) => row.code}
                      totalLabel={`${dict.total}: ${otherRows.length}`}
                    />
                  </Card>
                ) : null}
              </>
            ) : (
              <DataTable
                caption={t.deliveriesTitle}
                columns={deliveryColumns}
                rows={rows}
                rowKey={(row) => row.code}
                totalLabel={`${dict.total}: ${rows.length}`}
              />
            )}

            {queue.page && !queue.error ? (
              <Pager
                page={queue.page.page}
                totalPages={queue.page.totalPages}
                totalElements={queue.page.totalElements}
                size={queue.page.size}
                busy={queue.loading || busy}
                onPage={(next) => reloadQueue(filter, next)}
              />
            ) : null}
          </div>
        </section>
      ) : null}

      {tab === "subscribers" ? (
        <section
          id="wh-panel-subscribers"
          role="tabpanel"
          aria-labelledby="wh-tab-subscribers"
          className="grid gap-3"
        >
          <p className="text-small text-text-secondary">{t.subscribersLead}</p>
          {canManage ? (
            <Toolbar
              createLabel={t.createTitle}
              onCreate={() => {
                setServerError(null);
                setEditor({ originalCode: null, draft: emptyDraft() });
              }}
            />
          ) : (
            <Alert tone="info" label={dict.severity.info} live={false}>
              {t.superadminOnly}
            </Alert>
          )}
          {subscriptionsError ? (
            <StateNotice kind="error" detail={subscriptionsError} />
          ) : !subscriptions || subscriptions.length === 0 ? (
            <StateNotice kind="empty" />
          ) : (
            <>
              <p className="text-caption text-text-secondary">
                {t.fingerprintHint}
              </p>
              <DataTable
                caption={t.subscribersTitle}
                columns={subscriberColumns}
                rows={subscriptions}
                rowKey={(row) => row.code}
                totalLabel={`${dict.total}: ${subscriptions.length}`}
              />
            </>
          )}
        </section>
      ) : null}

      {/* Карточка подписчика */}
      <Modal
        open={editor !== null}
        onClose={() => setEditor(null)}
        busy={busy}
        title={editor?.originalCode === null ? t.createTitle : t.editTitle}
      >
        {editor ? (
          <form onSubmit={submitSubscriber} className="grid gap-4">
            <div className="grid gap-4 sm:grid-cols-2">
              {editor.originalCode === null ? (
                <TextField
                  label={t.fieldCode}
                  hint={t.fieldCodeHint}
                  value={editor.draft.code}
                  onChange={(code) => patch({ code })}
                  mono
                  required
                  disabled={busy}
                />
              ) : null}
              <TextField
                label={t.fieldName}
                value={editor.draft.name}
                onChange={(name) => patch({ name })}
                required
                disabled={busy}
              />
            </div>
            <TextField
              label={t.fieldUrl}
              hint={t.fieldUrlHint}
              value={editor.draft.targetUrl}
              onChange={(targetUrl) => patch({ targetUrl })}
              mono
              required
              disabled={busy}
            />
            <fieldset className="rounded-control border border-[var(--border-subtle)] px-3 pb-3 pt-2">
              <legend className="px-1 text-small font-semibold">
                {t.fieldEvents}
                <span aria-hidden="true" className="ml-0.5 text-brand-red">
                  *
                </span>
              </legend>
              <p className="mb-2 text-caption text-text-secondary">
                {t.fieldEventsHint}
              </p>
              <div className="grid gap-2 sm:grid-cols-2">
                {WEBHOOK_EVENT_TYPES.map((type) => (
                  <label key={type} className="flex items-center gap-2">
                    <input
                      type="checkbox"
                      checked={editor.draft.eventTypes.includes(type)}
                      disabled={busy}
                      onChange={() =>
                        patch({
                          eventTypes: toggleEvent(type, editor.draft.eventTypes),
                        })
                      }
                      className="h-4 w-4 accent-[var(--brand-navy)]"
                    />
                    <span className="text-small font-semibold">
                      {t.eventTypes[type]}
                    </span>
                  </label>
                ))}
              </div>
            </fieldset>
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField
                label={t.fieldRateLimit}
                hint={t.fieldRateLimitHint}
                value={editor.draft.rateLimitPerMinute}
                onChange={(rateLimitPerMinute) => patch({ rateLimitPerMinute })}
                mono
                disabled={busy}
              />
              <label className="flex items-end gap-2 pb-2">
                <input
                  type="checkbox"
                  checked={editor.draft.active}
                  disabled={busy}
                  onChange={(event) => patch({ active: event.target.checked })}
                  className="h-4 w-4 accent-[var(--brand-navy)]"
                />
                <span className="text-small font-semibold">{t.fieldActive}</span>
              </label>
            </div>
            {editor.originalCode === null ? (
              <Alert tone="info" label={dict.severity.info} live={false}>
                {t.secretOnceWarning}
              </Alert>
            ) : null}
            {serverError ? <ServerError {...serverError} /> : null}
            <FormActions onCancel={() => setEditor(null)} busy={busy} />
          </form>
        ) : null}
      </Modal>

      {/* Ротация секрета */}
      <Modal
        open={rotateTarget !== null}
        onClose={() => setRotateTarget(null)}
        busy={busy}
        title={t.rotateTitle}
      >
        {rotateTarget ? (
          <div className="grid gap-4">
            <p className="text-small">
              <span className="font-mono text-caption">{rotateTarget.code}</span>
              {" — "}
              {rotateTarget.name}
            </p>
            <Alert tone="critical" label={dict.severity.critical}>
              {t.rotateWarning}
            </Alert>
            {serverError ? <ServerError {...serverError} /> : null}
            <div className="flex justify-end gap-2">
              <Button onClick={() => setRotateTarget(null)} disabled={busy}>
                {dict.actions.cancel}
              </Button>
              <Button variant="danger" onClick={confirmRotate} disabled={busy}>
                {busy ? dict.actions.saving : t.rotateConfirm}
              </Button>
            </div>
          </div>
        ) : null}
      </Modal>

      {/*
        Единственный показ секрета. busy=true не даёт закрыть окно ни Esc, ни
        кликом по фону, ни крестиком: случайное закрытие здесь стоит ротации.
        Выход — только осознанной кнопкой «Я сохранил секрет».
      */}
      <Modal
        open={secret !== null}
        onClose={() => setSecret(null)}
        busy
        title={t.secretTitle}
      >
        {secret ? (
          <div className="grid gap-4">
            <Alert tone="critical" label={dict.severity.critical}>
              {t.secretOnceWarning}
            </Alert>
            <p className="text-small text-text-secondary">{t.secretLead}</p>
            <div>
              <p className="mb-1 text-small font-semibold">{t.secretLabel}</p>
              <code className="block break-all rounded-control border border-[var(--border-subtle)] bg-[var(--surface-sunken)] px-3 py-2 font-mono text-small">
                {secret}
              </code>
            </div>
            <div className="flex flex-wrap justify-end gap-2">
              <Button onClick={copySecret}>
                {copied ? t.secretCopied : t.secretCopy}
              </Button>
              <Button
                variant="primary"
                onClick={() => {
                  setSecret(null);
                  setCopied(false);
                }}
              >
                {t.secretAck}
              </Button>
            </div>
          </div>
        ) : null}
      </Modal>

      <ConfirmDialog
        open={toDelete !== null}
        onClose={() => setToDelete(null)}
        onConfirm={confirmDelete}
        target={toDelete ?? ""}
        busy={busy}
        error={serverError}
      />
    </div>
  );
}
