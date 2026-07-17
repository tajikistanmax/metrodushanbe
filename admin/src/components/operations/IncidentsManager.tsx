"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import {
  createIncident,
  transitionIncident,
  updateIncident,
} from "@/lib/admin-actions";
import {
  INCIDENT_CATEGORIES,
  INCIDENT_SEVERITIES,
  type ActionError,
  type IncidentCategoryInput,
  type IncidentCreateBody,
  type IncidentSeverityInput,
} from "@/lib/admin-forms";
import { formatDateTime } from "@/lib/i18n";
import type {
  Incident,
  IncidentCategory,
  IncidentSeverity,
  IncidentStats,
  IncidentStatus,
} from "@/lib/types";
import DataTable, { type Column } from "../DataTable";
import { useI18n } from "../I18nProvider";
import StateNotice from "../StateNotice";
import Modal from "../admin/Modal";
import Toolbar from "../admin/Toolbar";
import {
  FormActions,
  SelectField,
  ServerError,
  TextField,
  TextareaField,
} from "../admin/fields";
import { useToast } from "../admin/ToastProvider";

type Editor = {
  /** null — регистрация нового инцидента. */
  originalCode: string | null;
  body: IncidentCreateBody;
};

type Transition = {
  incident: Incident;
  status: IncidentStatus | "";
  resolution: string;
};

type IncidentsManagerProps = {
  data: Incident[] | null;
  error: string | null;
  stats: IncidentStats | null;
  /** false — роль viewer: только чтение. */
  canWrite: boolean;
};

const STATUS_FILTERS: (IncidentStatus | "all")[] = [
  "all",
  "open",
  "acknowledged",
  "in_progress",
  "resolved",
  "closed",
];

/** Цвет плашки критичности; текст дублирует смысл — цвет не единственный носитель. */
const SEVERITY_STYLE: Record<IncidentSeverity, string> = {
  low: "bg-[var(--table-head-bg)] text-text-secondary",
  medium: "bg-info/15 text-info",
  high: "bg-warning/15 text-warning",
  critical: "bg-brand-red/15 text-brand-red",
};

const STATUS_STYLE: Record<IncidentStatus, string> = {
  open: "bg-brand-red/15 text-brand-red",
  acknowledged: "bg-warning/15 text-warning",
  in_progress: "bg-info/15 text-info",
  resolved: "bg-brand-green/15 text-brand-green",
  closed: "bg-[var(--table-head-bg)] text-text-secondary",
};

/**
 * ISO → значение для <input type="datetime-local"> ("YYYY-MM-DDTHH:mm").
 * Поле работает в локальной зоне браузера, поэтому смещение снимаем вручную:
 * toISOString() дал бы UTC и сдвинул бы показанное оператору время.
 */
