"use client";

/**
 * Список станций (list-mode, требование A11Y-06): карта никогда не является
 * единственным способом получить информацию о сети.
 * Станции сгруппированы по линиям; клик — flyTo на карте и открытие popup.
 */

import { useMemo } from "react";
import { pickName } from "@/lib/i18n";
import {
  isLineFeature,
  isStationFeature,
  type LineFeature,
  type NetworkGeoJson,
  type StationFeature,
} from "@/lib/types";
import { useI18n } from "./I18nProvider";

type StationListProps = {
  data: NetworkGeoJson | null;
  onSelect: (code: string) => void;
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

export default function StationList({ data, onSelect }: StationListProps) {
  const { lang, dict } = useI18n();
  const groups = useMemo(() => (data ? groupByLine(data) : []), [data]);

  return (
    <section
      id="station-list"
      aria-label={dict.stationsHeading}
      className="rounded-xl border border-brand-navy/15 bg-surface-light p-4"
    >
      <h2 className="mb-3 text-base font-bold">{dict.stationsHeading}</h2>

      {groups.length === 0 ? (
        <p className="text-sm text-text-secondary">{dict.loading}</p>
      ) : (
        <div className="space-y-4">
          {groups.map(({ line, stations }) => (
            <div key={line.properties.code}>
              <h3 className="mb-1.5 flex items-center gap-2 text-sm font-semibold">
                <span
                  aria-hidden="true"
                  className="inline-block h-1.5 w-6 rounded-full"
                  style={{ backgroundColor: line.properties.color_hex }}
                />
                {pickName(line.properties.name, lang)}
                <span className="font-normal text-text-secondary">
                  ({line.properties.code})
                </span>
              </h3>
              <ul className="space-y-0.5">
                {stations.map((station) => (
                  <li key={station.properties.code}>
                    <button
                      type="button"
                      onClick={() => onSelect(station.properties.code)}
                      className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm hover:bg-surface-muted"
                    >
                      <span
                        aria-hidden="true"
                        className="inline-block shrink-0 rounded-full bg-surface-light"
                        style={{
                          width: station.properties.is_transfer ? 14 : 10,
                          height: station.properties.is_transfer ? 14 : 10,
                          border: "2.5px solid var(--brand-navy)",
                        }}
                      />
                      <span>{pickName(station.properties.name, lang)}</span>
                      {station.properties.is_transfer && (
                        <span className="ml-auto shrink-0 rounded-full bg-surface-muted px-2 py-0.5 text-xs font-semibold text-brand-navy">
                          {dict.transferBadge}
                        </span>
                      )}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
