// СГЕНЕРИРОВАНО: packages/design/sync.mjs — НЕ РЕДАКТИРОВАТЬ.
// Источник: packages/design/shared/ui/Alert.tsx
// Изменения вносите в источник и запускайте: node packages/design/sync.mjs

/**
 * Alert / Notice — сообщение о состоянии системы (GOV.UK-подобная врезка:
 * акцентная полоса слева + тинт + текст, без иконок-украшений).
 *
 * Доступность:
 *  - tone="critical" → role="alert" + aria-live="assertive": ошибка прерывает
 *    чтение, потому что пользователь должен узнать о ней немедленно;
 *  - остальные тона → role="status" + aria-live="polite": не перебивают;
 *  - `live={false}` отключает объявление вовсе — для врезок, отрендеренных
 *    вместе со страницей (объявлять статику при загрузке — шум);
 *  - тон продублирован СЛОВАМИ в `label` (например «Ошибка», «Внимание») —
 *    цвет и полоса не единственные носители смысла (SC 1.4.1).
 *
 * Контраст текста: --text-primary поверх тинта (≈13:1 в обеих темах) — см. Badge.
 */

import type { ReactNode } from "react";

export type AlertTone = "info" | "success" | "warning" | "critical";

const TONE: Record<AlertTone, { tint: string; bar: string }> = {
  info: { tint: "bg-[var(--tint-info)]", bar: "border-l-info" },
  success: { tint: "bg-[var(--tint-success)]", bar: "border-l-brand-green" },
  warning: { tint: "bg-[var(--tint-warning)]", bar: "border-l-warning" },
  critical: { tint: "bg-[var(--tint-critical)]", bar: "border-l-brand-red" },
};

type AlertProps = {
  tone?: AlertTone;
  /**
   * Текстовое название тона («Ошибка», «Внимание», «Готово») — обязательное
   * дублирование смысла словами. Берётся из словаря вызывающего приложения.
   */
  label: string;
  /** Заголовок сообщения. */
  heading?: ReactNode;
  /** Объявлять ли изменение содержимого скринридеру. */
  live?: boolean;
  className?: string;
  children?: ReactNode;
};

export default function Alert({
  tone = "info",
  label,
  heading,
  live = true,
  className,
  children,
}: AlertProps) {
  const isCritical = tone === "critical";
  const liveProps = live
    ? ({
        role: isCritical ? "alert" : "status",
        "aria-live": isCritical ? "assertive" : "polite",
      } as const)
    : {};

  return (
    <div
      {...liveProps}
      className={`rounded-control border-l-4 ${TONE[tone].bar} ${TONE[tone].tint} px-4 py-3 text-[var(--text-primary)] ${className ?? ""}`}
    >
      {/* Название тона словами: видимо зрячим и слышимо скринридером */}
      <p className="text-caption font-bold uppercase tracking-[0.08em]">
        {label}
      </p>
      {heading ? (
        <p className="mt-1 text-small font-bold">{heading}</p>
      ) : null}
      {children ? (
        <div className="mt-1 text-small leading-relaxed">{children}</div>
      ) : null}
    </div>
  );
}
