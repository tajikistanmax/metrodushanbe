"use client";

/**
 * Плавающая панель станций (list-mode, требование A11Y-06): карта никогда
 * не является единственным способом получить информацию о сети.
 * Desktop: карточка слева поверх карты; mobile (<768px): bottom-sheet 45vh.
 * Линии — вертикальные метро-диаграммы: цветная полоса, станции — кружки
 * на ней (пересадка — двойное кольцо navy), клик = flyTo + popup на карте.
 */

import { useMemo, useState } from "react";
import { lineBadgeLabel, pickName, type Lang } from "@/lib/i18n";
import {
  isLineFeature,
  isStationFeature,
  type LineFeature,
  type NetworkGeoJson,
  type StationFeature,
} from "@/lib/types";
import SearchBox from "./SearchBox";
import { useI18n } from "./I18nProvider";

type StationPanelProps = {
  data: NetworkGeoJson | null;
  onSelect: (code: string) => void;
  /** Код выбранной станции — для aria-current и подсветки ряда. */
  selectedCode: string | null;
};

type LineGroup = {
  line: LineFeature;
  stations: StationFeature[];
};

/** Квадрат расстояния между двумя точками [lng, lat] (для сортировки). */
function distanceSq(a: [number, number], b: [number, number]): number {
  const dx = a[0] - b[0];
  const dy = a[1] - b[1];
  return dx * dx + dy * dy;
}

/** Индекс ближайшей вершины линии — станции демо-данных лежат на вершинах. */
function nearestVertexIndex(
  station: StationFeature,
  line: LineFeature,
): number {
  let best = 0;
  let bestDist = Number.POSITIVE_INFINITY;
  line.geometry.coordinates.forEach((vertex, index) => {
    const d = distanceSq(station.geometry.coordinates, vertex);
    if (d < bestDist) {
      bestDist = d;
      best = index;
    }
  });
  return best;
}

/** Группировка станций по линиям с сортировкой вдоль трассы. */
function groupByLine(data: NetworkGeoJson): LineGroup[] {
  const lines = data.features
    .filter(isLineFeature)
    .sort((a, b) => a.properties.sort_order - b.properties.sort_order);
  const stations = data.features.filter(isStationFeature);

  return lines.map((line) => ({
    line,
    stations: stations
      .filter((s) => s.properties.lines.includes(line.properties.code))
      .sort((a, b) => nearestVertexIndex(a, line) - nearestVertexIndex(b, line)),
  }));
}

/** Совпадение по названию на любом из трёх языков (без учёта регистра). */
function matchesQuery(station: StationFeature, query: string): boolean {
  const name = station.properties.name;
  return [name.tg, name.ru, name.en].some((value) =>
    value?.toLowerCase().includes(query),
  );
}

/** Кружок станции на вертикальной полосе линии. */
function StationDot({
  transfer,
  lineColor,
}: {
  transfer: boolean;
  lineColor: string;
}) {
  if (transfer) {
    // Двойное кольцо navy (в тёмной теме — светлое, --diagram-ring)
    return (
      <span
        aria-hidden="true"
        className="absolute left-2.5 top-1/2 flex h-5 w-5 -translate-y-1/2 items-center justify-center rounded-full border-[2.5px] border-[var(--diagram-ring)] bg-[var(--station-fill)]"
      >
        <span className="h-2 w-2 rounded-full border-2 border-[var(--diagram-ring)]" />
      </span>
    );
  }
  return (
    <span
      aria-hidden="true"
      className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 rounded-full border-[3px] bg-[var(--station-fill)]"
      style={{ borderColor: lineColor }}
    />
  );
}

function LineDiagram({
  group,
  lang,
  selectedCode,
  transferLabel,
  onSelect,
}: {
  group: LineGroup;
  lang: Lang;
  selectedCode: string | null;
  transferLabel: string;
  onSelect: (code: string) => void;
}) {
  const { line, stations } = group;
  const color = line.properties.color_hex;

  return (
    <div className="mb-4 last:mb-1">
      <h3 className="mb-1 flex items-center gap-2 px-2 text-sm font-bold">
        <span className="line-badge" style={{ background: color }}>
          {lineBadgeLabel(line.properties.code, lang)}
        </span>
        <span className="min-w-0 truncate">
          {pickName(line.properties.name, lang)}
        </span>
      </h3>

      {/* Вертикальная метро-диаграмма: полоса линии + кружки станций */}
      <div className="relative">
        <span
          aria-hidden="true"
          className="absolute bottom-4 top-4 left-[17px] w-1.5 rounded-full"
          style={{ background: color }}
        />
        <ul>
          {stations.map((station) => {
            const code = station.properties.code;
            const selected = code === selectedCode;
            return (
              <li key={code}>
                <button
                  type="button"
                  aria-current={selected ? "true" : undefined}
                  onClick={() => onSelect(code)}
                  className={`relative flex w-full items-center gap-2 rounded-xl py-2 pl-10 pr-2 text-left text-sm font-semibold transition-colors duration-150 ease-out hover:bg-[var(--control-hover)] ${
                    selected ? "bg-[var(--control-hover)]" : ""
                  }`}
                >
                  <StationDot
                    transfer={station.properties.is_transfer}
                    lineColor={color}
                  />
                  <span className="min-w-0 flex-1 truncate">
                    {pickName(station.properties.name, lang)}
                  </span>
                  {station.properties.is_transfer && (
                    <span className="shrink-0 rounded-full border border-[var(--panel-border)] px-2 py-0.5 text-[11px] font-semibold text-text-secondary">
                      {transferLabel}
                    </span>
                  )}
                </button>
              </li>
            );
          })}
        </ul>
      </div>
    </div>
  );
}

