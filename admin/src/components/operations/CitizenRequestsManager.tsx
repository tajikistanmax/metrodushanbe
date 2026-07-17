"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { updateCitizenRequest } from "@/lib/admin-actions";
import type { ActionError, CitizenRequestUpdateBody } from "@/lib/admin-forms";
import { formatDateTime } from "@/lib/i18n";
import type {
  CitizenRequestAdmin,
  CitizenRequestPriority,
  CitizenRequestStatus,
} from "@/lib/types";
import DataTable, { type Column } from "../DataTable";
import { useI18n } from "../I18nProvider";
import StateNotice from "../StateNotice";
import Modal from "../admin/Modal";
import {
  FormActions,
  SelectField,
  ServerError,
  TextareaField,
  TextField,
} from "../admin/fields";
import { useToast } from "../admin/ToastProvider";

const STATUSES: CitizenRequestStatus[] = [
  "new",
  "in_progress",
  "awaiting_info",
  "resolved",
  "closed",
  "reopened",
];

const TRANSITIONS: Record<CitizenRequestStatus, CitizenRequestStatus[]> = {
  new: ["new", "in_progress"],
  in_progress: ["in_progress", "awaiting_info", "resolved"],
  awaiting_info: ["awaiting_info", "in_progress"],
  resolved: ["resolved", "closed", "reopened"],
  closed: ["closed"],
  reopened: ["reopened", "in_progress", "resolved"],
};

type Editor = {
  request: CitizenRequestAdmin;
  body: CitizenRequestUpdateBody;
};

function StatusPill({ status }: { status: CitizenRequestStatus }) {
  const { dict } = useI18n();
  const color: Record<CitizenRequestStatus, string> = {
    new: "bg-info/15 text-info",
    in_progress: "bg-warning/15 text-warning",
    awaiting_info: "bg-warning/15 text-warning",
    resolved: "bg-brand-green/15 text-brand-green",
    closed: "bg-text-secondary/15 text-text-secondary",
    reopened: "bg-brand-red/15 text-brand-red",
  };
  return (
    <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-bold ${color[status]}`}>
      {dict.operations.requestStatuses[status]}
    </span>
  );
}

function PriorityPill({ priority }: { priority: CitizenRequestPriority }) {
  const { dict } = useI18n();
  const color =
    priority === "high"
      ? "bg-brand-red/15 text-brand-red"
      : priority === "normal"
        ? "bg-info/15 text-info"
        : "bg-text-secondary/15 text-text-secondary";
  return (
    <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-bold ${color}`}>
      {dict.operations.requestPriorities[priority]}
    </span>
  );
}

