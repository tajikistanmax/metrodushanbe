"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import {
  changeNotificationStatus,
  createNotification,
  createNotificationTemplate,
  deleteNotificationTemplate,
  getNotificationDeliveries,
  getNotificationProblemDeliveries,
  getNotifications,
  retryNotificationDelivery,
  sendNotification,
  updateNotification,
  updateNotificationTemplate,
} from "@/lib/admin-actions";
import {
  EMPTY_I18N,
  NOTIFICATION_CHANNELS,
  NOTIFICATION_STATUSES,
  NOTIFICATION_TARGET_TYPES,
  NOTIFICATION_TYPES,
  SIMULATED_CHANNELS,
  type ActionError,
  type I18nInput,
  type NotificationChannelInput,
  type NotificationTargetBody,
  type NotificationTargetTypeInput,
  type NotificationTypeInput,
} from "@/lib/admin-forms";
import { formatDateTime, pickName } from "@/lib/i18n";
import type {
  Notification,
  NotificationDelivery,
  NotificationDeliveryPage,
  NotificationPage,
  NotificationStatus,
  NotificationTemplate,
} from "@/lib/types";
import DataTable, { type Column } from "../DataTable";
import { useI18n } from "../I18nProvider";
import StateNotice from "../StateNotice";
import ConfirmDialog from "../admin/ConfirmDialog";
import Modal from "../admin/Modal";
import Pager from "../admin/Pager";
import Toolbar from "../admin/Toolbar";
import {
  FormActions,
  I18nField,
  SelectField,
  ServerError,
  TextField,
} from "../admin/fields";
import { useToast } from "../admin/ToastProvider";
import { Alert, Badge, Button, Card } from "@/shared/ui";

type Tab = "messages" | "problems" | "templates";

/** Черновик формы рассылки: i18n-тексты редактируются как I18nInput. */
type MessageDraft = {
  code: string;
  templateCode: string;
  type: NotificationTypeInput;
  title: I18nInput;
  body: I18nInput;
  channels: NotificationChannelInput[];
  targets: NotificationTargetBody[];
  /** Значение <input type="datetime-local"> (локальная зона) либо "". */
  scheduledAtLocal: string;
};

type MessageEditor = {
  /** null — создание новой рассылки. */
  originalCode: string | null;
  /** Замороженная рассылка открывается только на чтение (NTF: notification.frozen). */
  frozen: boolean;
  draft: MessageDraft;
};

type TemplateDraft = {
  code: string;
  name: string;
  type: NotificationTypeInput;
  title: I18nInput;
  body: I18nInput;
  channels: NotificationChannelInput[];
  active: boolean;
};

type TemplateEditor = {
  originalCode: string | null;
  draft: TemplateDraft;
};

type DeliveriesPanel = {
  code: string;
  loading: boolean;
  data: NotificationDeliveryPage | null;
  error: string | null;
};

/** Очередь проблемных доставок: первая страница с сервера, дальше листает клиент. */
type ProblemsPanel = {
  loading: boolean;
  data: NotificationDeliveryPage | null;
  error: string | null;
};

/** Лента рассылок: первая страница с сервера, дальше листает клиент. */
type MessagesPanel = {
  loading: boolean;
  data: NotificationPage | null;
  error: string | null;
};

type NotificationsManagerProps = {
  messages: NotificationPage | null;
  error: string | null;
  templates: NotificationTemplate[] | null;
  templatesError: string | null;
  problems: NotificationDeliveryPage | null;
  problemsError: string | null;
  /** Роль operator и выше: ведение рассылок и повтор доставок. */
  canOperate: boolean;
  /** Роль editor и выше: правка шаблонов (они — редакционный справочник). */
  canEditTemplates: boolean;
};

const STATUS_FILTERS: (NotificationStatus | "all")[] = [
  "all",
  ...NOTIFICATION_STATUSES,
];

/**
 * Переходы, которые оператор выбирает руками в списке «Состояние».
 *
 * Карта переходов НЕ дублируется здесь: её считает backend и отдаёт в
 * `allowedTransitions` карточки (как у Incident и WebhookDelivery). Локальной
 * остаётся ровно одна вещь — политика самой консоли: `sending` и `sent` из
 * выбора убраны, потому что путь к отправке — кнопка «Отправить» (POST /send),
 * которая создаёт доставки, а прямой перевод в sent backend отклоняет вовсе
 * (400 notification.send_required). Это решение про UI, а не про домен, поэтому
 * ему здесь и место.
 */
const NOT_MANUALLY_SELECTABLE: NotificationStatus[] = ["sending", "sent"];

function manualTransitions(row: Notification): NotificationStatus[] {
  return row.allowedTransitions.filter(
    (status) => !NOT_MANUALLY_SELECTABLE.includes(status),
  );
}

const STATUS_TONE: Record<
  NotificationStatus,
  "neutral" | "info" | "success" | "warning" | "critical"
> = {
  draft: "neutral",
  scheduled: "info",
  sending: "warning",
  sent: "success",
  cancelled: "critical",
};

const DELIVERY_TONE: Record<
  NotificationDelivery["status"],
  "neutral" | "info" | "success" | "warning" | "critical"
> = {
  pending: "info",
  sent: "info",
  delivered: "success",
  failed: "critical",
};

