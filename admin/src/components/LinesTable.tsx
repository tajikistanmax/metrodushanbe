"use client";

/**
 * Таблица линий (read-only). Данные приходят с сервера (Server Component
 * страницы), локализация — на клиенте по выбранному языку.
 */

import type { ReactNode } from "react";
import { pickName } from "@/lib/i18n";
import type { Line } from "@/lib/types";
import { useI18n } from "./I18nProvider";
import DataTable, { type Column } from "./DataTable";
import LineBadge from "./LineBadge";
import StatusBadge from "./StatusBadge";
import StateNotice from "./StateNotice";

type LinesTableProps = {
  data: Line[] | null;
  error: string | null;
  /** Необязательная колонка действий (admin CRUD). */
  actions?: (row: Line) => ReactNode;
};

export default function LinesTable({ data, error, actions }: LinesTableProps) {
  const { lang, dict } = useI18n();

  if (error) {
    return <StateNotice kind="error" detail={error} />;
  }
  if (!data || data.length === 0) {
    return <StateNotice kind="empty" />;
  }

  const rows = [...data].sort((a, b) => a.sortOrder - b.sortOrder);

  const columns: Column<Line>[] = [
    {
      key: "code",
      header: dict.colCode,
      rowHeader: true,
      cell: (l) => <LineBadge code={l.code} colorHex={l.colorHex} />,
    },
    {
      key: "name",
      header: dict.colName,
      cell: (l) => pickName(l.name, lang),
    },
    {
      key: "color",
      header: dict.colColor,
      cell: (l) => (
        <span className="inline-flex items-center gap-2">
          <span
            aria-hidden="true"
            className="h-4 w-4 shrink-0 rounded border border-[var(--border-subtle)]"
            style={{ background: l.colorHex }}
          />
          <span className="font-mono text-xs uppercase">{l.colorHex}</span>
        </span>
      ),
    },
    {
      key: "status",
      header: dict.colStatus,
      cell: (l) => <StatusBadge status={l.status} />,
    },
    {
      key: "order",
      header: dict.colOrder,
      align: "right",
      cell: (l) => <span className="tabular-nums">{l.sortOrder}</span>,
    },
    ...(actions
      ? [
          {
            key: "actions",
            header: dict.colActions,
            align: "right" as const,
            cell: actions,
          },
        ]
      : []),
  ];

  return (
    <DataTable<Line>
      caption={dict.linesTitle}
      columns={columns}
      rows={rows}
      rowKey={(l) => l.code}
      totalLabel={`${dict.total}: ${rows.length}`}
    />
  );
}
