"use client";

/**
 * Форма создания/редактирования линии. Клиентская валидация зеркалит серверную
 * (полнота языков, #RRGGBB, статус из перечня). Код неизменен при редактировании.
 */

import { useState } from "react";
import { useI18n } from "../I18nProvider";
import { createLine, updateLine } from "@/lib/admin-actions";
import { EMPTY_I18N, LINE_STATUSES, type I18nInput } from "@/lib/admin-forms";
import type { Line } from "@/lib/types";
import { i18nComplete, isBlank, isHexColor, parseNumber, parsePath } from "@/lib/validate";
import {
  FormActions,
  I18nField,
  SelectField,
  ServerError,
  TextField,
  TextareaField,
} from "./fields";
import type { ActionError } from "@/lib/admin-forms";

type Props = { row: Line | null; onSuccess: (verb: "created" | "updated") => void; onCancel: () => void };

export default function LineForm({ row, onSuccess, onCancel }: Props) {
  const { dict, lang } = useI18n();
  const editing = row !== null;

  const [code, setCode] = useState(row?.code ?? "");
  const [name, setName] = useState<I18nInput>(
    row ? { tg: row.name.tg, ru: row.name.ru, en: row.name.en } : { ...EMPTY_I18N },
  );
  const [colorHex, setColorHex] = useState(row?.colorHex ?? "#");
  const [status, setStatus] = useState<string>(row?.status ?? "planned");
  const [sortOrder, setSortOrder] = useState(row ? String(row.sortOrder) : "0");
  const [pathText, setPathText] = useState("");

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [serverError, setServerError] = useState<ActionError | null>(null);

  const statusOptions = LINE_STATUSES.map((s) => ({ value: s, label: dict.status[s] }));

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (!editing && isBlank(code)) next.code = dict.form.errRequired;
    if (!i18nComplete(name)) next.name = dict.form.errI18nIncomplete;
    if (!isHexColor(colorHex)) next.colorHex = dict.form.errColorHex;
    const order = parseNumber(sortOrder);
    if (sortOrder.trim() !== "" && order === null) next.sortOrder = dict.form.errNumber;
    const path = parsePath(pathText);
    if (path === null) next.path = dict.form.errCoordinates;
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    setBusy(true);
    setServerError(null);
    const common = {
      name,
      colorHex: colorHex.trim(),
      status,
      ...(order !== null ? { sortOrder: order } : {}),
      ...(path && path.length > 0 ? { path } : {}),
    };
    const result = editing
      ? await updateLine(row.code, common)
      : await createLine({ code: code.trim(), ...common });
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
        placeholder="L1"
      />

      <I18nField
        legend={dict.colName}
        value={name}
        onChange={setName}
        error={errors.name}
        required
      />

      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label={dict.colColor}
          value={colorHex}
          onChange={setColorHex}
          error={errors.colorHex}
          required
          mono
          placeholder="#E21B2D"
        />
        <SelectField
          label={dict.colStatus}
          value={status}
          onChange={setStatus}
          options={statusOptions}
          required
        />
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label={dict.colOrder}
          value={sortOrder}
          onChange={setSortOrder}
          error={errors.sortOrder}
        />
      </div>

      <TextareaField
        label={dict.form.fieldPath}
        value={pathText}
        onChange={setPathText}
        error={errors.path}
        hint={dict.form.hintPath}
        placeholder={"68.78, 38.57\n68.79, 38.58"}
      />

      <p className="text-xs text-text-secondary" lang={lang}>
        {dict.form.hintCoordinates}
      </p>

      <FormActions onCancel={onCancel} busy={busy} />
    </form>
  );
}
