"use client";

import { useId, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import {
  getImportErrors,
  importNetworkGeoJson,
} from "@/lib/admin-actions";
import type { ActionError } from "@/lib/admin-forms";
import { formatDateTime } from "@/lib/i18n";
import type { ImportError, ImportJob, ImportPage } from "@/lib/types";
import DataTable, { type Column } from "../DataTable";
import { useI18n } from "../I18nProvider";
import StateNotice from "../StateNotice";
import { ServerError, TextField } from "../admin/fields";
import { useToast } from "../admin/ToastProvider";

type ImportManagerProps = {
  data: ImportPage | null;
  error: string | null;
};

type ErrorState = {
  jobId: string;
  loading: boolean;
  data: ImportError[] | null;
  error: string | null;
} | null;

function statusClass(status: string): string {
  if (status === "success") return "bg-brand-green/15 text-brand-green";
  if (status === "failed") return "bg-brand-red/15 text-brand-red";
  if (status === "partial") return "bg-warning/15 text-warning";
  return "bg-info/15 text-info";
}

export default function ImportManager({ data, error }: ImportManagerProps) {
  const { lang, dict } = useI18n();
  const router = useRouter();
  const toast = useToast();
  const fileId = useId();
  const fileRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [sourceName, setSourceName] = useState("");
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<ActionError | null>(null);
  const [errors, setErrors] = useState<ErrorState>(null);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file) {
      setFormError({
        code: "import.file_required",
        message: dict.operations.invalidFile,
      });
      return;
    }
    setBusy(true);
    setFormError(null);
    const result = await importNetworkGeoJson(
      sourceName.trim() || file.name,
      await file.text(),
    );
    setBusy(false);
    if (!result.ok) {
      setFormError(result.error);
      toast.error(`${result.error.message} (${result.error.code})`);
      return;
    }
    toast.success(`${dict.operations.importDone}: ${result.data.status}`);
    setFile(null);
    setSourceName("");
    if (fileRef.current) fileRef.current.value = "";
    router.refresh();
  }

  async function toggleErrors(job: ImportJob) {
    if (errors?.jobId === job.id) {
      setErrors(null);
      return;
    }
    setErrors({ jobId: job.id, loading: true, data: null, error: null });
    const result = await getImportErrors(job.id);
    setErrors({
      jobId: job.id,
      loading: false,
      data: result.data,
      error: result.error,
    });
  }

  const columns: Column<ImportJob>[] = [
    {
      key: "id",
      header: "ID",
      rowHeader: true,
      cell: (job) => (
        <span className="font-mono text-xs" title={job.id}>
          {job.id.slice(0, 8)}…
        </span>
      ),
    },
    {
      key: "status",
      header: dict.operations.status,
      cell: (job) => (
        <span
          className={`rounded-full px-2.5 py-1 text-xs font-bold ${statusClass(job.status)}`}
        >
          {job.status}
        </span>
      ),
    },
    {
      key: "source",
      header: dict.operations.source,
      cell: (job) => job.sourceName || dict.none,
    },
    {
      key: "counts",
      header: dict.operations.counts,
      cell: (job) => (
        <span className="grid gap-0.5 text-xs tabular-nums">
          <span>{dict.operations.created}: {job.createdCount}</span>
          <span>{dict.operations.updated}: {job.updatedCount}</span>
          <span className={job.failedCount > 0 ? "font-bold text-brand-red" : ""}>
            {dict.operations.failed}: {job.failedCount}
          </span>
        </span>
      ),
    },
    {
      key: "started",
      header: dict.operations.started,
      cell: (job) => job.startedAt ? formatDateTime(job.startedAt, lang) : dict.none,
    },
    {
      key: "finished",
      header: dict.operations.finished,
      cell: (job) => job.finishedAt ? formatDateTime(job.finishedAt, lang) : dict.none,
    },
    {
      key: "errors",
      header: dict.colActions,
      align: "right",
      cell: (job) => (
        <button
          type="button"
          onClick={() => toggleErrors(job)}
          disabled={errors?.loading && errors.jobId === job.id}
          className="rounded-lg border border-[var(--card-border)] px-2.5 py-1 text-xs font-semibold hover:bg-[var(--table-row-hover)] disabled:opacity-50"
        >
          {errors?.jobId === job.id
            ? dict.operations.hideErrors
            : dict.operations.showErrors}
        </button>
      ),
    },
  ];

  return (
    <div className="grid gap-5">
      <form onSubmit={submit} className="console-card grid gap-4 p-4 sm:p-5">
        <h2 className="text-lg font-extrabold">{dict.operations.uploadTitle}</h2>
        <div className="grid gap-4 md:grid-cols-2">
          <TextField
            label={dict.operations.sourceName}
            hint={dict.operations.sourceHint}
            value={sourceName}
            onChange={setSourceName}
            disabled={busy}
          />
          <div>
            <label htmlFor={fileId} className="mb-1 block text-sm font-semibold">
              {dict.operations.file}<span aria-hidden="true" className="ml-0.5 text-brand-red">*</span>
            </label>
            <input
              id={fileId}
              ref={fileRef}
              type="file"
              accept=".json,.geojson,application/json,application/geo+json"
              required
              disabled={busy}
              onChange={(event) => {
                const next = event.target.files?.[0] ?? null;
                setFile(next);
                if (next && !sourceName) setSourceName(next.name);
              }}
              className="w-full rounded-lg border border-[var(--card-border)] bg-[var(--card-bg)] px-3 py-2 text-sm file:mr-3 file:rounded-md file:border-0 file:bg-brand-navy file:px-3 file:py-1 file:font-semibold file:text-surface-light disabled:opacity-60"
            />
            <p className="mt-1 text-xs text-text-secondary">
              {file?.name ?? dict.operations.chooseFile}
            </p>
          </div>
        </div>
        {formError ? <ServerError {...formError} /> : null}
        <div className="flex justify-end">
          <button
            type="submit"
            disabled={busy}
            className="rounded-lg bg-brand-navy px-4 py-2 text-sm font-bold text-surface-light hover:opacity-90 disabled:opacity-50"
          >
            {busy ? dict.operations.importing : dict.operations.runImport}
          </button>
        </div>
      </form>

      <section aria-labelledby="import-jobs-title" className="grid gap-3">
        <h2 id="import-jobs-title" className="text-lg font-extrabold">
          {dict.operations.jobsTitle}
        </h2>
        {error ? (
          <StateNotice kind="error" detail={error} />
        ) : !data || data.items.length === 0 ? (
          <StateNotice kind="empty" />
        ) : (
          <DataTable
            caption={dict.operations.jobsTitle}
            columns={columns}
            rows={data.items}
            rowKey={(job) => job.id}
            totalLabel={`${dict.total}: ${data.totalElements}`}
          />
        )}
      </section>

      {errors ? (
        <section className="console-card grid gap-3 p-4" aria-live="polite">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-lg font-extrabold">{dict.operations.errorsTitle}</h2>
            <button
              type="button"
              onClick={() => setErrors(null)}
              className="rounded-lg border border-[var(--card-border)] px-2.5 py-1 text-xs font-semibold"
            >
              {dict.actions.close}
            </button>
          </div>
          {errors.loading ? (
            <p role="status" className="text-sm text-text-secondary">{dict.loading}</p>
          ) : errors.error ? (
            <StateNotice kind="error" detail={errors.error} />
          ) : !errors.data || errors.data.length === 0 ? (
            <p className="text-sm text-text-secondary">{dict.operations.noErrors}</p>
          ) : (
            <ul className="grid gap-2">
              {errors.data.map((item) => (
                <li key={item.id} className="rounded-lg border border-brand-red/30 bg-brand-red/5 p-3 text-sm">
                  <p className="font-mono text-xs font-bold">{item.featureRef}</p>
                  <p className="mt-1">{item.message}</p>
                  <p className="mt-1 text-xs text-text-secondary">
                    {item.severity} · {formatDateTime(item.at, lang)}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </section>
      ) : null}
    </div>
  );
}
