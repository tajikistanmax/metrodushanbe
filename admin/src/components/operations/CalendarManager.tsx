"use client";

import { useId, useState } from "react";
import { useRouter } from "next/navigation";
import {
  createCalendarException,
  deleteCalendarException,
  updateCalendarException,
} from "@/lib/admin-actions";
import {
  SCHEDULE_DAY_TYPES,
  type ActionError,
  type CalendarExceptionBody,
  type ScheduleDayType,
} from "@/lib/admin-forms";
import { formatDateTime, type Lang } from "@/lib/i18n";
import type { CalendarException } from "@/lib/types";
import DataTable, { type Column } from "../DataTable";
import { useI18n } from "../I18nProvider";
import StateNotice from "../StateNotice";
import ConfirmDialog from "../admin/ConfirmDialog";
import Modal from "../admin/Modal";
import RowActions from "../admin/RowActions";
import Toolbar from "../admin/Toolbar";
import {
  CheckboxField,
  FormActions,
  SelectField,
  ServerError,
  TextField,
} from "../admin/fields";
import { useToast } from "../admin/ToastProvider";

type CalendarManagerProps = {
  data: CalendarException[] | null;
  error: string | null;
};

type EditorState = { id: number | null; body: CalendarExceptionBody } | null;

function emptyBody(): CalendarExceptionBody {
  const now = new Date();
  const localDate = [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, "0"),
    String(now.getDate()).padStart(2, "0"),
  ].join("-");
  return {
    exceptionDate: localDate,
    dayType: "holiday",
    descriptionTg: "",
    descriptionRu: "",
    descriptionEn: "",
    isRecurring: false,
  };
}

function bodyFromRow(row: CalendarException): CalendarExceptionBody {
  return {
    exceptionDate: row.exceptionDate,
    dayType: row.dayType as ScheduleDayType,
    descriptionTg: row.descriptionTg ?? "",
    descriptionRu: row.descriptionRu ?? "",
    descriptionEn: row.descriptionEn ?? "",
    isRecurring: row.isRecurring,
  };
}

function localizedDescription(row: CalendarException, lang: Lang): string {
  if (lang === "tg") return row.descriptionTg || row.descriptionRu || row.descriptionEn || "—";
  if (lang === "ru") return row.descriptionRu || row.descriptionTg || row.descriptionEn || "—";
  return row.descriptionEn || row.descriptionRu || row.descriptionTg || "—";
}

