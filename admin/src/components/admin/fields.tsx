"use client";

/**
 * Доступные примитивы полей формы (WCAG 2.2): каждый label связан с контролом
 * через htmlFor/id, ошибки — текстом под полем и через aria-describedby +
 * aria-invalid. Обязательность помечается «*» и aria-required.
 *
 * Все контролы контролируемые; стили — брендовые токены, тёмная тема наследуется.
 */

import { useId, type ReactNode } from "react";
import { useI18n } from "../I18nProvider";
import type { I18nInput } from "@/lib/admin-forms";
import { Alert, Button } from "@/shared/ui";

// Граница поля — --border-strong: --border-subtle (0.12) на белом даёт ≈1.4:1
// к подложке, ниже 3:1 из SC 1.4.11 для границ контролов.
const CONTROL =
  "w-full rounded-control border border-[var(--border-strong)] bg-[var(--surface-raised)] px-3 py-2 text-small text-[var(--text-primary)] outline-none focus-visible:outline-3 disabled:opacity-60";
const CONTROL_INVALID = "border-brand-red";

function LabelRow({
  htmlFor,
  label,
  required,
  hint,
}: {
  htmlFor: string;
  label: string;
  required?: boolean;
  hint?: string;
}) {
  return (
    <div className="mb-1 flex items-baseline justify-between gap-2">
      <label htmlFor={htmlFor} className="text-sm font-semibold">
        {label}
        {required ? (
          <span aria-hidden="true" className="ml-0.5 text-brand-red">
            *
          </span>
        ) : null}
      </label>
      {hint ? (
        <span className="text-xs text-text-secondary">{hint}</span>
      ) : null}
    </div>
  );
}

function ErrorText({ id, error }: { id: string; error?: string }) {
  if (!error) return null;
  return (
    <p id={id} className="mt-1 text-xs font-semibold text-brand-red">
      {error}
    </p>
  );
}

type BaseProps = {
  label: string;
  value: string;
  onChange: (v: string) => void;
  error?: string;
  required?: boolean;
  hint?: string;
  disabled?: boolean;
  placeholder?: string;
};

export function TextField({
  label,
  value,
  onChange,
  error,
  required,
  hint,
  disabled,
  placeholder,
  mono,
  type = "text",
  autoComplete,
}: BaseProps & {
  mono?: boolean;
  /**
   * "password" маскирует ввод; "datetime-local" даёт нативный выбор даты и
   * времени (значение — "YYYY-MM-DDTHH:mm" в локальной зоне, без смещения).
   */
  type?: "text" | "password" | "datetime-local";
  autoComplete?: string;
}) {
  const id = useId();
  const errId = `${id}-err`;
  return (
    <div>
      <LabelRow htmlFor={id} label={label} required={required} hint={hint} />
      <input
        id={id}
        type={type}
        autoComplete={autoComplete}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        required={required}
        placeholder={placeholder}
        aria-required={required || undefined}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errId : undefined}
        className={`${CONTROL} ${error ? CONTROL_INVALID : ""} ${
          mono ? "font-mono" : ""
        }`}
      />
      <ErrorText id={errId} error={error} />
    </div>
  );
}

export function TextareaField({
  label,
  value,
  onChange,
  error,
  required,
  hint,
  disabled,
  placeholder,
  rows = 4,
}: BaseProps & { rows?: number }) {
  const id = useId();
  const errId = `${id}-err`;
  return (
    <div>
      <LabelRow htmlFor={id} label={label} required={required} hint={hint} />
      <textarea
        id={id}
        value={value}
        rows={rows}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        required={required}
        placeholder={placeholder}
        aria-required={required || undefined}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errId : undefined}
        className={`${CONTROL} ${error ? CONTROL_INVALID : ""}`}
      />
      <ErrorText id={errId} error={error} />
    </div>
  );
}

export function SelectField({
  label,
  value,
  onChange,
  options,
  error,
  required,
  hint,
  disabled,
}: BaseProps & { options: { value: string; label: string }[] }) {
  const id = useId();
  const errId = `${id}-err`;
  return (
    <div>
      <LabelRow htmlFor={id} label={label} required={required} hint={hint} />
      <select
        id={id}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        aria-required={required || undefined}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errId : undefined}
        className={`${CONTROL} ${error ? CONTROL_INVALID : ""}`}
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      <ErrorText id={errId} error={error} />
    </div>
  );
}

export function CheckboxField({
  label,
  checked,
  onChange,
  disabled,
}: {
  label: string;
  checked: boolean;
  onChange: (v: boolean) => void;
  disabled?: boolean;
}) {
  const id = useId();
  return (
    <div className="flex items-center gap-2">
      <input
        id={id}
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        disabled={disabled}
        className="h-4 w-4 accent-[var(--brand-navy)]"
      />
      <label htmlFor={id} className="text-sm font-semibold">
        {label}
      </label>
    </div>
  );
}

