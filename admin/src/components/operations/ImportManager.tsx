"use client";

import { useId, useRef, useState } from "react";
import {
  getImportErrors,
  getImportJobs,
} from "@/lib/admin-actions";
import {
  IMPORT_KINDS,
  IMPORT_TARGET_STATUSES,
  type ActionError,
  type ImportFareActive,
  type ImportKindInput,
} from "@/lib/admin-forms";
import { LANGS, formatDateTime } from "@/lib/i18n";
import { uploadImport } from "@/lib/import-client";
import type {
  ImportError,
  ImportFormat,
  ImportJob,
  ImportJobType,
  ImportPage,
} from "@/lib/types";
import DataTable, { type Column } from "../DataTable";
import { useI18n } from "../I18nProvider";
import StateNotice from "../StateNotice";
import Pager from "../admin/Pager";
import { SelectField, ServerError, TextField } from "../admin/fields";
import { useToast } from "../admin/ToastProvider";
import { Alert, Badge, Button, Card } from "@/shared/ui";

type ImportManagerProps = {
  data: ImportPage | null;
  error: string | null;
  /** Роль superadmin: импорт меняет справочники сети целиком. */
  canImport: boolean;
};

type ErrorState = {
  jobId: string;
  loading: boolean;
  data: ImportError[] | null;
  error: string | null;
} | null;

/**
 * Лента заданий: первая страница приходит с сервера, дальше листает клиент —
 * тот же приём, что в очередях доставки (NotificationsManager/WebhooksManager).
 */
type JobsState = {
  loading: boolean;
  page: ImportPage | null;
  error: string | null;
};

/** accept для <input type="file"> по виду импорта. */
const ZIP_ACCEPT = ".zip,application/zip,application/x-zip-compressed";
const ACCEPT: Record<ImportKindInput, string> = {
  geojson: ".json,.geojson,application/json,application/geo+json",
  gtfs: ZIP_ACCEPT,
  "gtfs-fares": ZIP_ACCEPT,
  csv: ".csv,text/csv",
};

const STATUS_TONE: Record<
  string,
  "neutral" | "info" | "success" | "warning" | "critical"
> = {
  success: "success",
  partial: "warning",
  failed: "critical",
  pending: "info",
  running: "info",
};

function formatTone(format: string): "neutral" | "info" {
  return format === "geojson" ? "neutral" : "info";
}

/**
 * Импорт тарифов — это цены, а не топология: в ленте он обязан читаться иначе,
 * чем импорт сети, иначе два разных по последствиям джоба выглядят одинаково
 * (format у них общий — gtfs).
 */
function typeTone(type: string): "neutral" | "warning" {
  return type === "fare_gtfs" ? "warning" : "neutral";
}

