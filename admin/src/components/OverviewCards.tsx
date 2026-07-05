"use client";

/**
 * Обзорные счётчики: линии, станции, активные уведомления, новости.
 * Каждая карточка — ссылка на соответствующий раздел. Значение null
 * означает, что запрос к API не удался (показываем «—»).
 */

import Link from "next/link";
import { useI18n } from "./I18nProvider";

type OverviewCardsProps = {
  lines: number | null;
  stations: number | null;
  alerts: number | null;
  news: number | null;
};

export default function OverviewCards({
  lines,
  stations,
  alerts,
  news,
}: OverviewCardsProps) {
  const { dict } = useI18n();

  const cards: {
    key: string;
    label: string;
    value: number | null;
    href: string;
    accent: string;
  }[] = [
    { key: "lines", label: dict.countLines, value: lines, href: "/lines", accent: "var(--brand-red)" },
    { key: "stations", label: dict.countStations, value: stations, href: "/stations", accent: "var(--brand-green)" },
    { key: "alerts", label: dict.countAlerts, value: alerts, href: "/alerts", accent: "var(--warning)" },
    { key: "news", label: dict.countNews, value: news, href: "/news", accent: "var(--info)" },
  ];

  return (
    <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {cards.map((c) => (
        <li key={c.key}>
          <Link
            href={c.href}
            className="block rounded-xl border border-[var(--card-border)] bg-[var(--card-bg)] p-5 shadow-[var(--shadow-card)] transition-colors hover:bg-[var(--table-row-hover)]"
          >
            <span
              aria-hidden="true"
              className="mb-3 block h-1.5 w-10 rounded-full"
              style={{ background: c.accent }}
            />
            <span className="block text-3xl font-extrabold tabular-nums">
              {c.value ?? "—"}
            </span>
            <span className="mt-1 block text-sm font-semibold text-text-secondary">
              {c.label}
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
