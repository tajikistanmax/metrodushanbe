"use client";

/**
 * Уведомление о состоянии данных: ошибка загрузки или пустой список.
 * Используется, когда серверный запрос вернул error или пустой массив.
 */

import { useI18n } from "./I18nProvider";

type StateNoticeProps = {
  kind: "error" | "empty";
  /** Техническая причина (только для kind="error"). */
  detail?: string | null;
};

export default function StateNotice({ kind, detail }: StateNoticeProps) {
  const { dict } = useI18n();

  if (kind === "empty") {
    return (
      <p
        role="status"
        className="rounded-xl border border-[var(--card-border)] bg-[var(--card-bg)] px-4 py-6 text-center text-sm text-text-secondary"
      >
        {dict.empty}
      </p>
    );
  }

  return (
    <div
      role="alert"
      className="rounded-xl border border-brand-red/40 bg-brand-red/10 px-4 py-4 text-sm"
    >
      <p className="font-semibold text-brand-red">{dict.loadError}</p>
      <p className="mt-1 text-text-secondary">{dict.loadErrorHint}</p>
      {detail ? (
        <p className="mt-2 font-mono text-xs text-text-secondary/80">{detail}</p>
      ) : null}
    </div>
  );
}
