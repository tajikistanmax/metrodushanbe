"use client";

/**
 * Таблица активных сервисных уведомлений (read-only): уровень, заголовок,
 * цели, окно действия. Уровень передаётся не только цветом (WCAG SC 1.4.1) —
 * есть текстовая подпись и точка-индикатор.
 */

import { formatDateTime, pickName } from "@/lib/i18n";
import type { Alert, AlertSeverity } from "@/lib/types";
import { useI18n } from "./I18nProvider";
import DataTable, { type Column } from "./DataTable";
import StateNotice from "./StateNotice";

type AlertsTableProps = {
  data: Alert[] | null;
  error: string | null;
};

const SEVERITY_COLOR: Record<AlertSeverity, string> = {
  info: "var(--info)",
  warning: "var(--warning)",
  critical: "var(--brand-red)",
};

function SeverityBadge({ severity }: { severity: AlertSeverity }) {
  const { dict } = useI18n();
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-full bg-[var(--chip-bg)] px-2.5 py-1 text-xs font-semibold">
      <span
        aria-hidden="true"
        className="h-2 w-2 shrink-0 rounded-full"
        style={{ background: SEVERITY_COLOR[severity] ?? "var(--info)" }}
      />
      {dict.severity[severity] ?? severity}
    </span>
  );
}

export default function AlertsTable({ data, error }: AlertsTableProps) {
  const { lang, dict } = useI18n();

  if (error) {
    return <StateNotice kind="error" detail={error} />;
  }
  if (!data || data.length === 0) {
    return <StateNotice kind="empty" />;
  }

  const columns: Column<Alert>[] = [
    {
      key: "code",
      header: dict.colCode,
      rowHeader: true,
      cell: (a) => <span className="font-mono text-xs">{a.code}</span>,
    },
    {
      key: "severity",
      header: dict.colSeverity,
      cell: (a) => <SeverityBadge severity={a.severity} />,
    },
    {
      key: "title",
      header: dict.colTitle,
      cell: (a) => (
        <span className="block max-w-[28rem]">{pickName(a.title, lang)}</span>
      ),
    },
    {
      key: "targets",
      header: dict.colTargets,
      cell: (a) =>
        a.targets.length === 0 ? (
          <span className="text-text-secondary">{dict.none}</span>
        ) : (
          <span className="flex flex-wrap gap-1">
            {a.targets.map((t) => (
              <span
                key={`${t.type}:${t.code}`}
                title={t.type}
                className="rounded-full bg-[var(--chip-bg)] px-2 py-0.5 font-mono text-xs"
              >
                {t.code}
              </span>
            ))}
          </span>
        ),
    },
    {
      key: "startsAt",
      header: dict.colStartsAt,
      cell: (a) => (
        <span className="whitespace-nowrap tabular-nums">
          {formatDateTime(a.startsAt, lang)}
        </span>
      ),
    },
    {
      key: "endsAt",
      header: dict.colEndsAt,
      cell: (a) => (
        <span className="whitespace-nowrap tabular-nums">
          {a.endsAt ? formatDateTime(a.endsAt, lang) : dict.none}
        </span>
      ),
    },
  ];

  return (
    <DataTable<Alert>
      caption={dict.alertsTitle}
      columns={columns}
      rows={data}
      rowKey={(a) => a.code}
      totalLabel={`${dict.total}: ${data.length}`}
    />
  );
}
