"use client";

/**
 * Таблица новостей (read-only): дата публикации, заголовок, наличие обложки.
 * Локализация — на клиенте.
 */

import type { ReactNode } from "react";
import { formatDateTime, pickName } from "@/lib/i18n";
import type { News } from "@/lib/types";
import { useI18n } from "./I18nProvider";
import DataTable, { type Column } from "./DataTable";
import StateNotice from "./StateNotice";

type NewsTableProps = {
  data: News[] | null;
  error: string | null;
  /** Необязательная колонка действий (admin CRUD). */
  actions?: (row: News) => ReactNode;
};

export default function NewsTable({ data, error, actions }: NewsTableProps) {
  const { lang, dict } = useI18n();

  if (error) {
    return <StateNotice kind="error" detail={error} />;
  }
  if (!data || data.length === 0) {
    return <StateNotice kind="empty" />;
  }

  const rows = [...data].sort(
    (a, b) => Date.parse(b.publishedAt) - Date.parse(a.publishedAt),
  );

  const columns: Column<News>[] = [
    {
      key: "publishedAt",
      header: dict.colPublishedAt,
      cell: (n) => (
        <span className="whitespace-nowrap tabular-nums">
          {formatDateTime(n.publishedAt, lang)}
        </span>
      ),
    },
    {
      key: "title",
      header: dict.colTitle,
      rowHeader: true,
      cell: (n) => (
        <span className="block max-w-[32rem]">{pickName(n.title, lang)}</span>
      ),
    },
    {
      key: "slug",
      header: dict.colCode,
      cell: (n) => <span className="font-mono text-xs">{n.slug}</span>,
    },
    {
      key: "cover",
      header: dict.colCover,
      cell: (n) =>
        n.coverMediaUrl ? (
          <a
            href={n.coverMediaUrl}
            target="_blank"
            rel="noreferrer"
            className="text-info underline underline-offset-2"
          >
            {dict.openLink}
          </a>
        ) : (
          <span className="text-text-secondary">{dict.noCover}</span>
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
    <DataTable<News>
      caption={dict.newsTitle}
      columns={columns}
      rows={rows}
      rowKey={(n) => n.slug}
      totalLabel={`${dict.total}: ${rows.length}`}
    />
  );
}
