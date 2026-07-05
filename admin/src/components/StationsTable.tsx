"use client";

/**
 * Таблица станций (read-only): код, название, статус, линии, пересадка,
 * элементы доступности. Локализация — на клиенте.
 */

import type { ReactNode } from "react";
import { pickName } from "@/lib/i18n";
import type { Station } from "@/lib/types";
import { useI18n } from "./I18nProvider";
import DataTable, { type Column } from "./DataTable";
import LineBadge from "./LineBadge";
import StatusBadge from "./StatusBadge";
import StateNotice from "./StateNotice";

type StationsTableProps = {
  data: Station[] | null;
  error: string | null;
  /** Соответствие кода линии её цвету (для бейджей). */
  lineColors?: Record<string, string>;
  /** Необязательная колонка действий (admin CRUD). */
  actions?: (row: Station) => ReactNode;
};

export default function StationsTable({
  data,
  error,
  lineColors = {},
  actions,
}: StationsTableProps) {
  const { lang, dict } = useI18n();

  if (error) {
    return <StateNotice kind="error" detail={error} />;
  }
  if (!data || data.length === 0) {
    return <StateNotice kind="empty" />;
  }

  const columns: Column<Station>[] = [
    {
      key: "code",
      header: dict.colCode,
      rowHeader: true,
      cell: (s) => <span className="font-mono text-xs">{s.code}</span>,
    },
    {
      key: "name",
      header: dict.colName,
      cell: (s) => pickName(s.name, lang),
    },
    {
      key: "status",
      header: dict.colStatus,
      cell: (s) => <StatusBadge status={s.status} />,
    },
    {
      key: "lines",
      header: dict.colLines,
      cell: (s) => (
        <span className="flex flex-wrap gap-1">
          {s.lines.map((code) => (
            <LineBadge key={code} code={code} colorHex={lineColors[code]} />
          ))}
        </span>
      ),
    },
    {
      key: "transfer",
      header: dict.colTransfer,
      align: "center",
      cell: (s) => (s.isTransfer ? dict.yes : dict.no),
    },
    {
      key: "accessibility",
      header: dict.colAccessibility,
      cell: (s) =>
        s.accessibility.length === 0 ? (
          <span className="text-text-secondary">{dict.none}</span>
        ) : (
          <span className="flex flex-wrap gap-1">
            {s.accessibility.map((a) => (
              <span
                key={a}
                className="rounded-full bg-[var(--chip-bg)] px-2 py-0.5 text-xs"
              >
                {dict.accessibility[a] ?? a}
              </span>
            ))}
          </span>
        ),
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
    <DataTable<Station>
      caption={dict.stationsTitle}
      columns={columns}
      rows={data}
      rowKey={(s) => s.code}
      totalLabel={`${dict.total}: ${data.length}`}
    />
  );
}
