"use client";

/**
 * Доступная таблица данных (WCAG 2.2): семантические <table>/<caption>/<th scope>.
 * Значения-заголовки строк помечаются scope="row". Обёртка прокручивается по
 * горизонтали на узких экранах, страница по горизонтали не скроллится.
 *
 * Дженерик-примитив: конкретные экраны (LinesTable, StationsTable, …) описывают
 * колонки через `Column<T>` и передают строки. Задел под будущий CRUD:
 * колонку действий можно добавить последним `Column`.
 */

import type { ReactNode } from "react";

export type Column<T> = {
  /** Стабильный ключ колонки (для React key). */
  key: string;
  /** Локализованный заголовок колонки. */
  header: string;
  /** Рендер ячейки строки. */
  cell: (row: T) => ReactNode;
  align?: "left" | "right" | "center";
  /** Пометить ячейку как заголовок строки (scope="row"). */
  rowHeader?: boolean;
  /** Скрыть заголовок визуально, оставив для скринридеров. */
  srOnlyHeader?: boolean;
};

type DataTableProps<T> = {
  /** Описание таблицы для скринридеров (<caption>). */
  caption: string;
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  /** Подпись «Всего: N» под таблицей. */
  totalLabel?: string;
};

const ALIGN: Record<NonNullable<Column<unknown>["align"]>, string> = {
  left: "text-left",
  right: "text-right",
  center: "text-center",
};

export default function DataTable<T>({
  caption,
  columns,
  rows,
  rowKey,
  totalLabel,
}: DataTableProps<T>) {
  return (
    <div className="console-card overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-sm">
          <caption className="sr-only">{caption}</caption>
          <thead>
            <tr className="bg-[var(--table-head-bg)]">
              {columns.map((col) => (
                <th
                  key={col.key}
                  scope="col"
                  className={`border-b border-[var(--table-border)] px-4 py-3 text-[11px] font-bold uppercase tracking-[0.08em] text-text-secondary ${
                    ALIGN[col.align ?? "left"]
                  } ${col.srOnlyHeader ? "sr-only" : ""}`}
                >
                  {col.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr
                key={rowKey(row)}
                className="transition-colors hover:bg-[var(--table-row-hover)]"
              >
                {columns.map((col) => {
                  const content = col.cell(row);
                  const base = `border-b border-[var(--table-border)] px-4 py-3 align-middle ${
                    ALIGN[col.align ?? "left"]
                  }`;
                  return col.rowHeader ? (
                    <th
                      key={col.key}
                      scope="row"
                      className={`${base} font-semibold`}
                    >
                      {content}
                    </th>
                  ) : (
                    <td key={col.key} className={base}>
                      {content}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {totalLabel ? (
        <p className="border-t border-[var(--table-border)] px-4 py-2.5 text-xs text-text-secondary">
          {totalLabel}
        </p>
      ) : null}
    </div>
  );
}