export default function ImportManager({
  data,
  error,
  canImport,
}: ImportManagerProps) {
  const { lang, dict } = useI18n();
  const toast = useToast();
  const t = dict.operations;
  const fileId = useId();
  const fileRef = useRef<HTMLInputElement>(null);

  const [format, setFormat] = useState<ImportKindInput>("geojson");
  const [file, setFile] = useState<File | null>(null);
  const [sourceName, setSourceName] = useState("");
  const [feedLang, setFeedLang] = useState("");
  const [targetStatus, setTargetStatus] = useState("active");
  const [riderCategories, setRiderCategories] = useState("");
  const [fareActive, setFareActive] = useState<ImportFareActive>("");
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<ActionError | null>(null);
  const [errors, setErrors] = useState<ErrorState>(null);
  const [jobs, setJobs] = useState<JobsState>({
    loading: false,
    page: data,
    error,
  });

  async function loadJobsPage(next: number) {
    setJobs((current) => ({ ...current, loading: true }));
    const result = await getImportJobs(next);
    setJobs({ loading: false, page: result.data, error: result.error });
  }

  const resetFile = () => {
    setFile(null);
    setSourceName("");
    if (fileRef.current) fileRef.current.value = "";
  };

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file) {
      setFormError({ code: "import.file_required", message: t.invalidFile });
      return;
    }
    setBusy(true);
    setFormError(null);

    const source = sourceName.trim() || file.name;
    const result = await uploadImport(format, file, {
      sourceName: source,
      lang: format === "gtfs" || format === "gtfs-fares" ? feedLang : undefined,
      status: format === "gtfs" ? targetStatus : undefined,
      riderCategories: format === "gtfs-fares" ? riderCategories : undefined,
      active: format === "gtfs-fares" ? fareActive : undefined,
    });

    setBusy(false);
    if (!result.ok) {
      setFormError(result.error);
      toast.error(`${result.error.message} (${result.error.code})`);
      return;
    }
    toast.success(`${t.importDone}: ${result.data.status}`);
    resetFile();
    // Не router.refresh(): лента живёт в локальном состоянии (иначе листалка
    // сбрасывалась бы на серверную страницу), и refresh обновил бы только проп,
    // оставив на экране прежний список. Свежий джоб — всегда на первой странице.
    await loadJobsPage(0);
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
        <span className="font-mono text-caption" title={job.id}>
          {job.id.slice(0, 8)}…
        </span>
      ),
    },
    {
      // Что импортировали — важнее формата: сеть и тарифы приходят одним ZIP.
      key: "type",
      header: t.colType,
      cell: (job) => (
        <Badge tone={typeTone(job.type)}>
          {t.types[job.type as ImportJobType] ?? job.type}
        </Badge>
      ),
    },
    {
      key: "format",
      header: t.colFormat,
      cell: (job) => (
        <Badge tone={formatTone(job.format)}>
          {t.formats[job.format as ImportFormat] ?? job.format}
        </Badge>
      ),
    },
    {
      key: "status",
      header: t.status,
      cell: (job) => (
        <Badge tone={STATUS_TONE[job.status] ?? "info"} dot>
          {job.status}
        </Badge>
      ),
    },
    {
      key: "source",
      header: t.source,
      cell: (job) => job.sourceName || dict.none,
    },
    {
      key: "counts",
      header: t.counts,
      cell: (job) => (
        <span className="grid gap-0.5 text-caption tabular-nums">
          <span>
            {t.created}: {job.createdCount}
          </span>
          <span>
            {t.updated}: {job.updatedCount}
          </span>
          <span className={job.failedCount > 0 ? "font-bold text-brand-red" : ""}>
            {t.failed}: {job.failedCount}
          </span>
        </span>
      ),
    },
    {
      key: "started",
      header: t.started,
      cell: (job) => (job.startedAt ? formatDateTime(job.startedAt, lang) : dict.none),
    },
    {
      key: "finished",
      header: t.finished,
      cell: (job) =>
        job.finishedAt ? formatDateTime(job.finishedAt, lang) : dict.none,
    },
    {
      key: "errors",
      header: dict.colActions,
      align: "right",
      cell: (job) => (
        <Button
          size="sm"
          disabled={errors?.loading && errors.jobId === job.id}
          aria-expanded={errors?.jobId === job.id}
          onClick={() => toggleErrors(job)}
        >
          {errors?.jobId === job.id ? t.hideErrors : t.showErrors}
        </Button>
      ),
    },
  ];

  return (
    <div className="grid gap-5">
      {canImport ? (
        <Card as="section" heading={t.uploadTitle} padding="md">
          <form onSubmit={submit} className="grid gap-4">
            <div className="grid gap-4 md:grid-cols-2">
              <SelectField
                label={t.fieldFormat}
                hint={t.fieldFormatHint}
                value={format}
                onChange={(next) => {
                  setFormat(next as ImportKindInput);
                  // Файл одного вида в другом виде бессмыслен — сбрасываем,
                  // чтобы не отправить ZIP как GeoJSON и фид сети как тарифы.
                  resetFile();
                  setFormError(null);
                }}
                options={IMPORT_KINDS.map((value) => ({
                  value,
                  label: t.kinds[value],
                }))}
                disabled={busy}
              />
              <TextField
                label={t.sourceName}
                hint={t.sourceHint}
                value={sourceName}
                onChange={setSourceName}
                disabled={busy}
              />
            </div>

            <p className="text-caption text-text-secondary">{t.kindHints[format]}</p>

            <div>
              <label htmlFor={fileId} className="mb-1 block text-small font-semibold">
                {t.fileByFormat[format]}
                <span aria-hidden="true" className="ml-0.5 text-brand-red">
                  *
                </span>
              </label>
              <input
                id={fileId}
                ref={fileRef}
                type="file"
                accept={ACCEPT[format]}
                required
                disabled={busy}
                onChange={(event) => {
                  const next = event.target.files?.[0] ?? null;
                  setFile(next);
                  if (next && !sourceName) setSourceName(next.name);
                }}
                className="w-full rounded-control border border-[var(--border-subtle)] bg-[var(--surface-raised)] px-3 py-2 text-small file:mr-3 file:rounded-control file:border-0 file:bg-brand-navy file:px-3 file:py-1 file:font-semibold file:text-surface-light disabled:opacity-60"
              />
              <p className="mt-1 text-caption text-text-secondary">
                {file?.name ?? t.chooseFileByFormat[format]}
              </p>
            </div>

            {/* GTFS одноязычен — язык фида нужен обоим видам GTFS-импорта. */}
            {format === "gtfs" || format === "gtfs-fares" ? (
              <div className="grid gap-4 md:grid-cols-2">
                <SelectField
                  label={t.fieldFeedLang}
                  hint={t.fieldFeedLangHint}
                  value={feedLang}
                  onChange={setFeedLang}
                  options={[
                    { value: "", label: t.feedLangAuto },
                    ...LANGS.map((code) => ({
                      value: code,
                      label: dict.form[
                        code === "tg" ? "langTg" : code === "ru" ? "langRu" : "langEn"
                      ],
                    })),
                  ]}
                  disabled={busy}
                />
                {/* Статуса жизненного цикла в GTFS нет — он про сеть, а не про тарифы. */}
                {format === "gtfs" ? (
                  <SelectField
                    label={t.fieldTargetStatus}
                    hint={t.fieldTargetStatusHint}
                    value={targetStatus}
                    onChange={setTargetStatus}
                    options={IMPORT_TARGET_STATUSES.map((value) => ({
                      value,
                      label: dict.status[value],
                    }))}
                    disabled={busy}
                  />
                ) : (
                  <SelectField
                    label={t.fieldFareActive}
                    hint={t.fieldFareActiveHint}
                    value={fareActive}
                    onChange={(next) => setFareActive(next as ImportFareActive)}
                    options={[
                      { value: "", label: t.fareActiveAuto },
                      { value: "true", label: t.fareActiveYes },
                      { value: "false", label: t.fareActiveNo },
                    ]}
                    disabled={busy}
                  />
                )}
              </div>
            ) : null}

            {/* Категории пассажира и пробелы GTFS Fares v2 — только для тарифов. */}
            {format === "gtfs-fares" ? (
              <div className="grid gap-4">
                <TextField
                  label={t.fieldRiderCategories}
                  hint={t.fieldRiderCategoriesHint}
                  value={riderCategories}
                  onChange={setRiderCategories}
                  disabled={busy}
                />
                <Alert tone="warning" label={t.severities.warning} live={false}>
                  <p>{t.faresGapsLead}</p>
                </Alert>
              </div>
            ) : null}

            {formError ? <ServerError {...formError} /> : null}
            <div className="flex justify-end">
              <Button type="submit" variant="primary" disabled={busy}>
                {busy ? t.importing : t.runImport}
              </Button>
            </div>
          </form>
        </Card>
      ) : null}

      <section aria-labelledby="import-jobs-title" className="grid gap-3">
        <h2 id="import-jobs-title" className="text-title-s font-bold">
          {t.jobsTitle}
        </h2>
        {jobs.error ? (
          <StateNotice kind="error" detail={jobs.error} />
        ) : !jobs.page || jobs.page.items.length === 0 ? (
          <StateNotice kind="empty" />
        ) : (
          <DataTable
            caption={t.jobsTitle}
            columns={columns}
            rows={jobs.page.items}
            rowKey={(job) => job.id}
            totalLabel={`${dict.total}: ${jobs.page.totalElements}`}
          />
        )}

        {jobs.page && !jobs.error ? (
          <Pager
            page={jobs.page.page}
            totalPages={jobs.page.totalPages}
            totalElements={jobs.page.totalElements}
            size={jobs.page.size}
            busy={jobs.loading || busy}
            onPage={loadJobsPage}
          />
        ) : null}
      </section>

      {errors ? (
        <ImportReport
          state={errors}
          onClose={() => setErrors(null)}
        />
      ) : null}
    </div>
  );
}