export default function CitizenRequestsManager({
  data,
  error,
}: {
  data: CitizenRequestAdmin[] | null;
  error: string | null;
}) {
  const { lang, dict } = useI18n();
  const router = useRouter();
  const toast = useToast();
  const [filter, setFilter] = useState<CitizenRequestStatus | "all">("all");
  const [editor, setEditor] = useState<Editor | null>(null);
  const [busy, setBusy] = useState(false);
  const [serverError, setServerError] = useState<ActionError | null>(null);

  const rows = useMemo(
    () =>
      (data ?? []).filter((request) => filter === "all" || request.status === filter),
    [data, filter],
  );

  const open = (request: CitizenRequestAdmin) => {
    setServerError(null);
    setEditor({
      request,
      body: {
        status: request.status,
        response: request.response ?? "",
        assignedTo: request.assignedTo ?? "",
      },
    });
  };

  const patchBody = (patch: Partial<CitizenRequestUpdateBody>) => {
    setEditor((current) =>
      current ? { ...current, body: { ...current.body, ...patch } } : current,
    );
  };

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!editor) return;
    setBusy(true);
    setServerError(null);
    const result = await updateCitizenRequest(editor.request.code, editor.body);
    setBusy(false);
    if (!result.ok) {
      setServerError(result.error);
      return;
    }
    toast.success(dict.toast.updated);
    setEditor(null);
    router.refresh();
  };

  const columns: Column<CitizenRequestAdmin>[] = [
    {
      key: "code",
      header: dict.colCode,
      rowHeader: true,
      cell: (row) => (
        <div>
          <span className="font-mono text-xs">{row.code}</span>
          <span className="mt-1 block text-xs text-text-secondary">
            {formatDateTime(row.createdAt, lang)}
          </span>
        </div>
      ),
    },
    {
      key: "subject",
      header: dict.operations.subject,
      cell: (row) => (
        <div className="max-w-xs">
          <p className="font-semibold">{row.subject}</p>
          <p className="mt-1 text-xs text-text-secondary">
            {dict.operations.requestTypes[row.type]}
          </p>
        </div>
      ),
    },
    {
      key: "priority",
      header: dict.operations.state,
      cell: (row) => (
        <div className="grid justify-items-start gap-1.5">
          <StatusPill status={row.status} />
          <PriorityPill priority={row.priority} />
        </div>
      ),
    },
    {
      key: "assigned",
      header: dict.operations.assignedTo,
      cell: (row) => row.assignedTo || dict.operations.unassigned,
    },
    {
      key: "sla",
      header: dict.operations.resolutionDue,
      cell: (row) => (
        <div className="text-xs">
          <span>{formatDateTime(row.resolutionDueAt, lang)}</span>
          {row.responseSlaBreached || row.resolutionSlaBreached ? (
            <span className="mt-1 block font-bold text-brand-red">
              {dict.operations.slaBreached}
            </span>
          ) : null}
        </div>
      ),
    },
    {
      key: "actions",
      header: dict.colActions,
      align: "right",
      cell: (row) => (
        <button
          type="button"
          onClick={() => open(row)}
          className="rounded-lg border border-[var(--card-border)] px-3 py-1.5 text-xs font-semibold transition-colors hover:bg-[var(--table-row-hover)]"
        >
          {dict.operations.openRequest}
        </button>
      ),
    },
  ];

  const responseRequired =
    editor?.body.status === "awaiting_info" || editor?.body.status === "resolved";

  return (
    <div className="grid gap-4">
      <div className="flex justify-end">
        <label className="flex items-center gap-2 text-sm font-semibold">
          <span>{dict.operations.status}</span>
          <select
            value={filter}
            onChange={(event) => setFilter(event.target.value as CitizenRequestStatus | "all")}
            className="rounded-lg border border-[var(--card-border)] bg-[var(--card-bg)] px-3 py-2 text-sm"
          >
            <option value="all">{dict.operations.filterAll}</option>
            {STATUSES.map((status) => (
              <option key={status} value={status}>
                {dict.operations.requestStatuses[status]}
              </option>
            ))}
          </select>
        </label>
      </div>

      {error ? (
        <StateNotice kind="error" detail={error} />
      ) : rows.length === 0 ? (
        <StateNotice kind="empty" />
      ) : (
        <DataTable
          caption={dict.operations.requestsTitle}
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
        title={`${dict.operations.requestDetails} · ${editor?.request.code ?? ""}`}
      >
        {editor ? (
          <form onSubmit={submit} className="grid gap-4">
            <div className="grid gap-3 rounded-xl bg-[var(--table-row-hover)] p-4 text-sm sm:grid-cols-2">
              <div className="sm:col-span-2">
                <p className="text-xs font-bold uppercase tracking-wide text-text-secondary">
                  {dict.operations.subject}
                </p>
                <p className="mt-1 font-bold">{editor.request.subject}</p>
              </div>
              <div className="sm:col-span-2">
                <p className="text-xs font-bold uppercase tracking-wide text-text-secondary">
                  {dict.operations.message}
                </p>
                <p className="mt-1 whitespace-pre-wrap leading-relaxed">{editor.request.message}</p>
              </div>
              <div>
                <p className="text-xs font-bold uppercase tracking-wide text-text-secondary">
                  {dict.operations.contact}
                </p>
                <p className="mt-1 whitespace-pre-line">
                  {[editor.request.contactName, editor.request.contactEmail, editor.request.contactPhone]
                    .filter(Boolean)
                    .join("\n") || dict.none}
                </p>
              </div>
              <div>
                <p className="text-xs font-bold uppercase tracking-wide text-text-secondary">
                  {dict.operations.lineStation}
                </p>
                <p className="mt-1">
                  {[editor.request.lineCode, editor.request.stationCode].filter(Boolean).join(" / ") || dict.none}
                </p>
              </div>
              <div>
                <p className="text-xs font-bold uppercase tracking-wide text-text-secondary">
                  {dict.operations.responseDue}
                </p>
                <p className={editor.request.responseSlaBreached ? "mt-1 font-bold text-brand-red" : "mt-1"}>
                  {formatDateTime(editor.request.responseDueAt, lang)}
                </p>
              </div>
              <div>
                <p className="text-xs font-bold uppercase tracking-wide text-text-secondary">
                  {dict.operations.resolutionDue}
                </p>
                <p className={editor.request.resolutionSlaBreached ? "mt-1 font-bold text-brand-red" : "mt-1"}>
                  {formatDateTime(editor.request.resolutionDueAt, lang)}
                </p>
              </div>
            </div>

            <SelectField
              label={dict.operations.status}
              value={editor.body.status}
              onChange={(status) => patchBody({ status })}
              options={TRANSITIONS[editor.request.status].map((status) => ({
                value: status,
                label: dict.operations.requestStatuses[status],
              }))}
              required
              disabled={busy}
            />
            <TextField
              label={dict.operations.assignedTo}
              value={editor.body.assignedTo ?? ""}
              onChange={(assignedTo) => patchBody({ assignedTo })}
              disabled={busy}
            />
            <TextareaField
              label={dict.operations.response}
              value={editor.body.response ?? ""}
              onChange={(response) => patchBody({ response })}
              required={responseRequired}
              rows={5}
              disabled={busy}
            />
            {serverError ? <ServerError {...serverError} /> : null}
            <FormActions onCancel={() => setEditor(null)} busy={busy} />
          </form>
        ) : null}
      </Modal>
    </div>
  );
}