function toLocalInput(iso: string): string {
  const date = new Date(iso);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

/** Значение datetime-local (локальная зона) → ISO со смещением. */
function fromLocalInput(local: string): string {
  return local ? new Date(local).toISOString() : "";
}

function emptyBody(): IncidentCreateBody {
  return {
    category: "technical",
    severity: "medium",
    title: "",
    description: "",
    lineCode: "",
    stationCode: "",
    assignedTo: "",
    occurredAt: new Date().toISOString(),
  };
}

function bodyFromRow(row: Incident): IncidentCreateBody {
  return {
    category: row.category,
    severity: row.severity,
    title: row.title,
    description: row.description,
    lineCode: row.lineCode ?? "",
    stationCode: row.stationCode ?? "",
    assignedTo: row.assignedTo ?? "",
    occurredAt: row.occurredAt,
  };
}

export default function IncidentsManager({
  data,
  error,
  stats,
  canWrite,
}: IncidentsManagerProps) {
  const { lang, dict } = useI18n();
  const router = useRouter();
  const toast = useToast();
  const [filter, setFilter] = useState<IncidentStatus | "all">("all");
  const [editor, setEditor] = useState<Editor | null>(null);
  const [transition, setTransition] = useState<Transition | null>(null);
  const [busy, setBusy] = useState(false);
  const [serverError, setServerError] = useState<ActionError | null>(null);

  const t = dict.incidents;

  const patchBody = (patch: Partial<IncidentCreateBody>) => {
    setEditor((current) =>
      current ? { ...current, body: { ...current.body, ...patch } } : current,
    );
  };

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!editor) return;
    if (!editor.body.title.trim() || !editor.body.description.trim()) {
      setServerError({ code: "form.invalid", message: dict.form.fixErrors });
      return;
    }
    setBusy(true);
    setServerError(null);
    const result =
      editor.originalCode === null
        ? await createIncident(editor.body)
        : await updateIncident(editor.originalCode, editor.body);
    setBusy(false);
    if (!result.ok) {
      setServerError(result.error);
      return;
    }
    toast.success(editor.originalCode === null ? dict.toast.created : dict.toast.updated);
    setEditor(null);
    router.refresh();
  };

  const submitTransition = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!transition || !transition.status) return;
    setBusy(true);
    setServerError(null);
    const result = await transitionIncident(transition.incident.code, {
      status: transition.status,
      resolution: transition.resolution.trim() || undefined,
    });
    setBusy(false);
    if (!result.ok) {
      setServerError(result.error);
      return;
    }
    toast.success(dict.toast.updated);
    setTransition(null);
    router.refresh();
  };

  const rows = (data ?? []).filter(
    (row) => filter === "all" || row.status === filter,
  );

  const columns: Column<Incident>[] = [
    {
      key: "code",
      header: t.colCode,
      rowHeader: true,
      cell: (row) => <span className="font-mono text-xs">{row.code}</span>,
    },
    {
      key: "title",
      header: t.colTitle,
      cell: (row) => (
        <div className="max-w-xs">
          <p className="font-semibold">{row.title}</p>
          <p className="mt-1 text-xs text-text-secondary">
            {t.categories[row.category]}
          </p>
        </div>
      ),
    },
    {
      key: "severity",
      header: t.colSeverity,
      cell: (row) => (
        <span
          className={`rounded-full px-2.5 py-1 text-xs font-bold ${SEVERITY_STYLE[row.severity]}`}
        >
          {t.severities[row.severity]}
        </span>
      ),
    },
    {
      key: "status",
      header: t.colStatus,
      cell: (row) => (
        <span
          className={`rounded-full px-2.5 py-1 text-xs font-bold ${STATUS_STYLE[row.status]}`}
        >
          {t.statuses[row.status]}
        </span>
      ),
    },
    {
      key: "where",
      header: t.colWhere,
      cell: (row) => (
        <span className="font-mono text-xs">
          {row.stationCode ?? row.lineCode ?? t.networkWide}
        </span>
      ),
    },
    {
      key: "assignee",
      header: t.colAssignee,
      cell: (row) =>
        row.assignedTo ? (
          <span className="font-mono text-xs">{row.assignedTo}</span>
        ) : (
          <span className="text-text-secondary">{t.unassigned}</span>
        ),
    },
    {
      key: "occurred",
      header: t.colOccurred,
      cell: (row) => formatDateTime(row.occurredAt, lang),
    },
    {
      key: "actions",
      header: dict.colActions,
      align: "right",
      cell: (row) => {
        if (!canWrite) {
          return <span className="text-text-secondary">{dict.none}</span>;
        }
        return (
          <div className="flex justify-end gap-2">
            <button
              type="button"
              disabled={busy || row.status === "closed"}
              onClick={() => {
                setServerError(null);
                setEditor({ originalCode: row.code, body: bodyFromRow(row) });
              }}
              className="rounded-lg border border-card-border px-3 py-1.5 text-xs font-bold transition-colors hover:bg-[var(--table-row-hover)] disabled:opacity-40"
            >
              {dict.actions.edit}
            </button>
            <button
              type="button"
              // Пустой allowedTransitions = терминальное состояние: кнопке нечего делать.
              disabled={busy || row.allowedTransitions.length === 0}
              onClick={() => {
                setServerError(null);
                setTransition({ incident: row, status: "", resolution: "" });
              }}
              className="rounded-lg bg-brand-navy px-3 py-1.5 text-xs font-bold text-surface-light transition-opacity hover:opacity-90 disabled:opacity-40"
            >
              {t.transitionTitle}
            </button>
          </div>
        );
      },
    },
  ];

  return (
    <div className="grid gap-4">
      {stats ? (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-7">
          <StatTile label={t.statTodayLabel} value={stats.today} accent />
          <StatTile label={t.statOpenLabel} value={stats.open} accent />
          {INCIDENT_CATEGORIES.map((category) => (
            <StatTile
              key={category}
              label={t.categories[category as IncidentCategory]}
              value={stats.byCategory[category as IncidentCategory] ?? 0}
            />
          ))}
        </div>
      ) : null}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div role="group" aria-label={t.colStatus} className="flex flex-wrap gap-1.5">
          {STATUS_FILTERS.map((value) => (
            <button
              key={value}
              type="button"
              aria-pressed={filter === value}
              onClick={() => setFilter(value)}
              className={
                filter === value
                  ? "rounded-full bg-brand-navy px-3 py-1.5 text-xs font-bold text-surface-light"
                  : "rounded-full border border-card-border px-3 py-1.5 text-xs font-semibold transition-colors hover:bg-[var(--table-row-hover)]"
              }
            >
              {value === "all" ? t.filterAll : t.statuses[value]}
            </button>
          ))}
        </div>
        {canWrite ? (
          <Toolbar
            createLabel={t.createTitle}
            onCreate={() => {
              setServerError(null);
              setEditor({ originalCode: null, body: emptyBody() });
            }}
          />
        ) : null}
      </div>

      {error ? (
        <StateNotice kind="error" detail={error} />
      ) : rows.length === 0 ? (
        <StateNotice kind="empty" />
      ) : (
        <DataTable
          caption={t.title}
          columns={columns}
          rows={rows}
          rowKey={(row) => row.code}
          totalLabel={`${dict.total}: ${rows.length}`}
        />
      )}

      <Modal
        open={editor !== null}
        onClose={() => setEditor(null)}
        busy={busy}
        title={editor?.originalCode === null ? t.createTitle : t.editTitle}
      >
        {editor ? (
          <form onSubmit={submit} className="grid gap-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <SelectField
                label={t.fieldCategory}
                value={editor.body.category}
                onChange={(category) =>
                  patchBody({ category: category as IncidentCategoryInput })
                }
                options={INCIDENT_CATEGORIES.map((category) => ({
                  value: category,
                  label: t.categories[category as IncidentCategory],
                }))}
                required
                disabled={busy}
              />
              <SelectField
                label={t.fieldSeverity}
                value={editor.body.severity}
                onChange={(severity) =>
                  patchBody({ severity: severity as IncidentSeverityInput })
                }
                options={INCIDENT_SEVERITIES.map((severity) => ({
                  value: severity,
                  label: t.severities[severity as IncidentSeverity],
                }))}
                required
                disabled={busy}
              />
            </div>
            <TextField
              label={t.fieldTitle}
              value={editor.body.title}
              onChange={(title) => patchBody({ title })}
              required
              disabled={busy}
            />
            <TextareaField
              label={t.fieldDescription}
              value={editor.body.description}
              onChange={(description) => patchBody({ description })}
              required
              disabled={busy}
            />
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField
                label={t.fieldLine}
                value={editor.body.lineCode ?? ""}
                onChange={(lineCode) => patchBody({ lineCode })}
                mono
                disabled={busy}
              />
              <TextField
                label={t.fieldStation}
                value={editor.body.stationCode ?? ""}
                onChange={(stationCode) => patchBody({ stationCode })}
                mono
                disabled={busy}
              />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField
                label={t.fieldAssignee}
                value={editor.body.assignedTo ?? ""}
                onChange={(assignedTo) => patchBody({ assignedTo })}
                hint={t.fieldAssigneeHint}
                mono
                disabled={busy}
              />
              <TextField
                label={t.fieldOccurredAt}
                type="datetime-local"
                value={toLocalInput(editor.body.occurredAt)}
                onChange={(value) =>
                  patchBody({ occurredAt: fromLocalInput(value) })
                }
                required
                disabled={busy}
              />
            </div>
            {serverError ? <ServerError {...serverError} /> : null}
            <FormActions onCancel={() => setEditor(null)} busy={busy} />
          </form>
        ) : null}
      </Modal>

      <Modal
        open={transition !== null}
        onClose={() => setTransition(null)}
        busy={busy}
        title={t.transitionTitle}
      >
        {transition ? (
          <form onSubmit={submitTransition} className="grid gap-4">
            <p className="text-sm text-text-secondary">
              <span className="font-mono text-xs">{transition.incident.code}</span>
              {" — "}
              {transition.incident.title}
            </p>
            <SelectField
              label={t.colStatus}
              value={transition.status}
              onChange={(status) =>
                setTransition((current) =>
                  current ? { ...current, status: status as IncidentStatus } : current,
                )
              }
              // Список переходов приходит с backend — он же их и валидирует,
              // поэтому UI не может предложить недопустимое действие.
              options={transition.incident.allowedTransitions.map((status) => ({
                value: status,
                label: t.statuses[status],
              }))}
              required
              disabled={busy}
            />
            {transition.status === "resolved" ? (
              <TextareaField
                label={t.fieldResolution}
                value={transition.resolution}
                onChange={(resolution) =>
                  setTransition((current) =>
                    current ? { ...current, resolution } : current,
                  )
                }
                hint={t.fieldResolutionHint}
                required
                disabled={busy}
              />
            ) : null}
            {serverError ? <ServerError {...serverError} /> : null}
            <FormActions onCancel={() => setTransition(null)} busy={busy} />
          </form>
        ) : null}
      </Modal>
    </div>
  );
}

function StatTile({
  label,
  value,
  accent,
}: {
  label: string;
  value: number;
  accent?: boolean;
}) {
  return (
    <div className="console-card px-3 py-2.5">
      <p className="text-[11px] font-semibold text-text-secondary">{label}</p>
      <p
        className={`text-xl font-extrabold ${accent ? "text-brand-navy dark:text-surface-light" : ""}`}
      >
        {value}
      </p>
    </div>
  );
}