/**
 * Тройка полей одного i18n-значения (tg/ru/en). Ошибка — общая для группы
 * (если хотя бы один язык пуст). Легенда — семантический <fieldset>.
 */
export function I18nField({
  legend,
  value,
  onChange,
  error,
  required,
  multiline,
}: {
  legend: string;
  value: I18nInput;
  onChange: (v: I18nInput) => void;
  error?: string;
  required?: boolean;
  multiline?: boolean;
}) {
  const { dict } = useI18n();
  const errId = useId();
  const langs: { key: keyof I18nInput; label: string }[] = [
    { key: "tg", label: dict.form.langTg },
    { key: "ru", label: dict.form.langRu },
    { key: "en", label: dict.form.langEn },
  ];
  return (
    <fieldset
      className={`rounded-control border ${
        error ? "border-brand-red" : "border-[var(--border-subtle)]"
      } px-3 pb-3 pt-2`}
      aria-describedby={error ? errId : undefined}
    >
      <legend className="px-1 text-sm font-semibold">
        {legend}
        {required ? (
          <span aria-hidden="true" className="ml-0.5 text-brand-red">
            *
          </span>
        ) : null}
      </legend>
      <div className="grid gap-2">
        {langs.map(({ key, label }) => (
          <label key={key} className="grid gap-1">
            <span className="text-xs text-text-secondary">{label}</span>
            {multiline ? (
              <textarea
                lang={key}
                rows={2}
                value={value[key]}
                onChange={(e) => onChange({ ...value, [key]: e.target.value })}
                aria-label={`${legend} — ${label}`}
                className={CONTROL}
              />
            ) : (
              <input
                lang={key}
                type="text"
                value={value[key]}
                onChange={(e) => onChange({ ...value, [key]: e.target.value })}
                aria-label={`${legend} — ${label}`}
                className={CONTROL}
              />
            )}
          </label>
        ))}
      </div>
      {error ? (
        <p id={errId} className="mt-1 text-xs font-semibold text-brand-red">
          {error}
        </p>
      ) : null}
    </fieldset>
  );
}

/** Панель ошибки серверного envelope (code + message + details). */
export function ServerError({
  code,
  message,
  details,
}: {
  code: string;
  message: string;
  details?: unknown;
}) {
  // Врезка ошибки — на примитиве Alert: он даёт role="alert"/aria-live,
  // акцентную полосу и обязательное дублирование тона словом. Прежний вариант
  // нёс тон только цветом (text-brand-red на bg-brand-red/10 — в тёмной теме
  // ≈2.4:1), см. packages/design/shared/ui/Alert.tsx.
  return (
    <Alert tone="critical" label={message}>
      <p className="font-mono text-caption text-text-secondary">{code}</p>
      {renderDetails(details)}
    </Alert>
  );
}

function renderDetails(details: unknown): ReactNode {
  if (!details) return null;
  if (Array.isArray(details)) {
    return (
      <ul className="mt-1 list-disc pl-5 text-xs text-text-secondary">
        {details.map((d, i) => (
          <li key={i}>{formatDetail(d)}</li>
        ))}
      </ul>
    );
  }
  if (typeof details === "object") {
    return (
      <p className="mt-1 font-mono text-xs text-text-secondary">
        {JSON.stringify(details)}
      </p>
    );
  }
  return (
    <p className="mt-1 font-mono text-xs text-text-secondary">
      {String(details)}
    </p>
  );
}

function formatDetail(d: unknown): string {
  if (d && typeof d === "object") {
    const obj = d as Record<string, unknown>;
    if ("field" in obj || "message" in obj) {
      return `${obj.field ?? ""}${obj.field && obj.message ? ": " : ""}${
        obj.message ?? ""
      }`.trim();
    }
    return JSON.stringify(d);
  }
  return String(d);
}

/** Ряд кнопок формы: Отмена / Сохранить. */
export function FormActions({
  onCancel,
  busy,
  submitLabel,
}: {
  onCancel: () => void;
  busy: boolean;
  submitLabel?: string;
}) {
  const { dict } = useI18n();
  return (
    <div className="flex justify-end gap-2 pt-1">
      <Button onClick={onCancel} disabled={busy}>
        {dict.actions.cancel}
      </Button>
      {/* type="submit" обязателен: у примитива дефолт "button" */}
      <Button type="submit" variant="primary" disabled={busy}>
        {busy ? dict.actions.saving : submitLabel ?? dict.actions.save}
      </Button>
    </div>
  );
}
