"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { pickName } from "@/lib/i18n";
import { IconArrowRight, IconSearch, IconStation } from "@/lib/icons";
import type { Line, NetworkGeoJson, Station } from "@/lib/types";
import LineBadge from "../LineBadge";
import { useI18n } from "../I18nProvider";
import NetworkSchematic from "./NetworkSchematic";
import { Badge } from "@/shared/ui";

type Props = {
  network: NetworkGeoJson;
  networkSource: "api" | "demo";
  lines: Line[];
  stations: Station[];
};

export default function NetworkMapWorkspace({
  network,
  networkSource,
  lines,
  stations,
}: Props) {
  const { lang, dict } = useI18n();
  const t = dict.dash;
  const [query, setQuery] = useState("");
  const [selectedCode, setSelectedCode] = useState<string | null>(
    stations.find((station) => station.isTransfer)?.code ?? stations[0]?.code ?? null,
  );

  const lineByCode = useMemo(
    () => new Map(lines.map((line) => [line.code, line])),
    [lines],
  );
  const filtered = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase(lang);
    if (!normalized) return stations;
    return stations.filter((station) =>
      [station.code, station.name.tg, station.name.ru, station.name.en]
        .join(" ")
        .toLocaleLowerCase(lang)
        .includes(normalized),
    );
  }, [lang, query, stations]);
  const selected =
    stations.find((station) => station.code === selectedCode) ?? filtered[0] ?? null;

  return (
    <section
      className="overflow-hidden rounded-panel border border-[var(--border-subtle)] bg-[var(--surface-raised)]"
      aria-label={t.mapPageTitle}
    >
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-[var(--border-subtle)] p-5 sm:p-6">
        <div>
          {/* font-black(900) не загружен; заголовок страницы — title-l/700 */}
          <h1 className="text-title-l font-bold">{t.mapPageTitle}</h1>
          <p className="mt-2 max-w-3xl text-body leading-relaxed text-text-secondary">{t.mapPageLead}</p>
        </div>
        <Badge tone={networkSource === "api" ? "success" : "warning"} dot className="shrink-0">
          {networkSource === "api" ? t.sourceApi : t.sourceDemo}
        </Badge>
      </div>

      <div className="grid min-h-[660px] xl:grid-cols-[minmax(0,1fr)_360px]">
        <div className="flex min-w-0 items-center bg-[var(--map-panel-bg)] p-3 sm:p-6">
          <NetworkSchematic
            data={network}
            selectedCode={selected?.code ?? null}
            onStationSelect={setSelectedCode}
          />
        </div>

        <aside className="flex min-h-0 flex-col border-t border-[var(--border-subtle)] bg-[var(--surface-raised)] xl:border-l xl:border-t-0" aria-label={t.mapStationPanel}>
          <div className="border-b border-[var(--border-subtle)] p-4">
            <label className="relative block">
              <span className="sr-only">{t.mapSearchPlaceholder}</span>
              <IconSearch className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-secondary" aria-hidden="true" />
              <input
                type="search"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder={t.mapSearchPlaceholder}
                className="w-full rounded-control border border-[var(--border-strong)] bg-[var(--surface-raised)] py-2.5 pl-9 pr-3 text-small outline-none transition-colors placeholder:text-text-secondary focus:border-info"
              />
            </label>
          </div>

          {selected && (
            <div className="border-b border-[var(--border-subtle)] p-5">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-caption font-bold uppercase tracking-[0.08em] text-text-secondary">{t.mapStationPanel}</p>
                  <h2 className="mt-1 text-title-s font-bold">{pickName(selected.name, lang)}</h2>
                  <p className="data-text mt-1 text-caption text-text-secondary">{selected.code}</p>
                </div>
                <Badge tone="info" dot className="shrink-0">
                  {dict.status[selected.status]}
                </Badge>
              </div>

              <div className="mt-4 flex flex-wrap gap-2">
                {selected.lines.map((code) => {
                  const line = lineByCode.get(code);
                  return (
                    <LineBadge key={code} code={code} colorHex={line?.colorHex ?? "var(--brand-navy)"} />
                  );
                })}
                {selected.isTransfer && (
                  <Badge>{t.legendTransfer}</Badge>
                )}
              </div>

              <dl className="mt-4 grid grid-cols-2 gap-3 rounded-control bg-[var(--surface-chip)] p-3">
                <div>
                  <dt className="text-caption font-bold uppercase text-text-secondary">{t.coordinates}</dt>
                  <dd className="data-text mt-1 text-small font-bold">{selected.coordinates[1].toFixed(5)}</dd>
                  <dd className="data-text text-small font-bold">{selected.coordinates[0].toFixed(5)}</dd>
                </div>
                <div>
                  <dt className="text-caption font-bold uppercase text-text-secondary">{dict.analytics.accessibilityTitle}</dt>
                  <dd className="mt-1 text-small font-bold">{selected.accessibility.length}</dd>
                </div>
              </dl>

              {selected.accessibility.length > 0 && (
                <ul className="mt-3 flex flex-wrap gap-1.5">
                  {selected.accessibility.map((feature) => (
                    <li key={feature} className="rounded-chip border border-[var(--border-subtle)] px-2 py-1 text-caption font-semibold text-text-secondary">
                      {dict.accessibility[feature]}
                    </li>
                  ))}
                </ul>
              )}

              <Link href="/stations" className="mt-4 flex items-center justify-between rounded-control bg-brand-navy px-4 py-3 text-small font-bold text-surface-light transition-colors hover:bg-brand-navy/90 focus-visible:outline-[var(--focus-ring-on-dark)]">
                {t.openStationAdmin}
                <IconArrowRight className="h-4 w-4" aria-hidden="true" />
              </Link>
            </div>
          )}

          <div className="min-h-0 flex-1 overflow-y-auto p-3">
            <p className="px-2 pb-2 text-caption font-bold uppercase tracking-[0.08em] text-text-secondary">
              {dict.countStations} · {filtered.length}
            </p>
            <ul className="flex flex-col gap-1">
              {filtered.map((station) => {
                const active = station.code === selected?.code;
                return (
                  <li key={station.code}>
                    {/* aria-pressed: выбор станции — состояние, а не только цвет */}
                    <button
                      type="button"
                      aria-pressed={active}
                      onClick={() => setSelectedCode(station.code)}
                      className={`flex w-full items-center gap-3 rounded-control px-3 py-2.5 text-left transition-colors ${
                        active
                          ? "bg-[var(--tint-info)] font-bold"
                          : "hover:bg-[var(--surface-hover-subtle)]"
                      }`}
                    >
                      <span aria-hidden="true" className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-control ${active ? "bg-info text-surface-light" : "bg-[var(--surface-chip)] text-text-secondary"}`}>
                        <IconStation className="h-4 w-4" />
                      </span>
                      <span className="min-w-0">
                        <span className="block truncate text-small font-bold">{pickName(station.name, lang)}</span>
                        <span className="data-text block text-caption text-text-secondary">{station.code}</span>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          </div>
        </aside>
      </div>
    </section>
  );
}
