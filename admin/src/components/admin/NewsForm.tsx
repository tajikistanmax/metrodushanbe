"use client";

/**
 * Форма создания/редактирования новости. Создаётся в статусе draft; публикация —
 * отдельной кнопкой (гейт полноты языков BR-CMS-1). Валидация зеркалит серверную:
 * полнота языков title/body; slug латиница/цифры/дефис; URL обложки — http(s).
 */

import { useState } from "react";
import { useI18n } from "../I18nProvider";
import { createNews, updateNews } from "@/lib/admin-actions";
import { EMPTY_I18N, type ActionError, type I18nInput } from "@/lib/admin-forms";
import type { News } from "@/lib/types";
import { i18nComplete, isBlank, isHttpUrl, isSlug } from "@/lib/validate";
import {
  FormActions,
  I18nField,
  ServerError,
  TextField,
} from "./fields";

type Props = {
  row: News | null;
  onSuccess: (verb: "created" | "updated") => void;
  onCancel: () => void;
};

export default function NewsForm({ row, onSuccess, onCancel }: Props) {
  const { dict } = useI18n();
  const editing = row !== null;

  const [slug, setSlug] = useState(row?.slug ?? "");
  const [title, setTitle] = useState<I18nInput>(
    row ? { tg: row.title.tg, ru: row.title.ru, en: row.title.en } : { ...EMPTY_I18N },
  );
  const [body, setBody] = useState<I18nInput>(
    row ? { tg: row.body.tg, ru: row.body.ru, en: row.body.en } : { ...EMPTY_I18N },
  );
  const [coverMediaUrl, setCoverMediaUrl] = useState(row?.coverMediaUrl ?? "");

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [serverError, setServerError] = useState<ActionError | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (!editing) {
      if (isBlank(slug)) next.slug = dict.form.errRequired;
      else if (!isSlug(slug)) next.slug = dict.form.errRequired;
    }
    if (!i18nComplete(title)) next.title = dict.form.errI18nIncomplete;
    if (!i18nComplete(body)) next.body = dict.form.errI18nIncomplete;
    if (!isBlank(coverMediaUrl) && !isHttpUrl(coverMediaUrl)) {
      next.coverMediaUrl = dict.form.errUrl;
    }
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    setBusy(true);
    setServerError(null);
    const common = {
      title,
      body,
      ...(isBlank(coverMediaUrl) ? {} : { coverMediaUrl: coverMediaUrl.trim() }),
    };
    const result = editing
      ? await updateNews(row.slug, common)
      : await createNews({ slug: slug.trim(), ...common });
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

      {!editing ? (
        <p className="rounded-control border border-[var(--border-subtle)] bg-[var(--surface-chip)] px-3 py-2 text-xs text-text-secondary">
          {dict.form.draftHint}
        </p>
      ) : null}

      <TextField
        label={dict.form.fieldSlug}
        value={slug}
        onChange={setSlug}
        error={errors.slug}
        required={!editing}
        disabled={editing}
        hint={editing ? dict.form.hintCodeImmutable : undefined}
        mono
        placeholder="new-station-opened"
      />

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

      <TextField
        label={`${dict.form.fieldCoverUrl} (${dict.form.optional})`}
        value={coverMediaUrl}
        onChange={setCoverMediaUrl}
        error={errors.coverMediaUrl}
        placeholder="https://…"
      />

      <FormActions onCancel={onCancel} busy={busy} />
    </form>
  );
}
