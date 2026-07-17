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
  selectedCode = null,
  onStationSelect,
}: {
  data: NetworkGeoJson;
  selectedCode?: string | null;
  onStationSelect?: (code: string) => void;
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
      <defs>
        <linearGradient id="city-map-bg" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="var(--map-city-start)" />
          <stop offset="1" stopColor="var(--map-city-end)" />
        </linearGradient>
        <filter id="line-glow" x="-20%" y="-20%" width="140%" height="140%">
          <feDropShadow dx="0" dy="3" stdDeviation="3" floodColor="#082742" floodOpacity=".18" />
        </filter>
      </defs>

      <rect width={VIEW_W} height={VIEW_H} rx="18" fill="url(#city-map-bg)" />

      {/* Городская подложка: квартальная сетка, магистрали, река и зелёные зоны. */}
      <g stroke="var(--map-road-minor)" strokeWidth="1" opacity="0.72">
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

      <g fill="var(--map-park)" opacity=".72">
        <path d="M92 64h112l24 62-63 45-97-32Z" />
        <path d="M666 312h126l22 112-75 44-104-51Z" />
        <path d="M490 62h102l28 62-43 42-111-31Z" />
      </g>

      <g fill="none" strokeLinecap="round">
        <path d="M312 -10C292 78 352 132 334 216S275 352 306 530" stroke="var(--map-water)" strokeWidth="20" opacity=".58" />
        <path d="M312 -10C292 78 352 132 334 216S275 352 306 530" stroke="var(--map-water-core)" strokeWidth="3" opacity=".75" />
        <path d="M28 398C168 332 264 355 398 300S646 202 842 244" stroke="var(--map-road-major)" strokeWidth="5" opacity=".65" />
        <path d="M18 160C168 210 286 184 426 140S671 94 844 142" stroke="var(--map-road-major)" strokeWidth="4" opacity=".48" />
        <path d="M118 16C176 118 187 208 160 318S152 448 210 508" stroke="var(--map-road-major)" strokeWidth="4" opacity=".42" />
        <path d="M692 8C638 98 646 182 684 264S739 421 706 512" stroke="var(--map-road-major)" strokeWidth="4" opacity=".42" />
      </g>

      <text x="704" y="48" fill="var(--map-label)" fontSize="27" fontWeight="900" opacity=".19">
        {lang === "en" ? "DUSHANBE" : "ДУШАНБЕ"}
      </text>
      <g transform="translate(814 476)" fill="var(--map-label)" opacity=".65">
        <path d="M0 22 10 0l10 22-10-5Z" />
        <text x="10" y="38" textAnchor="middle" fontSize="10" fontWeight="800">N</text>
      </g>

      {/* Кассинг линий (подложка цвета карточки для «прорезания» сетки) */}
      <g filter="url(#line-glow)">
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
      </g>

      {/* Станции */}
      {stations.map((s) => {
        const stationGraphic = (
          <g className="group">
            <title>{s.name}</title>
            {selectedCode === s.code && (
              <circle
                cx={s.x}
                cy={s.y}
                r="18"
                fill="none"
                stroke="#e21b2d"
                strokeWidth="3"
                opacity=".32"
                className="network-selection-ring"
              />
            )}
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
        );

        return onStationSelect ? (
          <g
            key={s.code}
            role="button"
            tabIndex={0}
            aria-label={s.name}
            className="cursor-pointer focus:outline-none"
            onClick={() => onStationSelect(s.code)}
            onKeyDown={(event) => {
              if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                onStationSelect(s.code);
              }
            }}
          >
            {stationGraphic}
          </g>
        ) : (
          <Link key={s.code} href="/map" className="group focus:outline-none">
            {stationGraphic}
          </Link>
        );
      })}
    </svg>
  );
}
