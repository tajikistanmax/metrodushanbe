"use client";

/**
 * Форма создания/редактирования станции. Валидация зеркалит серверную:
 * полнота языков имени, статус из перечня, координаты [lon, lat] — два числа.
 * Теги доступности — группа чекбоксов; описание (i18n) — необязательно, но при
 * заполнении требуются все три языка.
 */

import { useState } from "react";
import { useI18n } from "../I18nProvider";
import { createStation, updateStation } from "@/lib/admin-actions";
import {
  ACCESSIBILITY_FEATURES,
  EMPTY_I18N,
  STATION_STATUSES,
  type ActionError,
  type I18nInput,
} from "@/lib/admin-forms";
import type { AccessibilityFeature, Station } from "@/lib/types";
import { i18nComplete, isBlank, parseNumber } from "@/lib/validate";
import {
  CheckboxField,
  FormActions,
  I18nField,
  SelectField,
  ServerError,
  TextField,
} from "./fields";

type Props = {
  row: Station | null;
  onSuccess: (verb: "created" | "updated") => void;
  onCancel: () => void;
};

export default function StationForm({ row, onSuccess, onCancel }: Props) {
  const { dict } = useI18n();
  const editing = row !== null;

  const [code, setCode] = useState(row?.code ?? "");
  const [name, setName] = useState<I18nInput>(
    row ? { tg: row.name.tg, ru: row.name.ru, en: row.name.en } : { ...EMPTY_I18N },
  );
  const [status, setStatus] = useState<string>(row?.status ?? "planned");
  const [lon, setLon] = useState(row ? String(row.coordinates[0]) : "");
  const [lat, setLat] = useState(row ? String(row.coordinates[1]) : "");
  const [isTransfer, setIsTransfer] = useState(row?.isTransfer ?? false);
  const [accessibility, setAccessibility] = useState<string[]>(
    row?.accessibility ?? [],
  );
  const [description, setDescription] = useState<I18nInput>({ ...EMPTY_I18N });

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [serverError, setServerError] = useState<ActionError | null>(null);

  const statusOptions = STATION_STATUSES.map((s) => ({
    value: s,
    label: dict.status[s],
  }));

  function toggleFeature(f: AccessibilityFeature, on: boolean) {
    setAccessibility((prev) =>
      on ? [...new Set([...prev, f])] : prev.filter((x) => x !== f),
    );
  }

  const descriptionTouched =
    !isBlank(description.tg) || !isBlank(description.ru) || !isBlank(description.en);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (!editing && isBlank(code)) next.code = dict.form.errRequired;
    if (!i18nComplete(name)) next.name = dict.form.errI18nIncomplete;
    const lonN = parseNumber(lon);
    const latN = parseNumber(lat);
    if (lonN === null || latN === null) next.coordinates = dict.form.errCoordinates;
    if (descriptionTouched && !i18nComplete(description)) {
      next.description = dict.form.errI18nIncomplete;
    }
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    setBusy(true);
    setServerError(null);
    const coordinates: [number, number] = [lonN as number, latN as number];
    const common = {
      name,
      status,
      coordinates,
      isTransfer,
      accessibility,
      ...(descriptionTouched ? { description } : {}),
    };
    const result = editing
      ? await updateStation(row.code, common)
      : await createStation({ code: code.trim(), ...common });
    setBusy(false);
    if (result.ok) {
      onSuccess(editing ? "updated" : "created");
    } else {
      setServerError(result.error);
    }
  }

  return (
    <form onSubmit={submit} className="grid gap-4" noValidate>
      {serverError ? (
        <ServerError
          code={serverError.code}
          message={serverError.message}
          details={serverError.details}
        />
      ) : null}

      <TextField
        label={dict.colCode}
        value={code}
        onChange={setCode}
        error={errors.code}
        required={!editing}
        disabled={editing}
        hint={editing ? dict.form.hintCodeImmutable : undefined}
        mono
        placeholder="STN-01"
      />

      <I18nField
        legend={dict.colName}
        value={name}
        onChange={setName}
        error={errors.name}
        required
      />

      <SelectField
        label={dict.colStatus}
        value={status}
        onChange={setStatus}
        options={statusOptions}
        required
      />

      <fieldset
        className={`rounded-control border ${
          errors.coordinates ? "border-brand-red" : "border-[var(--border-subtle)]"
        } px-3 pb-3 pt-2`}
      >
        <legend className="px-1 text-sm font-semibold">
          {dict.form.fieldCoordinates}
          <span aria-hidden="true" className="ml-0.5 text-brand-red">
            *
          </span>
        </legend>
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField label={dict.form.fieldLon} value={lon} onChange={setLon} required />
          <TextField label={dict.form.fieldLat} value={lat} onChange={setLat} required />
        </div>
        {errors.coordinates ? (
          <p className="mt-1 text-xs font-semibold text-brand-red">
            {errors.coordinates}
          </p>
        ) : (
          <p className="mt-1 text-xs text-text-secondary">
            {dict.form.hintCoordinates}
          </p>
        )}
      </fieldset>

      <CheckboxField
        label={dict.colTransfer}
        checked={isTransfer}
        onChange={setIsTransfer}
      />

      <fieldset className="rounded-control border border-[var(--border-subtle)] px-3 pb-3 pt-2">
        <legend className="px-1 text-sm font-semibold">
          {dict.colAccessibility}
        </legend>
        <div className="flex flex-wrap gap-x-5 gap-y-2">
          {ACCESSIBILITY_FEATURES.map((f) => (
            <CheckboxField
              key={f}
              label={dict.accessibility[f]}
              checked={accessibility.includes(f)}
              onChange={(on) => toggleFeature(f, on)}
            />
          ))}
        </div>
      </fieldset>

      <I18nField
        legend={`${dict.form.fieldDescription} (${dict.form.optional})`}
        value={description}
        onChange={setDescription}
        error={errors.description}
        multiline
      />

      <FormActions onCancel={onCancel} busy={busy} />
    </form>
  );
}