/**
 * Отчёт задания: ошибки и предупреждения РАЗДЕЛЕНЫ.
 *
 * severity=warning у GTFS — это не сбой, а список недостающих переводов
 * (язык фида подставлен вместо tg/ru/en). Свалить их в одну кучу с ошибками
 * значит либо утопить настоящие ошибки, либо выдать нормальный импорт за
 * поломку.
 */
function ImportReport({
  state,
  onClose,
}: {
  state: NonNullable<ErrorState>;
  onClose: () => void;
}) {
  const { lang, dict } = useI18n();
  const t = dict.operations;

  const items = state.data ?? [];
  const warnings = items.filter((item) => item.severity === "warning");
  const hardErrors = items.filter((item) => item.severity !== "warning");

  return (
    <Card
      as="section"
      heading={t.errorsTitle}
      padding="md"
      actions={<Button onClick={onClose}>{dict.actions.close}</Button>}
    >
      <div aria-live="polite" className="grid gap-4">
        {state.loading ? (
          <p role="status" className="text-small text-text-secondary">
            {dict.loading}
          </p>
        ) : state.error ? (
          <StateNotice kind="error" detail={state.error} />
        ) : items.length === 0 ? (
          <p className="text-small text-text-secondary">{t.noErrors}</p>
        ) : (
          <>
            <section aria-labelledby="import-hard-errors" className="grid gap-2">
              <h3 id="import-hard-errors" className="text-small font-bold">
                {t.errorsOnlyTitle} ({hardErrors.length})
              </h3>
              {hardErrors.length === 0 ? (
                <p className="text-small text-text-secondary">{t.noHardErrors}</p>
              ) : (
                <ul className="grid gap-2">
                  {hardErrors.map((item) => (
                    <li key={item.id}>
                      <Alert
                        tone="critical"
                        label={t.severities.error}
                        heading={item.featureRef}
                        live={false}
                      >
                        <p>{item.message}</p>
                        <p className="mt-1 text-caption text-text-secondary">
                          {formatDateTime(item.at, lang)}
                        </p>
                      </Alert>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section aria-labelledby="import-warnings" className="grid gap-2">
              <h3 id="import-warnings" className="text-small font-bold">
                {t.warningsTitle} ({warnings.length})
              </h3>
              <p className="text-caption text-text-secondary">{t.warningsLead}</p>
              {warnings.length === 0 ? (
                <p className="text-small text-text-secondary">{t.noWarnings}</p>
              ) : (
                <ul className="grid gap-2">
                  {warnings.map((item) => (
                    <li key={item.id}>
                      <Alert
                        tone="warning"
                        label={t.severities.warning}
                        heading={item.featureRef}
                        live={false}
                      >
                        <p>{item.message}</p>
                        <p className="mt-1 text-caption text-text-secondary">
                          {formatDateTime(item.at, lang)}
                        </p>
                      </Alert>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </>
        )}
      </div>
    </Card>
  );
}
