"use client";

/**
 * Форма создания/редактирования сервисного уведомления. Создаётся в статусе
 * draft; публикация — отдельной кнопкой в таблице (жизненный цикл §6.2.6).
 * Валидация зеркалит серверную: полнота языков title/body, severity из перечня,
 * окно [startsAt, endsAt) с endsAt позже startsAt.
 */

import { useId, useState } from "react";
import { useI18n } from "../I18nProvider";
import { createAlert, updateAlert } from "@/lib/admin-actions";
import {
  ALERT_SEVERITIES,
  EMPTY_I18N,
  TARGET_TYPES,
  type ActionError,
  type AlertTargetBody,
  type I18nInput,
} from "@/lib/admin-forms";
import type { Alert } from "@/lib/types";
import { i18nComplete, isBlank, localToIso, isoToLocal } from "@/lib/validate";
import {
  FormActions,
  I18nField,
  SelectField,
  ServerError,
  TextField,
} from "./fields";

type Props = {
  row: Alert | null;
  onSuccess: (verb: "created" | "updated") => void;
  onCancel: () => void;
};

export default function AlertForm({ row, onSuccess, onCancel }: Props) {
  const { dict } = useI18n();
  const editing = row !== null;
  const startId = useId();
  const endId = useId();

  const [code, setCode] = useState(row?.code ?? "");
  const [severity, setSeverity] = useState<string>(row?.severity ?? "info");
  const [title, setTitle] = useState<I18nInput>(
    row ? { tg: row.title.tg, ru: row.title.ru, en: row.title.en } : { ...EMPTY_I18N },
  );
  const [body, setBody] = useState<I18nInput>(
    row ? { tg: row.body.tg, ru: row.body.ru, en: row.body.en } : { ...EMPTY_I18N },
  );
  const [startsAt, setStartsAt] = useState(isoToLocal(row?.startsAt));
  const [endsAt, setEndsAt] = useState(isoToLocal(row?.endsAt ?? null));
  const [targets, setTargets] = useState<AlertTargetBody[]>(
    row?.targets.map((t) => ({ type: t.type, code: t.code })) ?? [],
  );

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [serverError, setServerError] = useState<ActionError | null>(null);

  const severityOptions = ALERT_SEVERITIES.map((s) => ({
    value: s,
    label: dict.severity[s],
  }));
  const typeOptions = TARGET_TYPES.map((t) => ({
    value: t,
    label: t === "line" ? dict.form.targetLine : dict.form.targetStation,
  }));

  function updateTarget(i: number, patch: Partial<AlertTargetBody>) {
    setTargets((prev) => prev.map((t, idx) => (idx === i ? { ...t, ...patch } : t)));
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (!editing && isBlank(code)) next.code = dict.form.errRequired;
    if (!i18nComplete(title)) next.title = dict.form.errI18nIncomplete;
    if (!i18nComplete(body)) next.body = dict.form.errI18nIncomplete;
    const startIso = localToIso(startsAt);
    if (startIso === null) next.startsAt = dict.form.errRequired;
    const endIso = localToIso(endsAt);
    if (endIso !== null && startIso !== null && Date.parse(endIso) <= Date.parse(startIso)) {
      next.endsAt = dict.form.errDateRange;
    }
    const cleanTargets = targets.filter((t) => !isBlank(t.code));
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    setBusy(true);
    setServerError(null);
    const common = {
      severity,
      title,
      body,
      startsAt: startIso as string,
      ...(endIso ? { endsAt: endIso } : {}),
      ...(cleanTargets.length > 0 ? { targets: cleanTargets } : {}),
    };
    const result = editing
      ? await updateAlert(row.code, common)
      : await createAlert({ code: code.trim(), ...common });
    setBusy(false);
    if (result.ok) {
      onSuccess(editing ? "updated" : "created");
    } else {
      setServerError(result.error);
    }
  }

  const dateInput =
    "w-full rounded-control border border-[var(--border-subtle)] bg-[var(--surface-raised)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none focus-visible:outline-3";

  return (
    <form onSubmit={submit} className="grid gap-4" noValidate>
      {serverError ? (
        <ServerError
          code={serverError.code}
          message={serverError.message}
          details={serverError.details}
        />
      ) : null}

      {!editing ? (
        <p className="rounded-control border border-[var(--border-subtle)] bg-[var(--surface-chip)] px-3 py-2 text-xs text-text-secondary">
          {dict.form.draftHint}
        </p>
      ) : null}

      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label={dict.colCode}
          value={code}
          onChange={setCode}
          error={errors.code}
          required={!editing}
          disabled={editing}
          hint={editing ? dict.form.hintCodeImmutable : undefined}
          mono
          placeholder="ALT-2026-01"
        />
        <SelectField
          label={dict.colSeverity}
          value={severity}
          onChange={setSeverity}
          options={severityOptions}
          required
        />
      </div>

      <I18nField
        legend={dict.colTitle}
        value={title}
        onChange={setTitle}
        error={errors.title}
        required
      />
      <I18nField
        legend={dict.form.fieldBody}
        value={body}
        onChange={setBody}
        error={errors.body}
        required
        multiline
      />

      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor={startId} className="mb-1 block text-sm font-semibold">
            {dict.colStartsAt}
            <span aria-hidden="true" className="ml-0.5 text-brand-red">
              *
            </span>
          </label>
          <input
            id={startId}
            type="datetime-local"
            value={startsAt}
            onChange={(e) => setStartsAt(e.target.value)}
            aria-required
            aria-invalid={errors.startsAt ? true : undefined}
            className={dateInput}
          />
          {errors.startsAt ? (
            <p className="mt-1 text-xs font-semibold text-brand-red">
              {errors.startsAt}
            </p>
          ) : null}
        </div>
        <div>
          <label htmlFor={endId} className="mb-1 block text-sm font-semibold">
            {dict.colEndsAt}{" "}
            <span className="text-xs font-normal text-text-secondary">
              ({dict.form.optional})
            </span>
          </label>
          <input
            id={endId}
            type="datetime-local"
            value={endsAt}
            onChange={(e) => setEndsAt(e.target.value)}
            aria-invalid={errors.endsAt ? true : undefined}
            className={dateInput}
          />
          {errors.endsAt ? (
            <p className="mt-1 text-xs font-semibold text-brand-red">
              {errors.endsAt}
            </p>
          ) : null}
        </div>
      </div>

      <fieldset className="rounded-control border border-[var(--border-subtle)] px-3 pb-3 pt-2">
        <legend className="px-1 text-sm font-semibold">
          {dict.form.fieldTargets}{" "}
          <span className="text-xs font-normal text-text-secondary">
            ({dict.form.optional})
          </span>
        </legend>
        <div className="grid gap-2">
          {targets.map((t, i) => (
            <div key={i} className="flex items-end gap-2">
              <div className="w-36 shrink-0">
                <SelectField
                  label={dict.form.targetType}
                  value={t.type}
                  onChange={(v) => updateTarget(i, { type: v })}
                  options={typeOptions}
                />
              </div>
              <div className="min-w-0 flex-1">
                <TextField
                  label={dict.form.targetCode}
                  value={t.code}
                  onChange={(v) => updateTarget(i, { code: v })}
                  mono
                />
              </div>
              <button
                type="button"
                onClick={() => setTargets((prev) => prev.filter((_, idx) => idx !== i))}
                aria-label={dict.form.removeTarget}
                className="mb-1 rounded-control border border-[var(--border-subtle)] px-3 py-2 text-sm text-brand-red hover:bg-brand-red/10"
              >
                <span aria-hidden="true">×</span>
              </button>
            </div>
          ))}
          <button
            type="button"
            onClick={() => setTargets((prev) => [...prev, { type: "line", code: "" }])}
            className="w-fit rounded-control border border-dashed border-[var(--border-subtle)] px-3 py-1.5 text-xs font-semibold hover:bg-[var(--surface-hover-subtle)]"
          >
            + {dict.form.addTarget}
          </button>
        </div>
      </fieldset>

      <FormActions onCancel={onCancel} busy={busy} />
    </form>
  );
}
