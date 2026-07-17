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
import { Badge, Button, Card, type BadgeTone } from "@/shared/ui";

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

/**
 * Тон плашки критичности; текст дублирует смысл — цвет не единственный
 * носитель (SC 1.4.1). Было: своя пара bg-<цвет>/15 + text-<цвет> на каждый
 * ключ — цветной текст на цветном тинте, в тёмной теме ≈1.7–2.4:1.
 * Badge кладёт --text-primary поверх тинта: ≈13:1 в обеих темах.
 */
const SEVERITY_TONE: Record<IncidentSeverity, BadgeTone> = {
  low: "neutral",
  medium: "info",
  high: "warning",
  critical: "critical",
};

const STATUS_TONE: Record<IncidentStatus, BadgeTone> = {
  open: "critical",
  acknowledged: "warning",
  in_progress: "info",
  resolved: "success",
  closed: "neutral",
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
        <Badge tone={SEVERITY_TONE[row.severity]} dot>
          {t.severities[row.severity]}
        </Badge>
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
            <Button
              size="sm"
              disabled={busy || row.status === "closed"}
              onClick={() => {
                setServerError(null);
                setEditor({ originalCode: row.code, body: bodyFromRow(row) });
              }}
            >
              {dict.actions.edit}
            </Button>
            <Button
              size="sm"
              variant="primary"
              // Пустой allowedTransitions = терминальное состояние: кнопке нечего делать.
              disabled={busy || row.allowedTransitions.length === 0}
              onClick={() => {
                setServerError(null);
                setTransition({ incident: row, status: "", resolution: "" });
              }}
            >
              {t.transitionTitle}
            </Button>
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
              // Форма фильтра — как в эталоне (NotificationsManager):
              // rounded-control, а не rounded-full.
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
    <Card as="div" padding="none" className="px-3 py-2.5">
      <p className="text-caption font-semibold text-text-secondary">{label}</p>
      {/* accent подсвечивал числo цветом; --text-primary уже даёт нужный
          контраст в обеих темах, а вес отличает акцентную плитку. */}
      <p className={`text-title-s ${accent ? "font-extrabold" : "font-bold"}`}>
        {value}
      </p>
    </Card>
  );
}
