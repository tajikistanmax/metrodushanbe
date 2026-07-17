"use client";

/**
 * Бейдж статуса линии/станции. Смысл передаётся НЕ только цветом
 * (WCAG 2.2 SC 1.4.1): всегда есть текстовая подпись + форма точки-индикатора.
 */

import { useI18n } from "@/shared/I18nProvider";
import type { LineStatus, StationStatus } from "@/lib/types";

type Status = LineStatus | StationStatus;

/**
 * Цвет точки-индикатора по статусу (только вспомогательный сигнал).
 * Только токены: сырые #8aa1b4/#6b7785 не имели тёмного варианта и на тёмной
 * теме проваливали 3:1 к подложке.
 */
const DOT_COLOR: Record<Status, string> = {
  planned: "var(--status-neutral)",
  under_construction: "var(--warning)",
  testing: "var(--info)",
  active: "var(--brand-green)",
  suspended: "var(--brand-red)",
  temporarily_closed: "var(--warning)",
  decommissioned: "var(--status-retired)",
};

export default function StatusBadge({ status }: { status: Status }) {
  const { dict } = useI18n();
  const label = dict.status[status] ?? status;
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-full bg-[var(--surface-chip)] px-2.5 py-1 text-xs font-semibold">
      <span
        aria-hidden="true"
        className="h-2 w-2 shrink-0 rounded-full"
        style={{ background: DOT_COLOR[status] ?? "var(--status-neutral)" }}
      />
      {label}
    </span>
  );
}
