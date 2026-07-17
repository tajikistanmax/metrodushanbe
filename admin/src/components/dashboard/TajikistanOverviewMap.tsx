"use client";

/**
 * Обзорная карта Таджикистана для презентационного дашборда.
 * Контур взят из public-domain набора Natural Earth 3.3.0 (Admin 0, 1:110m),
 * координаты WGS84. Маркер Душанбе использует фактическую геопозицию города.
 */

import { useMemo } from "react";
import { useI18n } from "../I18nProvider";

type LngLat = [number, number];

const OUTLINE: LngLat[] = [
  [71.014198, 40.244366], [70.648019, 39.935754], [69.55961, 40.103211],
  [69.464887, 39.526683], [70.549162, 39.604198], [71.784694, 39.279463],
  [73.675379, 39.431237], [73.928852, 38.505815], [74.257514, 38.606507],
  [74.864816, 38.378846], [74.829986, 37.990007], [74.980002, 37.41999],
  [73.948696, 37.421566], [73.260056, 37.495257], [72.63689, 37.047558],
  [72.193041, 36.948288], [71.844638, 36.738171], [71.448693, 37.065645],
  [71.541918, 37.905774], [71.239404, 37.953265], [71.348131, 38.258905],
  [70.806821, 38.486282], [70.376304, 38.138396], [70.270574, 37.735165],
  [70.116578, 37.588223], [69.518785, 37.608997], [69.196273, 37.151144],
  [68.859446, 37.344336], [68.135562, 37.023115], [67.83, 37.144994],
  [68.392033, 38.157025], [68.176025, 38.901553], [67.44222, 39.140144],
  [67.701429, 39.580478], [68.536416, 39.533453], [69.011633, 40.086158],
  [69.329495, 40.727824], [70.666622, 40.960213], [70.45816, 40.496495],
  [70.601407, 40.218527], [71.014198, 40.244366],
];

const DUSHANBE: LngLat = [68.787, 38.5598];
const VIEW_W = 640;
const VIEW_H = 420;
const PAD_X = 52;
const PAD_Y = 40;

export default function TajikistanOverviewMap() {
  const { lang, dict } = useI18n();
  const countryName = lang === "en" ? "TAJIKISTAN" : lang === "tg" ? "ТОҶИКИСТОН" : "ТАДЖИКИСТАН";
  const cityName = lang === "en" ? "DUSHANBE" : "ДУШАНБЕ";

  const map = useMemo(() => {
    const lons = OUTLINE.map(([lon]) => lon);
    const lats = OUTLINE.map(([, lat]) => lat);
    const minLon = Math.min(...lons);
    const maxLon = Math.max(...lons);
    const minLat = Math.min(...lats);
    const maxLat = Math.max(...lats);
    const midLat = (minLat + maxLat) / 2;
    const kx = Math.cos((midLat * Math.PI) / 180);
    const spanX = (maxLon - minLon) * kx;
    const spanY = maxLat - minLat;
    const scale = Math.min(
      (VIEW_W - PAD_X * 2) / spanX,
      (VIEW_H - PAD_Y * 2) / spanY,
    );
    const offsetX = (VIEW_W - spanX * scale) / 2;
    const offsetY = (VIEW_H - spanY * scale) / 2;

    const project = ([lon, lat]: LngLat): LngLat => [
      offsetX + (lon - minLon) * kx * scale,
      VIEW_H - offsetY - (lat - minLat) * scale,
    ];
    const path = OUTLINE.map((point, index) => {
      const [x, y] = project(point);
      return `${index === 0 ? "M" : "L"}${x.toFixed(1)} ${y.toFixed(1)}`;
    }).join(" ") + " Z";

    return { path, dushanbe: project(DUSHANBE) };
  }, []);

  const [cityX, cityY] = map.dushanbe;

  return (
    <svg
      viewBox={`0 0 ${VIEW_W} ${VIEW_H}`}
      className="h-auto w-full"
      role="img"
      aria-label={`${dict.dash.countryTitle}. ${dict.dash.capitalLabel}`}
    >
      <defs>
        <linearGradient id="tj-land" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#e8f2f7" />
          <stop offset="0.55" stopColor="#dcebdc" />
          <stop offset="1" stopColor="#bad7c2" />
        </linearGradient>
        <filter id="tj-shadow" x="-20%" y="-20%" width="140%" height="150%">
          <feDropShadow dx="0" dy="12" stdDeviation="14" floodColor="#082742" floodOpacity=".18" />
        </filter>
        <clipPath id="tj-clip">
          <path d={map.path} />
        </clipPath>
        <pattern id="tj-grid" width="34" height="34" patternUnits="userSpaceOnUse" patternTransform="rotate(18)">
          <path d="M 34 0 L 0 0 0 34" fill="none" stroke="#ffffff" strokeWidth="1" opacity=".55" />
        </pattern>
      </defs>

      <rect x="0" y="0" width={VIEW_W} height={VIEW_H} rx="22" fill="var(--map-panel-bg, #f5f8fb)" />

      <g filter="url(#tj-shadow)">
        <path d={map.path} fill="url(#tj-land)" stroke="#ffffff" strokeWidth="6" strokeLinejoin="round" />
        <path d={map.path} fill="none" stroke="#2d6f70" strokeWidth="2" strokeLinejoin="round" opacity=".75" />
      </g>

      <g clipPath="url(#tj-clip)">
        <rect width={VIEW_W} height={VIEW_H} fill="url(#tj-grid)" />
        <path d="M70 290 C175 225 245 255 330 185 S500 115 600 160" fill="none" stroke="#74a995" strokeWidth="2" opacity=".5" />
        <path d="M55 325 C170 275 250 315 355 225 S510 160 620 205" fill="none" stroke="#74a995" strokeWidth="2" opacity=".38" />
        <path d="M85 350 C205 318 290 350 390 278 S525 230 610 250" fill="none" stroke="#74a995" strokeWidth="2" opacity=".3" />
      </g>

      <text x="366" y="215" fill="#2d6f70" fontSize="24" fontWeight="800" opacity=".78">
        {countryName}
      </text>

      <g transform={`translate(${cityX} ${cityY})`}>
        <circle r="23" fill="#e21b2d" opacity=".13" className="country-map-pulse" />
        <circle r="11" fill="#ffffff" stroke="#e21b2d" strokeWidth="5" />
        <circle r="3" fill="#e21b2d" />
        <path d="M14 -2 L32 -20" stroke="#e21b2d" strokeWidth="2" />
        <g transform="translate(28 -55)">
          <rect width="174" height="48" rx="14" fill="#082742" />
          <text x="14" y="21" fill="#ffffff" fontSize="13" fontWeight="800">{cityName}</text>
          <text x="14" y="37" fill="#c8d7e4" fontSize="10" fontWeight="600">{dict.dash.capitalLabel}</text>
        </g>
      </g>

      <g transform="translate(28 26)">
        <rect width="94" height="30" rx="15" fill="#ffffff" opacity=".92" />
        <circle cx="16" cy="15" r="5" fill="#138a3d" />
        <text x="29" y="19" fill="#082742" fontSize="10" fontWeight="800">WGS 84</text>
      </g>
    </svg>
  );
}