export default function CalendarManager({ data, error }: CalendarManagerProps) {
  const { lang, dict } = useI18n();
  const router = useRouter();
  const toast = useToast();
  const dateId = useId();
  const [editor, setEditor] = useState<EditorState>(null);
  const [busy, setBusy] = useState(false);
  const [serverError, setServerError] = useState<ActionError | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<CalendarException | null>(null);
  const [deleteError, setDeleteError] = useState<ActionError | null>(null);

  const dayTypeLabels: Record<ScheduleDayType, string> = {
    weekday: dict.operations.weekday,
    weekend: dict.operations.weekend,
    holiday: dict.operations.holiday,
  };

  function patchBody(patch: Partial<CalendarExceptionBody>) {
    setEditor((current) => current ? { ...current, body: { ...current.body, ...patch } } : current);
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editor) return;
    setBusy(true);
    setServerError(null);
    const result = editor.id === null
      ? await createCalendarException(editor.body)
      : await updateCalendarException(editor.id, editor.body);
    setBusy(false);
    if (!result.ok) {
      setServerError(result.error);
      return;
    }
    toast.success(editor.id === null ? dict.toast.created : dict.toast.updated);
    setEditor(null);
    router.refresh();
  }

  async function confirmDelete() {
    if (!deleteTarget) return;
    setBusy(true);
    setDeleteError(null);
    const result = await deleteCalendarException(deleteTarget.id);
    setBusy(false);
    if (!result.ok) {
      setDeleteError(result.error);
      return;
    }
    toast.success(dict.toast.deleted);
    setDeleteTarget(null);
    router.refresh();
  }

  const columns: Column<CalendarException>[] = [
    {
      key: "date",
      header: dict.operations.date,
      rowHeader: true,
      cell: (row) => <span className="font-mono">{row.exceptionDate}</span>,
    },
    {
      key: "dayType",
      header: dict.operations.dayType,
      cell: (row) => dayTypeLabels[row.dayType as ScheduleDayType] ?? row.dayType,
    },
    {
      key: "description",
      header: dict.operations.description,
      cell: (row) => localizedDescription(row, lang),
    },
    {
      key: "recurring",
      header: dict.operations.recurring,
      cell: (row) => row.isRecurring ? dict.yes : dict.no,
    },
    {
      key: "created",
      header: dict.colCreatedAt,
      cell: (row) => formatDateTime(row.createdAt, lang),
    },
    {
      key: "actions",
      header: dict.colActions,
      align: "right",
      cell: (row) => (
        <RowActions
          entityLabel={row.exceptionDate}
          busy={busy}
          onEdit={() => {
            setServerError(null);
            setEditor({ id: row.id, body: bodyFromRow(row) });
          }}
          onDelete={() => {
            setDeleteError(null);
            setDeleteTarget(row);
          }}
        />
      ),
    },
  ];

  return (
    <div className="grid gap-4">
      <Toolbar onCreate={() => setEditor({ id: null, body: emptyBody() })} />
      {error ? (
        <StateNotice kind="error" detail={error} />
      ) : !data || data.length === 0 ? (
        <StateNotice kind="empty" />
      ) : (
        <DataTable
          caption={dict.operations.calendarTitle}
          columns={columns}
          rows={[...data].sort((a, b) => a.exceptionDate.localeCompare(b.exceptionDate))}
          rowKey={(row) => String(row.id)}
          totalLabel={`${dict.total}: ${data.length}`}
        />
      )}

      <Modal
        open={editor !== null}
        onClose={() => setEditor(null)}
        busy={busy}
        title={editor?.id === null ? dict.operations.newException : dict.operations.editException}
      >
        {editor ? (
          <form onSubmit={submit} className="grid gap-4">
            <div>
              <label htmlFor={dateId} className="mb-1 block text-sm font-semibold">
                {dict.operations.date}<span aria-hidden="true" className="ml-0.5 text-brand-red">*</span>
              </label>
              <input
                id={dateId}
                type="date"
                required
                value={editor.body.exceptionDate}
                onChange={(event) => patchBody({ exceptionDate: event.target.value })}
                disabled={busy}
                className="w-full rounded-lg border border-[var(--card-border)] bg-[var(--card-bg)] px-3 py-2 text-sm outline-none focus-visible:outline-3 disabled:opacity-60"
              />
            </div>
            <SelectField
              label={dict.operations.dayType}
              value={editor.body.dayType}
              onChange={(value) => patchBody({ dayType: value as ScheduleDayType })}
              options={SCHEDULE_DAY_TYPES.map((value) => ({ value, label: dayTypeLabels[value] }))}
              required
              disabled={busy}
            />
            <TextField
              label={dict.operations.descriptionTg}
              value={editor.body.descriptionTg ?? ""}
              onChange={(value) => patchBody({ descriptionTg: value })}
              disabled={busy}
            />
            <TextField
              label={dict.operations.descriptionRu}
              value={editor.body.descriptionRu ?? ""}
              onChange={(value) => patchBody({ descriptionRu: value })}
              disabled={busy}
            />
            <TextField
              label={dict.operations.descriptionEn}
              value={editor.body.descriptionEn ?? ""}
              onChange={(value) => patchBody({ descriptionEn: value })}
              disabled={busy}
            />
            <CheckboxField
              label={dict.operations.recurring}
              checked={editor.body.isRecurring}
              onChange={(value) => patchBody({ isRecurring: value })}
              disabled={busy}
            />
            {serverError ? <ServerError {...serverError} /> : null}
            <FormActions onCancel={() => setEditor(null)} busy={busy} />
          </form>
        ) : null}
      </Modal>

      <ConfirmDialog
        open={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        onConfirm={confirmDelete}
        target={deleteTarget?.exceptionDate ?? ""}
        busy={busy}
        error={deleteError}
      />
    </div>
  );
}
