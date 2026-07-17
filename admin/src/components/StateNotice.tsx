"use client";

/**
 * Уведомление о состоянии данных: ошибка загрузки или пустой список.
 * Используется, когда серверный запрос вернул error или пустой массив.
 */

import { Alert } from "@/shared/ui";
import { useI18n } from "./I18nProvider";

type StateNoticeProps = {
  kind: "error" | "empty";
  /** Техническая причина (только для kind="error"). */
  detail?: string | null;
};

/**
 * Радиус приведён к шкале (был rounded-xl=12 при .console-card=8 у элемента
 * той же роли). Ошибка построена на примитиве Alert: он уже даёт полосу-акцент,
 * тинт, role="alert"/aria-live и обязательное дублирование тона СЛОВОМ —
 * прежняя врезка несла тон только цветом (text-brand-red на bg-brand-red/10,
 * в тёмной теме ≈2.4:1).
 */
export default function StateNotice({ kind, detail }: StateNoticeProps) {
  const { dict } = useI18n();

  if (kind === "empty") {
    // Пусто — не ошибка: нейтральная поверхность, role="status".
    return (
      <p
        role="status"
        className="rounded-panel border border-[var(--border-subtle)] bg-[var(--surface-raised)] px-4 py-6 text-center text-small text-text-secondary"
      >
        {dict.empty}
      </p>
    );
  }

  return (
    <Alert tone="critical" label={dict.loadError} heading={dict.loadErrorHint}>
      {detail ? (
        <p className="font-mono text-caption text-text-secondary">{detail}</p>
      ) : null}
    </Alert>
  );
}