export default function StationPanel({
  data,
  onSelect,
  selectedCode,
}: StationPanelProps) {
  const { lang, dict } = useI18n();
  const [query, setQuery] = useState("");
  const [expanded, setExpanded] = useState(true);

  const groups = useMemo(() => (data ? groupByLine(data) : []), [data]);

  const normalizedQuery = query.trim().toLowerCase();
  const visibleGroups = useMemo(() => {
    if (!normalizedQuery) {
      return groups;
    }
    return groups
      .map((group) => ({
        ...group,
        stations: group.stations.filter((s) =>
          matchesQuery(s, normalizedQuery),
        ),
      }))
      .filter((group) => group.stations.length > 0);
  }, [groups, normalizedQuery]);

  return (
    <section
      id="station-list"
      aria-label={dict.stationsHeading}
      style={{ background: "var(--panel-bg)" }}
      className={`absolute inset-x-0 bottom-0 z-10 flex flex-col overflow-hidden rounded-t-2xl border border-[var(--panel-border)] text-[var(--text-primary)] shadow-[var(--shadow-card)] backdrop-blur-md md:inset-x-auto md:bottom-auto md:left-4 md:top-4 md:w-[360px] md:rounded-2xl ${
        expanded
          ? "h-[45dvh] md:h-auto md:max-h-[calc(100dvh-160px)]"
          : "h-auto"
      }`}
    >
      {/* Свайп-ручка (визуальная) — только mobile bottom-sheet */}
      <div aria-hidden="true" className="flex justify-center pt-2 md:hidden">
        <span className="h-1 w-10 rounded-full bg-[var(--text-secondary)] opacity-40" />
      </div>

      <div className="flex shrink-0 items-center gap-2 px-4 pb-2 pt-1.5 md:pt-3">
        <h2 className="text-[15px] font-bold">{dict.stationsHeading}</h2>
        <button
          type="button"
          aria-expanded={expanded}
          aria-controls="station-panel-body"
          aria-label={expanded ? dict.panelCollapse : dict.panelExpand}
          title={expanded ? dict.panelCollapse : dict.panelExpand}
          onClick={() => setExpanded((v) => !v)}
          className="ml-auto flex h-8 w-8 items-center justify-center rounded-lg transition-colors duration-150 ease-out hover:bg-[var(--control-hover)]"
        >
          <svg
            aria-hidden="true"
            focusable="false"
            width={16}
            height={16}
            viewBox="0 0 16 16"
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            strokeLinecap="round"
            strokeLinejoin="round"
            className={`transition-transform duration-200 ease-out ${
              expanded ? "" : "rotate-180"
            }`}
          >
            {/* chevron: вниз (свернуть) / вверх (развернуть) */}
            <path d="M4 6.5 8 10.5 12 6.5" />
          </svg>
        </button>
      </div>

      {expanded && (
        <div
          id="station-panel-body"
          className="flex min-h-0 flex-1 flex-col gap-2.5 px-3 pb-3"
        >
          <SearchBox value={query} onChange={setQuery} />

          <div className="min-h-0 flex-1 overflow-y-auto pr-1">
            {groups.length === 0 ? (
              <p className="px-2 py-3 text-sm text-text-secondary">
                {dict.loading}
              </p>
            ) : visibleGroups.length === 0 ? (
              <p role="status" className="px-2 py-3 text-sm text-text-secondary">
                {dict.searchNoResults}
              </p>
            ) : (
              visibleGroups.map((group) => (
                <LineDiagram
                  key={group.line.properties.code}
                  group={group}
                  lang={lang}
                  selectedCode={selectedCode}
                  transferLabel={dict.transferBadge}
                  onSelect={onSelect}
                />
              ))
            )}
          </div>
        </div>
      )}
    </section>
  );
}
