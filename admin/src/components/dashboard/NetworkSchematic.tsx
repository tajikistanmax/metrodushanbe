"use client";

/**
 * Схема сети метро: SVG-рендер GeoJSON (линии + станции) без картографических
 * библиотек — стилистика официальной схемы (цветные линии, белые станции,
 * кольцо пересадки). Координаты EPSG:4326 проецируются в viewBox
 * эквидистантно с поправкой на широту (cos φ), чтобы пропорции города
 * не искажались.
 *
 * Компонент чисто презентационный: данные приходят готовыми props.
 */

import Link from "next/link";
import { useMemo } from "react";
import { lineBadgeLabel, pickName } from "@/lib/i18n";
import type { NetworkFeature, NetworkGeoJson } from "@/lib/types";
import { useI18n } from "../I18nProvider";

const VIEW_W = 860;
const VIEW_H = 520;
const PAD = 56;

type XY = [number, number];

type ProjectedLine = {
  code: string;
  color: string;
  name: string;
  points: XY[];
};

type ProjectedStation = {
  code: string;
  name: string;
  isTransfer: boolean;
  colors: string[];
  x: number;
  y: number;
  /** Сторона подписи: 1 — справа, -1 — слева, 0 — сверху (пересадка). */
  labelSide: 1 | -1 | 0;
};

function isLineFeature(f: NetworkFeature): boolean {
  return f.properties.feature_type === "line" && f.geometry.type === "LineString";
}

function isStationFeature(f: NetworkFeature): boolean {
  return f.properties.feature_type === "station" && f.geometry.type === "Point";
}