/**
 * ISO → значение <input type="datetime-local"> ("YYYY-MM-DDTHH:mm").
 * Поле живёт в локальной зоне браузера, поэтому смещение снимаем вручную:
 * toISOString() дал бы UTC и сдвинул бы показанное оператору время.
 */
function toLocalInput(iso: string | null): string {
  if (!iso) return "";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

function fromLocalInput(local: string): string | undefined {
  return local ? new Date(local).toISOString() : undefined;
}

function emptyMessageDraft(): MessageDraft {
  return {
    code: "",
    templateCode: "",
    type: "info",
    title: EMPTY_I18N,
    body: EMPTY_I18N,
    channels: ["in_app"],
    targets: [],
    scheduledAtLocal: "",
  };
}

function draftFromRow(row: Notification): MessageDraft {
  return {
    code: row.code,
    templateCode: row.templateCode ?? "",
    type: row.type,
    title: { ...row.title },
    body: { ...row.body },
    channels: [...row.channels],
    targets: row.targets.map((target) => ({ ...target })),
    scheduledAtLocal: toLocalInput(row.scheduledAt),
  };
}

function emptyTemplateDraft(): TemplateDraft {
  return {
    code: "",
    name: "",
    type: "info",
    title: EMPTY_I18N,
    body: EMPTY_I18N,
    channels: ["in_app"],
    active: true,
  };
}

function templateDraftFromRow(row: NotificationTemplate): TemplateDraft {
  return {
    code: row.code,
    name: row.name,
    type: row.type,
    title: { ...row.title },
    body: { ...row.body },
    channels: [...row.channels],
    active: row.active,
  };
}

function i18nComplete(value: I18nInput): boolean {
  return Boolean(value.tg.trim() && value.ru.trim() && value.en.trim());
}

export default function NotificationsManager({
  messages,
  error,
  templates,
  templatesError,
  problems,
  problemsError,
  canOperate,
  canEditTemplates,
}: NotificationsManagerProps) {
  const { lang, dict } = useI18n();
  const router = useRouter();
  const toast = useToast();
  const t = dict.notifications;

  const [tab, setTab] = useState<Tab>("messages");
  const [filter, setFilter] = useState<NotificationStatus | "all">("all");
  const [editor, setEditor] = useState<MessageEditor | null>(null);
  const [templateEditor, setTemplateEditor] = useState<TemplateEditor | null>(null);
  const [templateToDelete, setTemplateToDelete] = useState<string | null>(null);
  const [sendTarget, setSendTarget] = useState<Notification | null>(null);
  const [statusTarget, setStatusTarget] = useState<{
    row: Notification;
    next: NotificationStatus | "";
  } | null>(null);
  const [deliveries, setDeliveries] = useState<DeliveriesPanel | null>(null);
  const [problemsPanel, setProblemsPanel] = useState<ProblemsPanel>({
    loading: false,
    data: problems,
    error: problemsError,
  });
  const [messagesPanel, setMessagesPanel] = useState<MessagesPanel>({
    loading: false,
    data: messages,
    error,
  });
  const [busy, setBusy] = useState(false);
  const [serverError, setServerError] = useState<ActionError | null>(null);

  const patch = (value: Partial<MessageDraft>) =>
    setEditor((current) =>
      current ? { ...current, draft: { ...current.draft, ...value } } : current,
    );

  const patchTemplate = (value: Partial<TemplateDraft>) =>
    setTemplateEditor((current) =>
      current ? { ...current, draft: { ...current.draft, ...value } } : current,
    );

  /** Шаблон задаёт значения по умолчанию; дальше рассылка от него не зависит. */
  const applyTemplate = (code: string) => {
    const template = (templates ?? []).find((item) => item.code === code);
    if (!template) {
      patch({ templateCode: "" });
      return;
    }
    patch({
      templateCode: code,
      type: template.type,
      title: { ...template.title },
      body: { ...template.body },
      channels: [...template.channels],
    });
  };

  const toggleChannel = (
    channel: NotificationChannelInput,
    current: NotificationChannelInput[],
  ): NotificationChannelInput[] =>
    current.includes(channel)
      ? current.filter((item) => item !== channel)
      : [...current, channel];

  const submitMessage = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!editor || editor.frozen) return;
    const { draft, originalCode } = editor;
    if (
      !i18nComplete(draft.title) ||
      !i18nComplete(draft.body) ||
      draft.channels.length === 0 ||
      (originalCode === null && !draft.code.trim())
    ) {
      setServerError({ code: "form.invalid", message: dict.form.fixErrors });
      return;
    }
    setBusy(true);
    setServerError(null);
    const targets = draft.targets.filter((target) => target.code.trim());
    const result =
      originalCode === null
        ? await createNotification({
            code: draft.code.trim(),
            templateCode: draft.templateCode || undefined,
            type: draft.type,
            title: draft.title,
            body: draft.body,
            channels: draft.channels,
            targets,
            scheduledAt: fromLocalInput(draft.scheduledAtLocal),
          })
        : await updateNotification(originalCode, {
            type: draft.type,
            title: draft.title,
            body: draft.body,
            channels: draft.channels,
            targets,
            scheduledAt: fromLocalInput(draft.scheduledAtLocal),
          });
    setBusy(false);
    if (!result.ok) {
      setServerError(result.error);
      return;
    }
    toast.success(originalCode === null ? dict.toast.created : dict.toast.updated);
    setEditor(null);
    // Не router.refresh(): лента живёт в локальном состоянии (иначе листалка
    // сбрасывалась бы на серверную страницу), и refresh обновил бы только проп,
    // оставив на экране прежний список — тот же приём, что в ImportManager.
    // Новая рассылка — всегда на первой странице (порядок createdAt desc);
    // правка же обязана вернуть оператора туда, где он стоял.
    await loadMessages(filter, originalCode === null ? 0 : messagesPage);
  };

  const submitTemplate = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!templateEditor) return;
    const { draft, originalCode } = templateEditor;
    if (
      !draft.name.trim() ||
      !i18nComplete(draft.title) ||
      !i18nComplete(draft.body) ||
      draft.channels.length === 0 ||
      (originalCode === null && !draft.code.trim())
    ) {
      setServerError({ code: "form.invalid", message: dict.form.fixErrors });
      return;
    }
    setBusy(true);
    setServerError(null);
    const payload = {
      name: draft.name.trim(),
      type: draft.type,
      title: draft.title,
      body: draft.body,
      channels: draft.channels,
      active: draft.active,
    };
    const result =
      originalCode === null
        ? await createNotificationTemplate({ code: draft.code.trim(), ...payload })
        : await updateNotificationTemplate(originalCode, payload);
    setBusy(false);
    if (!result.ok) {
      setServerError(result.error);
      return;
    }
    toast.success(originalCode === null ? dict.toast.created : dict.toast.updated);
    setTemplateEditor(null);
    router.refresh();
  };

  const confirmDeleteTemplate = async () => {
    if (!templateToDelete) return;
    setBusy(true);
    setServerError(null);
    const result = await deleteNotificationTemplate(templateToDelete);
    setBusy(false);
    if (!result.ok) {
      setServerError(result.error);
      return;
    }
    toast.success(dict.toast.deleted);
    setTemplateToDelete(null);
    router.refresh();
  };

  const confirmSend = async () => {
    if (!sendTarget) return;
    setBusy(true);
    setServerError(null);
    const result = await sendNotification(sendTarget.code);
    setBusy(false);
    if (!result.ok) {
      setServerError(result.error);
      return;
    }
    toast.success(t.sendDone);
    setSendTarget(null);
    // Отправка меняет и статус рассылки, и очередь доставок — перечитываем ленту
    // на месте: refresh обновил бы только серверный проп, а на экране осталась бы
    // рассылка в прежнем состоянии.
    await loadMessages(filter, messagesPage);
  };

  const submitStatus = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!statusTarget || !statusTarget.next) return;
    setBusy(true);
    setServerError(null);
    const result = await changeNotificationStatus(
      statusTarget.row.code,
      statusTarget.next,
    );
    setBusy(false);
    if (!result.ok) {
      setServerError(result.error);
      return;
    }
    toast.success(dict.toast.updated);
    setStatusTarget(null);
    // Смена статуса может выкинуть строку из отфильтрованной ленты — перечитываем
    // ту же страницу с тем же фильтром.
    await loadMessages(filter, messagesPage);
  };

  const toggleDeliveries = async (row: Notification) => {
    if (deliveries?.code === row.code) {
      setDeliveries(null);
      return;
    }
    await loadDeliveries(row.code, 0);
  };

  const loadDeliveries = async (code: string, page: number) => {
    setDeliveries({ code, loading: true, data: null, error: null });
    const result = await getNotificationDeliveries(code, page);
    setDeliveries({
      code,
      loading: false,
      data: result.data,
      error: result.error,
    });
  };

  /**
   * Загрузка страницы ленты. Фильтр передаётся аргументом, а не читается из
   * состояния: setFilter применится только к следующему рендеру, и загрузка по
   * состоянию тянула бы предыдущий фильтр.
   */
  const loadMessages = async (
    status: NotificationStatus | "all",
    page: number,
  ) => {
    setMessagesPanel((current) => ({ ...current, loading: true }));
    const result = await getNotifications(
      status === "all" ? undefined : status,
      page,
    );
    setMessagesPanel({ loading: false, data: result.data, error: result.error });
  };

  /** Текущая страница ленты — куда возвращаться после правки. */
  const messagesPage = messagesPanel.data?.page ?? 0;

  /**
   * Смена фильтра обязана сбрасывать страницу на первую: на странице 3 полной
   * ленты у отфильтрованной может не быть и одной строки, и оператор увидел бы
   * пустой экран вместо своей выборки.
   */
  const applyFilter = async (value: NotificationStatus | "all") => {
    setFilter(value);
    await loadMessages(value, 0);
  };

  const loadProblems = async (page: number) => {
    setProblemsPanel((current) => ({ ...current, loading: true }));
    const result = await getNotificationProblemDeliveries(page);
    setProblemsPanel({ loading: false, data: result.data, error: result.error });
  };

  const retry = async (delivery: NotificationDelivery) => {
    setBusy(true);
    const result = await retryNotificationDelivery(delivery.id);
    setBusy(false);
    if (!result.ok) {
      toast.error(`${result.error.message} (${result.error.code})`);
      return;
    }
    toast.success(t.retryDone);
    // Перечитываем ту страницу, на которой оператор стоит: он разбирает очередь
    // построчно, и прыжок в начало после каждого повтора терял бы его место.
    if (deliveries?.data) {
      await loadDeliveries(deliveries.code, deliveries.data.page);
    }
    if (problemsPanel.data) {
      await loadProblems(problemsPanel.data.page);
    }
    router.refresh();
  };

  // Фильтрует backend (?status=), а не консоль: отфильтровать уже пришедшую
  // страницу значило бы искать нужный статус в 50 строках из тысячи.
  const rows = messagesPanel.data?.items ?? [];

  const columns: Column<Notification>[] = [
    {
      key: "code",
      header: t.colCode,
      rowHeader: true,
      cell: (row) => <span className="font-mono text-caption">{row.code}</span>,
    },
    {
      key: "title",
      header: t.colTitle,
      cell: (row) => (
        <div className="max-w-xs">
          <p className="font-semibold">{pickName(row.title, lang)}</p>
          <p className="mt-1 text-caption text-text-secondary">
            {t.types[row.type]}
          </p>
        </div>
      ),
    },
    {
      key: "channels",
      header: t.colChannels,
      cell: (row) => (
        <div className="flex flex-wrap gap-1">
          {row.channels.map((channel) => (
            <Badge
              key={channel}
              tone={
                SIMULATED_CHANNELS.includes(channel) ? "warning" : "neutral"
              }
            >
              {t.channels[channel]}
              {SIMULATED_CHANNELS.includes(channel) ? (
                <span className="font-normal"> · {t.simulatedBadge}</span>
              ) : null}
            </Badge>
          ))}
        </div>
      ),
    },
    {
      key: "status",
      header: t.colStatus,
      cell: (row) => (
        <Badge tone={STATUS_TONE[row.status]} dot>
          {t.statuses[row.status]}
        </Badge>
      ),
    },
    {
      key: "targets",
      header: t.colTargets,
      cell: (row) =>
        row.targets.length === 0 ? (
          <span className="text-text-secondary">{t.networkWide}</span>
        ) : (
          <ul className="grid gap-0.5 text-caption">
            {row.targets.map((target) => (
              <li key={`${target.type}:${target.code}`}>
                {t.targetTypes[target.type]}{" "}
                <span className="font-mono">{target.code}</span>
              </li>
            ))}
          </ul>
        ),
    },
    {
      key: "scheduled",
      header: t.colScheduled,
      cell: (row) =>
        row.scheduledAt ? formatDateTime(row.scheduledAt, lang) : dict.none,
    },
    {
      key: "sent",
      header: t.colSent,
      cell: (row) => (row.sentAt ? formatDateTime(row.sentAt, lang) : dict.none),
    },
    {
      key: "actions",
      header: dict.colActions,
      align: "right",
      cell: (row) => (
        <div className="flex flex-wrap justify-end gap-1.5">
          <Button
            size="sm"
            onClick={() => toggleDeliveries(row)}
            aria-expanded={deliveries?.code === row.code}
          >
            {deliveries?.code === row.code ? t.hideDeliveries : t.showDeliveries}
          </Button>
          {canOperate ? (
            <>
              <Button
                size="sm"
                onClick={() => {
                  setServerError(null);
                  setEditor({
                    originalCode: row.code,
                    frozen: row.frozen,
                    draft: draftFromRow(row),
                  });
                }}
              >
                {dict.actions.edit}
              </Button>
              <Button
                size="sm"
                disabled={busy || manualTransitions(row).length === 0}
                onClick={() => {
                  setServerError(null);
                  setStatusTarget({ row, next: "" });
                }}
              >
                {t.changeStatus}
              </Button>
              <Button
                size="sm"
                variant="primary"
                disabled={busy || row.frozen}
                onClick={() => {
                  setServerError(null);
                  setSendTarget(row);
                }}
              >
                {t.send}
              </Button>
            </>
          ) : null}
        </div>
      ),
    },
  ];

  const templateColumns: Column<NotificationTemplate>[] = [
    {
      key: "code",
      header: t.colCode,
      rowHeader: true,
      cell: (row) => <span className="font-mono text-caption">{row.code}</span>,
    },
    { key: "name", header: t.colName, cell: (row) => row.name },
    {
      key: "title",
      header: t.colTitle,
      cell: (row) => pickName(row.title, lang),
    },
    { key: "type", header: t.colType, cell: (row) => t.types[row.type] },
    {
      key: "channels",
      header: t.colChannels,
      cell: (row) => row.channels.map((c) => t.channels[c]).join(" · "),
    },
    {
      key: "active",
      header: dict.operations.state,
      cell: (row) => (
        <Badge tone={row.active ? "success" : "neutral"} dot>
          {row.active ? dict.operations.enabled : dict.operations.disabled}
        </Badge>
      ),
    },
    {
      key: "actions",
      header: dict.colActions,
      align: "right",
      cell: (row) =>
        canEditTemplates ? (
          <div className="flex justify-end gap-1.5">
            <Button
              size="sm"
              onClick={() => {
                setServerError(null);
                setTemplateEditor({
                  originalCode: row.code,
                  draft: templateDraftFromRow(row),
                });
              }}
            >
              {dict.actions.edit}
            </Button>
            <Button
              size="sm"
              variant="danger"
              onClick={() => {
                setServerError(null);
                setTemplateToDelete(row.code);
              }}
            >
              {dict.actions.delete}
            </Button>
          </div>
        ) : (
          <span className="text-text-secondary">{dict.none}</span>
        ),
    },
  ];

  const tabs: { key: Tab; label: string }[] = [
    { key: "messages", label: t.tabMessages },
    { key: "problems", label: t.tabProblems },
    { key: "templates", label: t.tabTemplates },
  ];

  return (
    <div className="grid gap-5">
      {/* Имитация внешних каналов — не деталь реализации, а то, без чего
          оператор решит, что письма реально ушли. Врезка постоянная. */}
      <Alert tone="warning" label={dict.severity.warning} live={false}>
        {t.simulatedNotice}
      </Alert>

      <div role="tablist" aria-label={t.title} className="flex flex-wrap gap-1.5">
        {tabs.map(({ key, label }) => (
          <button
            key={key}
            type="button"
            role="tab"
            id={`ntf-tab-${key}`}
            aria-selected={tab === key}
            aria-controls={`ntf-panel-${key}`}
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

      {tab === "messages" ? (
        <section
          id="ntf-panel-messages"
          role="tabpanel"
          aria-labelledby="ntf-tab-messages"
          className="grid gap-3"
        >
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div role="group" aria-label={t.colStatus} className="flex flex-wrap gap-1.5">
              {STATUS_FILTERS.map((value) => (
                <button
                  key={value}
                  type="button"
                  aria-pressed={filter === value}
                  disabled={messagesPanel.loading || busy}
                  onClick={() => applyFilter(value)}
                  className={
                    filter === value
                      ? "rounded-control bg-brand-navy px-3 py-1.5 text-caption font-bold text-surface-light"
                      : "rounded-control border border-[var(--border-strong)] px-3 py-1.5 text-caption font-semibold transition-colors hover:bg-[var(--surface-hover)]"
                  }
                >
                  {value === "all" ? t.filterAll : t.statuses[value]}
                </button>
              ))}
            </div>
            {canOperate ? (
              <Toolbar
                createLabel={t.createTitle}
                onCreate={() => {
                  setServerError(null);
                  setEditor({
                    originalCode: null,
                    frozen: false,
                    draft: emptyMessageDraft(),
                  });
                }}
              />
            ) : null}
          </div>

          <div aria-live="polite" className="grid gap-3">
            {messagesPanel.loading ? (
              <p role="status" className="text-small text-text-secondary">
                {dict.loading}
              </p>
            ) : messagesPanel.error ? (
              <StateNotice kind="error" detail={messagesPanel.error} />
            ) : rows.length === 0 ? (
              <StateNotice kind="empty" />
            ) : (
              <DataTable
                caption={t.title}
                columns={columns}
                rows={rows}
                rowKey={(row) => row.code}
                // Итог берём у backend: длина страницы — это не размер ленты.
                totalLabel={`${dict.total}: ${messagesPanel.data?.totalElements ?? rows.length}`}
              />
            )}

            {messagesPanel.data && !messagesPanel.error ? (
              <Pager
                page={messagesPanel.data.page}
                totalPages={messagesPanel.data.totalPages}
                totalElements={messagesPanel.data.totalElements}
                size={messagesPanel.data.size}
                busy={messagesPanel.loading || busy}
                onPage={(next) => loadMessages(filter, next)}
              />
            ) : null}
          </div>

          {deliveries ? (
            <Card heading={`${t.deliveriesTitle} · ${deliveries.code}`}>
              <div aria-live="polite" className="grid gap-3">
                {deliveries.loading ? (
                  <p role="status" className="text-small text-text-secondary">
                    {dict.loading}
                  </p>
                ) : deliveries.error ? (
                  <StateNotice kind="error" detail={deliveries.error} />
                ) : !deliveries.data || deliveries.data.items.length === 0 ? (
                  <p className="text-small text-text-secondary">{t.noDeliveries}</p>
                ) : (
                  <DeliveriesTable
                    rows={deliveries.data.items}
                    onRetry={canOperate ? retry : undefined}
                    busy={busy}
                  />
                )}

                {deliveries.data && !deliveries.error ? (
                  <Pager
                    page={deliveries.data.page}
                    totalPages={deliveries.data.totalPages}
                    totalElements={deliveries.data.totalElements}
                    size={deliveries.data.size}
                    busy={deliveries.loading || busy}
                    onPage={(next) => loadDeliveries(deliveries.code, next)}
                  />
                ) : null}
              </div>
            </Card>
          ) : null}
        </section>
      ) : null}

      {tab === "problems" ? (
        <section
          id="ntf-panel-problems"
          role="tabpanel"
          aria-labelledby="ntf-tab-problems"
          className="grid gap-3"
        >
          <p className="text-small text-text-secondary">{t.problemsLead}</p>
          <div aria-live="polite" className="grid gap-3">
            {problemsPanel.loading ? (
              <p role="status" className="text-small text-text-secondary">
                {dict.loading}
              </p>
            ) : problemsPanel.error ? (
              <StateNotice kind="error" detail={problemsPanel.error} />
            ) : !problemsPanel.data || problemsPanel.data.totalElements === 0 ? (
              // «Всё доставлено» говорим только про ПУСТУЮ ОЧЕРЕДЬ, а не про
              // пустую страницу: за последней страницей строк тоже нет, но
              // проблемы при этом никуда не делись.
              <Alert tone="success" label={dict.dash.ready} live={false}>
                {t.problemsEmpty}
              </Alert>
            ) : problemsPanel.data.items.length === 0 ? (
              <StateNotice kind="empty" />
            ) : (
              <DeliveriesTable
                rows={problemsPanel.data.items}
                onRetry={canOperate ? retry : undefined}
                busy={busy}
                caption={t.problemsTitle}
                showMessage
              />
            )}

            {problemsPanel.data && !problemsPanel.error ? (
              <Pager
                page={problemsPanel.data.page}
                totalPages={problemsPanel.data.totalPages}
                totalElements={problemsPanel.data.totalElements}
                size={problemsPanel.data.size}
                busy={problemsPanel.loading || busy}
                onPage={loadProblems}
              />
            ) : null}
          </div>
        </section>
      ) : null}

      {tab === "templates" ? (
        <section
          id="ntf-panel-templates"
          role="tabpanel"
          aria-labelledby="ntf-tab-templates"
          className="grid gap-3"
        >
          <p className="text-small text-text-secondary">{t.templatesLead}</p>
          {!canEditTemplates ? (
            <Alert tone="info" label={dict.severity.info} live={false}>
              {t.templatesReadOnly}
            </Alert>
          ) : (
            <Toolbar
              createLabel={t.templateCreate}
              onCreate={() => {
                setServerError(null);
                setTemplateEditor({
                  originalCode: null,
                  draft: emptyTemplateDraft(),
                });
              }}
            />
          )}
          {templatesError ? (
            <StateNotice kind="error" detail={templatesError} />
          ) : !templates || templates.length === 0 ? (
            <StateNotice kind="empty" />
          ) : (
            <DataTable
              caption={t.templatesTitle}
              columns={templateColumns}
              rows={templates}
              rowKey={(row) => row.code}
              totalLabel={`${dict.total}: ${templates.length}`}
            />
          )}
        </section>
      ) : null}

      {/* Форма рассылки */}
      <Modal
        open={editor !== null}
        onClose={() => setEditor(null)}
        busy={busy}
        title={editor?.originalCode === null ? t.createTitle : t.editTitle}
      >
        {editor ? (
          <form onSubmit={submitMessage} className="grid gap-4">
            {editor.frozen ? (
              <Alert tone="critical" label={t.frozenTitle}>
                {t.frozenText}
              </Alert>
            ) : null}
            {editor.originalCode === null ? (
              <div className="grid gap-4 sm:grid-cols-2">
                <TextField
                  label={t.fieldCode}
                  hint={t.fieldCodeHint}
                  value={editor.draft.code}
                  onChange={(code) => patch({ code })}
                  mono
                  required
                  disabled={busy}
                />
                <SelectField
                  label={t.fieldTemplate}
                  hint={t.fieldTemplateHint}
                  value={editor.draft.templateCode}
                  onChange={applyTemplate}
                  options={[
                    { value: "", label: t.templateNone },
                    ...(templates ?? [])
                      .filter((item) => item.active)
                      .map((item) => ({ value: item.code, label: item.name })),
                  ]}
                  disabled={busy}
                />
              </div>
            ) : null}
            <SelectField
              label={t.fieldType}
              value={editor.draft.type}
              onChange={(type) => patch({ type: type as NotificationTypeInput })}
              options={NOTIFICATION_TYPES.map((type) => ({
                value: type,
                label: t.types[type],
              }))}
              required
              disabled={busy || editor.frozen}
            />
            <I18nField
              legend={t.fieldTitleText}
              value={editor.draft.title}
              onChange={(title) => patch({ title })}
              required
            />
            <I18nField
              legend={t.fieldBody}
              value={editor.draft.body}
              onChange={(body) => patch({ body })}
              required
              multiline
            />
            <fieldset className="rounded-control border border-[var(--border-subtle)] px-3 pb-3 pt-2">
              <legend className="px-1 text-small font-semibold">
                {t.fieldChannels}
                <span aria-hidden="true" className="ml-0.5 text-brand-red">
                  *
                </span>
              </legend>
              <p className="mb-2 text-caption text-text-secondary">
                {t.fieldChannelsHint}
              </p>
              <div className="grid gap-2 sm:grid-cols-2">
                {NOTIFICATION_CHANNELS.map((channel) => (
                  <label key={channel} className="flex items-center gap-2">
                    <input
                      type="checkbox"
                      checked={editor.draft.channels.includes(channel)}
                      disabled={busy || editor.frozen}
                      onChange={() =>
                        patch({
                          channels: toggleChannel(channel, editor.draft.channels),
                        })
                      }
                      className="h-4 w-4 accent-[var(--brand-navy)]"
                    />
                    <span className="text-small font-semibold">
                      {t.channels[channel]}
                    </span>
                    {SIMULATED_CHANNELS.includes(channel) ? (
                      <Badge tone="warning">{t.simulatedBadge}</Badge>
                    ) : null}
                  </label>
                ))}
              </div>
            </fieldset>
            <TargetsEditor
              targets={editor.draft.targets}
              disabled={busy || editor.frozen}
              onChange={(targets) => patch({ targets })}
            />
            <TextField
              label={t.fieldScheduledAt}
              hint={t.fieldScheduledAtHint}
              type="datetime-local"
              value={editor.draft.scheduledAtLocal}
              onChange={(scheduledAtLocal) => patch({ scheduledAtLocal })}
              disabled={busy || editor.frozen}
            />
            {serverError ? <ServerError {...serverError} /> : null}
            {editor.frozen ? (
              <div className="flex justify-end">
                <Button onClick={() => setEditor(null)}>
                  {dict.actions.close}
                </Button>
              </div>
            ) : (
              <FormActions onCancel={() => setEditor(null)} busy={busy} />
            )}
          </form>
        ) : null}
      </Modal>

      {/* Форма шаблона */}
      <Modal
        open={templateEditor !== null}
        onClose={() => setTemplateEditor(null)}
        busy={busy}
        title={
          templateEditor?.originalCode === null ? t.templateCreate : t.templateEdit
        }
      >
        {templateEditor ? (
          <form onSubmit={submitTemplate} className="grid gap-4">
            <div className="grid gap-4 sm:grid-cols-2">
              {templateEditor.originalCode === null ? (
                <TextField
                  label={t.fieldCode}
                  hint={t.fieldCodeHint}
                  value={templateEditor.draft.code}
                  onChange={(code) => patchTemplate({ code })}
                  mono
                  required
                  disabled={busy}
                />
              ) : null}
              <TextField
                label={t.fieldName}
                hint={t.fieldNameHint}
                value={templateEditor.draft.name}
                onChange={(name) => patchTemplate({ name })}
                required
                disabled={busy}
              />
            </div>
            <SelectField
              label={t.fieldType}
              value={templateEditor.draft.type}
              onChange={(type) =>
                patchTemplate({ type: type as NotificationTypeInput })
              }
              options={NOTIFICATION_TYPES.map((type) => ({
                value: type,
                label: t.types[type],
              }))}
              required
              disabled={busy}
            />
            <I18nField
              legend={t.fieldTitleText}
              value={templateEditor.draft.title}
              onChange={(title) => patchTemplate({ title })}
              required
            />
            <I18nField
              legend={t.fieldBody}
              value={templateEditor.draft.body}
              onChange={(body) => patchTemplate({ body })}
              required
              multiline
            />
            <fieldset className="rounded-control border border-[var(--border-subtle)] px-3 pb-3 pt-2">
              <legend className="px-1 text-small font-semibold">
                {t.fieldChannels}
              </legend>
              <div className="grid gap-2 sm:grid-cols-2">
                {NOTIFICATION_CHANNELS.map((channel) => (
                  <label key={channel} className="flex items-center gap-2">
                    <input
                      type="checkbox"
                      checked={templateEditor.draft.channels.includes(channel)}
                      disabled={busy}
                      onChange={() =>
                        patchTemplate({
                          channels: toggleChannel(
                            channel,
                            templateEditor.draft.channels,
                          ),
                        })
                      }
                      className="h-4 w-4 accent-[var(--brand-navy)]"
                    />
                    <span className="text-small font-semibold">
                      {t.channels[channel]}
                    </span>
                    {SIMULATED_CHANNELS.includes(channel) ? (
                      <Badge tone="warning">{t.simulatedBadge}</Badge>
                    ) : null}
                  </label>
                ))}
              </div>
            </fieldset>
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={templateEditor.draft.active}
                disabled={busy}
                onChange={(event) =>
                  patchTemplate({ active: event.target.checked })
                }
                className="h-4 w-4 accent-[var(--brand-navy)]"
              />
              <span className="text-small font-semibold">{t.fieldActive}</span>
            </label>
            {serverError ? <ServerError {...serverError} /> : null}
            <FormActions onCancel={() => setTemplateEditor(null)} busy={busy} />
          </form>
        ) : null}
      </Modal>

      {/* Отправка */}
      <Modal
        open={sendTarget !== null}
        onClose={() => setSendTarget(null)}
        busy={busy}
        title={t.sendTitle}
      >
        {sendTarget ? (
          <div className="grid gap-4">
            <p className="text-small">
              <span className="font-mono text-caption">{sendTarget.code}</span>
              {" — "}
              {pickName(sendTarget.title, lang)}
            </p>
            <Alert tone="warning" label={dict.severity.warning}>
              {t.sendWarning}
            </Alert>
            <Alert tone="info" label={dict.severity.info} live={false}>
              {t.simulatedNotice}
            </Alert>
            {serverError ? <ServerError {...serverError} /> : null}
            <div className="flex justify-end gap-2">
              <Button onClick={() => setSendTarget(null)} disabled={busy}>
                {dict.actions.cancel}
              </Button>
              <Button variant="primary" onClick={confirmSend} disabled={busy}>
                {busy ? dict.actions.saving : t.send}
              </Button>
            </div>
          </div>
        ) : null}
      </Modal>

      {/* Смена состояния */}
      <Modal
        open={statusTarget !== null}
        onClose={() => setStatusTarget(null)}
        busy={busy}
        title={t.statusTitle}
      >
        {statusTarget ? (
          <form onSubmit={submitStatus} className="grid gap-4">
            <p className="text-small text-text-secondary">
              <span className="font-mono text-caption">
                {statusTarget.row.code}
              </span>
              {" — "}
              {t.statuses[statusTarget.row.status]}
            </p>
            <SelectField
              label={t.colStatus}
              value={statusTarget.next}
              onChange={(next) =>
                setStatusTarget((current) =>
                  current
                    ? { ...current, next: next as NotificationStatus }
                    : current,
                )
              }
              options={manualTransitions(statusTarget.row).map((status) => ({
                value: status,
                label: t.statuses[status],
              }))}
              required
              disabled={busy}
            />
            {serverError ? <ServerError {...serverError} /> : null}
            <FormActions onCancel={() => setStatusTarget(null)} busy={busy} />
          </form>
        ) : null}
      </Modal>

      <ConfirmDialog
        open={templateToDelete !== null}
        onClose={() => setTemplateToDelete(null)}
        onConfirm={confirmDeleteTemplate}
        target={templateToDelete ?? ""}
        busy={busy}
        error={serverError}
      />
    </div>
  );
}

/** Повторяемый список таргетов; пустой список = вся сеть (NTF-02). */
function TargetsEditor({
  targets,
  disabled,
  onChange,
}: {
  targets: NotificationTargetBody[];
  disabled: boolean;
  onChange: (targets: NotificationTargetBody[]) => void;
}) {
  const { dict } = useI18n();
  const t = dict.notifications;
  return (
    <fieldset className="rounded-control border border-[var(--border-subtle)] px-3 pb-3 pt-2">
      <legend className="px-1 text-small font-semibold">{t.fieldTargets}</legend>
      <p className="mb-2 text-caption text-text-secondary">{t.fieldTargetsHint}</p>
      {targets.length === 0 ? (
        <p className="mb-2 text-small">{t.networkWide}</p>
      ) : (
        <ul className="mb-2 grid gap-2">
          {targets.map((target, index) => (
            <li key={index} className="flex items-end gap-2">
              <div className="w-40">
                <SelectField
                  label={t.fieldTargetType}
                  value={target.type}
                  onChange={(type) =>
                    onChange(
                      targets.map((item, i) =>
                        i === index
                          ? { ...item, type: type as NotificationTargetTypeInput }
                          : item,
                      ),
                    )
                  }
                  options={NOTIFICATION_TARGET_TYPES.map((type) => ({
                    value: type,
                    label: t.targetTypes[type],
                  }))}
                  disabled={disabled}
                />
              </div>
              <div className="flex-1">
                <TextField
                  label={t.fieldTargetCode}
                  value={target.code}
                  onChange={(code) =>
                    onChange(
                      targets.map((item, i) =>
                        i === index ? { ...item, code } : item,
                      ),
                    )
                  }
                  mono
                  disabled={disabled}
                />
              </div>
              <Button
                variant="danger"
                disabled={disabled}
                onClick={() => onChange(targets.filter((_, i) => i !== index))}
              >
                {t.removeTarget}
              </Button>
            </li>
          ))}
        </ul>
      )}
      <Button
        disabled={disabled}
        onClick={() => onChange([...targets, { type: "line", code: "" }])}
      >
        {t.addTarget}
      </Button>
    </fieldset>
  );
}

/**
 * Очередь доставок (NTF-06). Признак simulated показывается у каждой строки:
 * без него «Доставлена» по каналу email читается как «письмо ушло».
 */
function DeliveriesTable({
  rows,
  onRetry,
  busy,
  caption,
  showMessage = false,
}: {
  rows: NotificationDelivery[];
  onRetry?: (delivery: NotificationDelivery) => void;
  busy: boolean;
  caption?: string;
  /** Показать колонку рассылки — нужна в сводной очереди проблем. */
  showMessage?: boolean;
}) {
  const { lang, dict } = useI18n();
  const t = dict.notifications;

  const columns: Column<NotificationDelivery>[] = [
    ...(showMessage
      ? [
          {
            key: "message",
            header: t.colCode,
            rowHeader: true,
            cell: (row: NotificationDelivery) => (
              <span className="font-mono text-caption">{row.messageCode}</span>
            ),
          } satisfies Column<NotificationDelivery>,
        ]
      : []),
    {
      key: "channel",
      header: t.colChannel,
      cell: (row) => (
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="font-semibold">{t.channels[row.channel]}</span>
          {row.simulated ? (
            <Badge tone="warning">{t.simulatedBadge}</Badge>
          ) : null}
        </div>
      ),
    },
    {
      key: "recipient",
      header: t.colRecipient,
      cell: (row) => <span className="font-mono text-caption">{row.recipient}</span>,
    },
    {
      key: "status",
      header: t.colStatus,
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
      key: "delivered",
      header: t.colDeliveredAt,
      cell: (row) =>
        row.deliveredAt ? formatDateTime(row.deliveredAt, lang) : dict.none,
    },
    {
      key: "actions",
      header: dict.colActions,
      align: "right",
      cell: (row) => {
        if (!onRetry) {
          return <span className="text-text-secondary">{dict.none}</span>;
        }
        // Повтор допустим только для failed (400 notification.retry_not_failed):
        // кнопка не показывает действие, которое backend отклонит.
        if (row.status !== "failed") {
          return <span className="text-text-secondary">{dict.none}</span>;
        }
        return (
          <Button size="sm" disabled={busy} onClick={() => onRetry(row)}>
            {t.retry}
          </Button>
        );
      },
    },
  ];

  return (
    <DataTable
      caption={caption ?? t.deliveriesTitle}
      columns={columns}
      rows={rows}
      rowKey={(row) => row.id}
      totalLabel={`${dict.total}: ${rows.length}`}
    />
  );
}
