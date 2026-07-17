"use client";

// СГЕНЕРИРОВАНО: packages/design/sync.mjs — НЕ РЕДАКТИРОВАТЬ.
// Источник: packages/design/shared/ui/Field.tsx
// Изменения вносите в источник и запускайте: node packages/design/sync.mjs
/**
 * Поля ввода общего слоя. API и уровень доступности намеренно повторяют
 * admin/src/components/admin/fields.tsx (эталон качества в проекте):
 * label связан с контролом через htmlFor/id, ошибка — текстом под полем
 * И через aria-describedby + aria-invalid, обязательность — «*» + aria-required.
 *
 * Отличие от admin/fields.tsx: здесь нет привязки к @/lib/admin-forms (тип
 * I18nInput) и к словарю консоли, поэтому примитивы работают в обоих
 * приложениях. Формы консоли остаются на admin/fields.tsx — см. отчёт.
 *
 * Ошибка не кодируется одним лишь красным цветом (SC 1.4.1): всегда есть текст.
 */

import { useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from "react";

/** Общие классы контрола: 4px радиус, 1px граница, без тени. */
const CONTROL =
  "w-full rounded-control border border-[var(--border-subtle)] " +
  "bg-[var(--surface-raised)] px-3 py-2 text-small text-[var(--text-primary)] " +
  "outline-none placeholder:text-text-secondary/70 disabled:opacity-60";

const CONTROL_INVALID = "border-brand-red";

/**
 * Обёртка поля: подпись + подсказка + слот контрола + ошибка.
 * Используется, когда нужен нестандартный контрол (например, группа радио):
 * Field отдаёт готовые id через render-проп.
 */
export function Field({
  label,
  hint,
  error,
  required,
  children,
}: {
  label: string;
  hint?: string;
  error?: string;
  required?: boolean;
  children: (ids: { id: string; describedBy?: string }) => ReactNode;
}) {
  const id = useId();
  const errId = `${id}-err`;
  return (
    <div>
      <LabelRow htmlFor={id} label={label} required={required} hint={hint} />
      {children({ id, describedBy: error ? errId : undefined })}
      <ErrorText id={errId} error={error} />
    </div>
  );
}

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
      <label htmlFor={htmlFor} className="text-small font-semibold">
        {label}
        {required ? (
          // aria-hidden: обязательность уже озвучена через aria-required на
          // контроле — иначе скринридер прочитает «звёздочка» второй раз.
          <span aria-hidden="true" className="ml-0.5 text-brand-red">
            *
          </span>
        ) : null}
      </label>
      {hint ? <span className="text-caption text-text-secondary">{hint}</span> : null}
    </div>
  );
}

function ErrorText({ id, error }: { id: string; error?: string }) {
  if (!error) return null;
  return (
    <p id={id} className="mt-1 text-caption font-semibold text-brand-red">
      {error}
    </p>
  );
}

type BaseFieldProps = {
  label: string;
  error?: string;
  hint?: string;
  /** Моноширинный ввод для кодов/координат (--font-data). */
  mono?: boolean;
};

/** Однострочный ввод. */
export function Input({
  label,
  error,
  hint,
  mono,
  className,
  required,
  ...rest
}: BaseFieldProps & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId();
  const errId = `${id}-err`;
  return (
    <div>
      <LabelRow htmlFor={id} label={label} required={required} hint={hint} />
      <input
        id={id}
        required={required}
        aria-required={required || undefined}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errId : undefined}
        className={`${CONTROL} ${error ? CONTROL_INVALID : ""} ${
          mono ? "font-mono" : ""
        } ${className ?? ""}`}
        {...rest}
      />
      <ErrorText id={errId} error={error} />
    </div>
  );
}

/** Многострочный ввод. */
export function Textarea({
  label,
  error,
  hint,
  mono,
  className,
  required,
  rows = 4,
  ...rest
}: BaseFieldProps & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const id = useId();
  const errId = `${id}-err`;
  return (
    <div>
      <LabelRow htmlFor={id} label={label} required={required} hint={hint} />
      <textarea
        id={id}
        rows={rows}
        required={required}
        aria-required={required || undefined}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errId : undefined}
        className={`${CONTROL} ${error ? CONTROL_INVALID : ""} ${
          mono ? "font-mono" : ""
        } ${className ?? ""}`}
        {...rest}
      />
      <ErrorText id={errId} error={error} />
    </div>
  );
}

export type SelectOption = { value: string; label: string };

/**
 * Группа опций — рендерится нативным {@code <optgroup>}.
 *
 * Группировка нужна там, где плоский список теряет смысл: станции обязаны быть
 * сгруппированы по линиям, иначе в сети из нескольких десятков станций выбрать
 * нужную невозможно. Нативный optgroup, а не свой listbox: он бесплатно даёт
 * правильную семантику для скринридера и родной выпадающий список на мобильных.
 */
export type SelectGroup = { label: string; options: SelectOption[] };

function isGroup(item: SelectOption | SelectGroup): item is SelectGroup {
  return "options" in item;
}

/** Выпадающий список. Принимает плоский список опций либо группы (optgroup). */
export function Select({
  label,
  error,
  hint,
  className,
  required,
  options,
  ...rest
}: BaseFieldProps &
  SelectHTMLAttributes<HTMLSelectElement> & {
    options: (SelectOption | SelectGroup)[];
  }) {
  const id = useId();
  const errId = `${id}-err`;
  return (
    <div>
      <LabelRow htmlFor={id} label={label} required={required} hint={hint} />
      <select
        id={id}
        required={required}
        aria-required={required || undefined}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errId : undefined}
        className={`${CONTROL} ${error ? CONTROL_INVALID : ""} ${className ?? ""}`}
        {...rest}
      >
        {options.map((item) =>
          isGroup(item) ? (
            <optgroup key={item.label} label={item.label}>
              {item.options.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </optgroup>
          ) : (
            <option key={item.value} value={item.value}>
              {item.label}
            </option>
          ),
        )}
      </select>
      <ErrorText id={errId} error={error} />
    </div>
  );
}

/** Чекбокс с подписью. */
export function Checkbox({
  label,
  className,
  ...rest
}: { label: string } & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId();
  return (
    <div className="flex items-center gap-2">
      <input
        id={id}
        type="checkbox"
        className={`h-4 w-4 accent-[var(--brand-navy)] ${className ?? ""}`}
        {...rest}
      />
      <label htmlFor={id} className="text-small font-semibold">
        {label}
      </label>
    </div>
  );
}