export default function NetworkSchematic({
  data,
}: {
  data: NetworkGeoJson;
}) {
  const { lang, dict } = useI18n();

  const { lines, stations } = useMemo(() => {
    const lineFeatures = data.features.filter(isLineFeature);
    const stationFeatures = data.features.filter(isStationFeature);

    // Общий bbox по всем координатам
    const all: XY[] = [];
    for (const f of lineFeatures) {
      if (f.geometry.type === "LineString") {
        all.push(...(f.geometry.coordinates as XY[]));
      }
    }
    for (const f of stationFeatures) {
      if (f.geometry.type === "Point") {
        all.push(f.geometry.coordinates as XY);
      }
    }
    if (all.length === 0) {
      return { lines: [] as ProjectedLine[], stations: [] as ProjectedStation[] };
    }

    const lons = all.map((p) => p[0]);
    const lats = all.map((p) => p[1]);
    const minLon = Math.min(...lons);
    const maxLon = Math.max(...lons);
    const minLat = Math.min(...lats);
    const maxLat = Math.max(...lats);

    // Поправка на широту: 1° долготы короче 1° широты в cos(φ) раз
    const midLat = (minLat + maxLat) / 2;
    const kx = Math.cos((midLat * Math.PI) / 180);

    const spanX = Math.max((maxLon - minLon) * kx, 1e-9);
    const spanY = Math.max(maxLat - minLat, 1e-9);
    const scale = Math.min(
      (VIEW_W - PAD * 2) / spanX,
      (VIEW_H - PAD * 2) / spanY,
    );
    const offsetX = (VIEW_W - spanX * scale) / 2;
    const offsetY = (VIEW_H - spanY * scale) / 2;

    const project = ([lon, lat]: XY): XY => [
      offsetX + (lon - minLon) * kx * scale,
      // Ось Y экрана направлена вниз
      VIEW_H - offsetY - (lat - minLat) * scale,
    ];

    const colorByLine = new Map<string, string>();
    const projLines: ProjectedLine[] = lineFeatures.map((f) => {
      const color = f.properties.color_hex ?? "#0e5a8a";
      colorByLine.set(f.properties.code, color);
      return {
        code: f.properties.code,
        color,
        name: pickName(f.properties.name, lang),
        points:
          f.geometry.type === "LineString"
            ? (f.geometry.coordinates as XY[]).map(project)
            : [],
      };
    });

    const projStations: ProjectedStation[] = stationFeatures.map((f) => {
      const [x, y] =
        f.geometry.type === "Point" ? project(f.geometry.coordinates as XY) : [0, 0];
      const isTransfer = Boolean(f.properties.is_transfer);
      return {
        code: f.properties.code,
        name: pickName(f.properties.name, lang),
        isTransfer,
        colors: (f.properties.lines ?? []).map(
          (lc) => colorByLine.get(lc) ?? "#0e5a8a",
        ),
        x,
        y,
        labelSide: isTransfer ? 0 : x > VIEW_W * 0.72 ? -1 : 1,
      };
    });

    return { lines: projLines, stations: projStations };
  }, [data, lang]);

  return (
    <svg
      viewBox={`0 0 ${VIEW_W} ${VIEW_H}`}
      className="h-auto w-full"
      role="img"
      aria-label={dict.dash.networkTitle}
    >
      {/* Мягкая сетка фона — намёк на карту города */}
      <g stroke="var(--card-border)" strokeWidth="1" opacity="0.5">
        {Array.from({ length: 7 }, (_, i) => (
          <line
            key={`v${i}`}
            x1={((i + 1) * VIEW_W) / 8}
            y1="0"
            x2={((i + 1) * VIEW_W) / 8}
            y2={VIEW_H}
          />
        ))}
        {Array.from({ length: 4 }, (_, i) => (
          <line
            key={`h${i}`}
            x1="0"
            y1={((i + 1) * VIEW_H) / 5}
            x2={VIEW_W}
            y2={((i + 1) * VIEW_H) / 5}
          />
        ))}
      </g>

      {/* Кассинг линий (подложка цвета карточки для «прорезания» сетки) */}
      {lines.map((l) => (
        <polyline
          key={`case-${l.code}`}
          points={l.points.map((p) => p.join(",")).join(" ")}
          fill="none"
          stroke="var(--card-bg)"
          strokeWidth="13"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      ))}

      {/* Линии */}
      {lines.map((l) => (
        <polyline
          key={l.code}
          points={l.points.map((p) => p.join(",")).join(" ")}
          fill="none"
          stroke={l.color}
          strokeWidth="7"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <title>{`${lineBadgeLabel(l.code, lang)} — ${l.name}`}</title>
        </polyline>
      ))}

      {/* Станции */}
      {stations.map((s) => (
        <Link key={s.code} href="/stations" className="group focus:outline-none">
          <g>
            <title>{s.name}</title>
            {s.isTransfer ? (
              <>
                <circle
                  cx={s.x}
                  cy={s.y}
                  r="12"
                  fill="var(--card-bg)"
                  stroke="var(--text-primary)"
                  strokeWidth="3.5"
                  className="transition-transform group-hover:scale-110 group-focus-visible:scale-110"
                  style={{ transformOrigin: `${s.x}px ${s.y}px` }}
                />
                <circle
                  cx={s.x}
                  cy={s.y}
                  r="5"
                  fill="var(--text-primary)"
                  opacity="0.85"
                />
              </>
            ) : (
              <circle
                cx={s.x}
                cy={s.y}
                r="7"
                fill="var(--card-bg)"
                stroke={s.colors[0] ?? "var(--text-primary)"}
                strokeWidth="3.5"
                className="transition-transform group-hover:scale-125 group-focus-visible:scale-125"
                style={{ transformOrigin: `${s.x}px ${s.y}px` }}
              />
            )}
            <text
              x={s.labelSide === 0 ? s.x : s.x + s.labelSide * 14}
              y={s.labelSide === 0 ? s.y - 20 : s.y + 4}
              textAnchor={
                s.labelSide === 0 ? "middle" : s.labelSide === 1 ? "start" : "end"
              }
              fill="var(--text-primary)"
              fontSize="12.5"
              fontWeight={s.isTransfer ? 700 : 600}
              paintOrder="stroke"
              stroke="var(--card-bg)"
              strokeWidth="4"
              strokeLinejoin="round"
            >
              {s.name}
            </text>
          </g>
        </Link>
      ))}
    </svg>
  );
}
