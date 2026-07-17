/**
 * Бейдж/тег — компактная метка состояния или категории.
 *
 * Цвет НЕ единственный носитель смысла (WCAG 2.2 SC 1.4.1):
 *  - подпись обязательна (children) — тон лишь дублирует её;
 *  - точка-индикатор (dot) — вспомогательный сигнал, aria-hidden.
 *
 * Контраст: текст всегда --text-primary поверх тинта. Тинты подобраны так, что
 * в светлой теме подложка остаётся почти белой (navy на ней ≈13:1), а в тёмной —
 * почти navy (#f2f5f8 на ней ≈13:1). Поэтому цветной текст на цветном фоне,
 * который обычно и проваливает AA, здесь не используется вовсе.
 */

import type { ReactNode } from "react";

export type BadgeTone =
  | "neutral"
  | "info"
  | "success"
  | "warning"
  | "critical";

const TINT: Record<BadgeTone, string> = {
  neutral: "bg-[var(--tint-neutral)]",
  info: "bg-[var(--tint-info)]",
  success: "bg-[var(--tint-success)]",
  warning: "bg-[var(--tint-warning)]",
  critical: "bg-[var(--tint-critical)]",
};

/** Цвет точки-индикатора: единственное место, где тон читается цветом. */
const DOT: Record<BadgeTone, string> = {
  neutral: "bg-[var(--status-neutral)]",
  info: "bg-info",
  success: "bg-brand-green",
  warning: "bg-warning",
  critical: "bg-brand-red",
};

type BadgeProps = {
  tone?: BadgeTone;
  /** Показать точку-индикатор перед подписью. */
  dot?: boolean;
  className?: string;
  children: ReactNode;
};

export default function Badge({
  tone = "neutral",
  dot = false,
  className,
  children,
}: BadgeProps) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-chip ${TINT[tone]} px-2 py-0.5 text-caption font-semibold text-[var(--text-primary)] ${className ?? ""}`}
    >
      {dot ? (
        <span
          aria-hidden="true"
          className={`h-2 w-2 shrink-0 rounded-full ${DOT[tone]}`}
        />
      ) : null}
      {children}
    </span>
  );
}
