"use client";

/**
 * Карта сети метро на MapLibre GL — полностью офлайн:
 * - стиль без внешних тайлов/глифов: один background-слой + GeoJSON-источник;
 * - линии: белая подложка (width 9) + цветная линия (width 5);
 * - станции: белые кружки с navy-обводкой; пересадка — двойное кольцо;
 * - hover: курсор pointer + радиус +1.5 (feature-state);
 * - выбранная станция: пульсирующее кольцо (не при prefers-reduced-motion);
 * - подписи станций — через Popup по клику (symbol/text-слои не используются,
 *   т.к. требуют glyphs-сервер);
 * - тёмная тема: перекраска слоёв при смене data-theme.
 */

import { useEffect, useRef, useState } from "react";
import maplibregl from "maplibre-gl";
import type {
  ExpressionSpecification,
  GeoJSONSource,
  MapLayerMouseEvent,
} from "maplibre-gl";
import type { FeatureCollection } from "geojson";
import "maplibre-gl/dist/maplibre-gl.css";
import { getDict, lineBadgeLabel, pickName, LANGS, type Lang } from "@/lib/i18n";
import {
  isLineFeature,
  isStationFeature,
  type AccessibilityFeature,
  type LineFeature,
  type NetworkGeoJson,
  type StationFeature,
} from "@/lib/types";
import type { ResolvedTheme } from "./ThemeProvider";

/** Центр Душанбе и стартовый зум (ТЗ, публичная карта). */
const MAP_CENTER: [number, number] = [68.787, 38.574];
const MAP_ZOOM = 11.5;

const SOURCE_ID = "metro-network";
const BACKGROUND_LAYER_ID = "background";
const LINES_CASING_LAYER_ID = "metro-lines-casing";
const LINES_LAYER_ID = "metro-lines";
const STATIONS_LAYER_ID = "metro-stations";
const STATIONS_TRANSFER_LAYER_ID = "metro-stations-transfer";

/** Палитра карты по теме (арт-директива §5/§7). */
const MAP_THEME: Record<
  ResolvedTheme,
  {
    bg: string;
    casing: string;
    casingOpacity: number;
    stationFill: string;
    stationStroke: string;
  }
> = {
  light: {
    bg: "#EDF1F5",
    casing: "#FFFFFF",
    casingOpacity: 1,
    stationFill: "#FFFFFF",
    stationStroke: "#082742",
  },
  dark: {
    // Белая подложка-glow сохраняется: брендовый красный на #0B1622 даёт
    // ≈3.9:1, а подложка страхует различимость линий (SC 1.4.11)
    bg: "#0B1622",
    casing: "#FFFFFF",
    casingOpacity: 0.85,
    stationFill: "#0F1D2E",
    stationStroke: "#F2F5F8",
  },
};

/** Радиус кружка: 6.5 / пересадка 10, на hover +1.5 (feature-state). */
const STATION_RADIUS: ExpressionSpecification = [
  "+",
  ["case", ["boolean", ["get", "is_transfer"], false], 10, 6.5],
  ["case", ["boolean", ["feature-state", "hover"], false], 1.5, 0],
];

export type MapSelection = {
  /** Код выбранной станции (ST-*). */
  code: string;
  /** Счётчик, чтобы повторный клик по той же станции снова открывал popup. */
  seq: number;
};

type NetworkMapProps = {
  data: NetworkGeoJson | null;
  lang: Lang;
  theme: ResolvedTheme;
  selection: MapSelection | null;
  /** Клик по станции на карте (синхронизация с панелью). */
  onSelect: (code: string) => void;
};

/** Пользователь просит уменьшить анимацию? */
function prefersReducedMotion(): boolean {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

// --- Простые 16px-иконки доступности (inline SVG, fill/stroke currentColor) ---

type IconShape =
  | { kind: "path"; d: string; stroke?: boolean; evenodd?: boolean }
  | { kind: "circle"; cx: number; cy: number; r: number };

const ACCESS_ICONS: Record<AccessibilityFeature, IconShape[]> = {
  // Лифт: кабина + стрелки вверх/вниз
  elevator: [
    {
      kind: "path",
      evenodd: true,
      d: "M3 1.5h10a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1v-11a1 1 0 0 1 1-1ZM4.5 3v10h7V3h-7ZM8 4.4 6.1 6.8h3.8L8 4.4ZM8 11.6 6.1 9.2h3.8L8 11.6Z",
    },
  ],
  // Эскалатор: диагональная лента
  escalator: [
    {
      kind: "path",
      d: "M2.5 13a1.25 1.25 0 0 1 0-2.5h1.9l5.2-6.7a1 1 0 0 1 .8-.4h3.1a1.25 1.25 0 0 1 0 2.5h-1.9l-5.2 6.7a1 1 0 0 1-.8.4H2.5Z",
    },
  ],
  // Пандус: клин-склон
  ramp: [
    { kind: "path", d: "M14 13.5H2.4a.6.6 0 0 1-.33-1.1L14 4v9.5Z" },
  ],
  // Тактильная навигация: рельеф из точек
  tactile: [
    { kind: "circle", cx: 4, cy: 5, r: 1.4 },
    { kind: "circle", cx: 8, cy: 5, r: 1.4 },
    { kind: "circle", cx: 12, cy: 5, r: 1.4 },
    { kind: "circle", cx: 4, cy: 10.5, r: 1.4 },
    { kind: "circle", cx: 8, cy: 10.5, r: 1.4 },
    { kind: "circle", cx: 12, cy: 10.5, r: 1.4 },
  ],
  // Аудиосопровождение: динамик + волна
  audio_assist: [
    { kind: "path", d: "M3 6.2v3.6h2.3L9 13V3L5.3 6.2H3Z" },
    { kind: "path", d: "M11 5.6a3.4 3.4 0 0 1 0 4.8", stroke: true },
  ],
};

const SVG_NS = "http://www.w3.org/2000/svg";

function buildAccessIcon(feature: AccessibilityFeature): SVGSVGElement {
  const svg = document.createElementNS(SVG_NS, "svg");
  svg.setAttribute("width", "16");
  svg.setAttribute("height", "16");
  svg.setAttribute("viewBox", "0 0 16 16");
  svg.setAttribute("aria-hidden", "true");
  svg.setAttribute("focusable", "false");
  for (const shape of ACCESS_ICONS[feature]) {
    if (shape.kind === "circle") {
      const circle = document.createElementNS(SVG_NS, "circle");
      circle.setAttribute("cx", String(shape.cx));
      circle.setAttribute("cy", String(shape.cy));
      circle.setAttribute("r", String(shape.r));
      circle.setAttribute("fill", "currentColor");
      svg.appendChild(circle);
    } else {
      const path = document.createElementNS(SVG_NS, "path");
      path.setAttribute("d", shape.d);
      if (shape.stroke) {
        path.setAttribute("fill", "none");
        path.setAttribute("stroke", "currentColor");
        path.setAttribute("stroke-width", "1.6");
        path.setAttribute("stroke-linecap", "round");
      } else {
        path.setAttribute("fill", "currentColor");
        if (shape.evenodd) {
          path.setAttribute("fill-rule", "evenodd");
        }
      }
      svg.appendChild(path);
    }
  }
  return svg;
}

/** Карточка popup станции строится через DOM API (без innerHTML). */
function buildPopupContent(
  station: StationFeature,
  linesByCode: Map<string, LineFeature>,
  lang: Lang,
): HTMLElement {
  const dict = getDict(lang);
  const props = station.properties;

  const root = document.createElement("div");
  root.style.minWidth = "200px";

  // Название крупно — на текущем языке
  const title = document.createElement("div");
  title.textContent = pickName(props.name, lang);
  title.lang = lang;
  title.style.fontSize = "16px";
  title.style.fontWeight = "700";
  title.style.lineHeight = "1.25";
  title.style.paddingRight = "18px";
  root.appendChild(title);

  // Два остальных языка — мелко, вторичным цветом
  const otherLangs = LANGS.filter((code) => code !== lang);
  const subtitle = document.createElement("div");
  subtitle.style.fontSize = "12px";
  subtitle.style.color = "var(--text-secondary)";
  subtitle.style.margin = "2px 0 8px";
  otherLangs.forEach((code, index) => {
    if (index > 0) {
      subtitle.appendChild(document.createTextNode(" · "));
    }
    const span = document.createElement("span");
    span.lang = code;
    span.textContent = pickName(props.name, code);
    subtitle.appendChild(span);
  });
  root.appendChild(subtitle);

  // Бейджи линий + метка пересадки
  const badges = document.createElement("div");
  badges.style.display = "flex";
  badges.style.flexWrap = "wrap";
  badges.style.alignItems = "center";
  badges.style.gap = "6px";
  for (const lineCode of props.lines) {
    const line = linesByCode.get(lineCode);
    const badge = document.createElement("span");
    badge.className = "line-badge";
    badge.style.background = line?.properties.color_hex ?? "var(--brand-navy)";
    badge.textContent = lineBadgeLabel(lineCode, lang);
    if (line) {
      badge.title = pickName(line.properties.name, lang);
    }
    badges.appendChild(badge);
  }
  if (props.is_transfer) {
    const transfer = document.createElement("span");
    transfer.textContent = dict.transferBadge;
    transfer.style.fontSize = "11px";
    transfer.style.fontWeight = "600";
    transfer.style.padding = "1px 8px";
    transfer.style.borderRadius = "999px";
    transfer.style.border = "1px solid var(--panel-border)";
    transfer.style.color = "var(--text-secondary)";
    badges.appendChild(transfer);
  }
  root.appendChild(badges);

  // Ряд доступности: иконки 16px c aria-label из словаря
  if (props.accessibility.length > 0) {
    const row = document.createElement("div");
    row.style.display = "flex";
    row.style.gap = "6px";
    row.style.marginTop = "9px";
    row.setAttribute("aria-label", dict.popupAccessibility);
    for (const feature of props.accessibility) {
      const label = dict.accessibility[feature];
      const chip = document.createElement("span");
      chip.setAttribute("role", "img");
      chip.setAttribute("aria-label", label);
      chip.title = label;
      chip.style.display = "inline-flex";
      chip.style.alignItems = "center";
      chip.style.justifyContent = "center";
      chip.style.width = "26px";
      chip.style.height = "26px";
      chip.style.borderRadius = "8px";
      chip.style.background = "var(--control-hover)";
      chip.style.color = "var(--text-primary)";
      chip.appendChild(buildAccessIcon(feature));
      row.appendChild(chip);
    }
    root.appendChild(row);
  }

  return root;
}

export default function NetworkMap({
  data,
  lang,
  theme,
  selection,
  onSelect,
}: NetworkMapProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const popupRef = useRef<maplibregl.Popup | null>(null);
  const pulseMarkerRef = useRef<maplibregl.Marker | null>(null);
  const hoveredIdRef = useRef<string | number | null>(null);
  const [mapReady, setMapReady] = useState(false);

  // Актуальные значения для обработчиков, привязанных один раз
  const dataRef = useRef<NetworkGeoJson | null>(data);
  const langRef = useRef<Lang>(lang);
  const onSelectRef = useRef(onSelect);
  useEffect(() => {
    dataRef.current = data;
  }, [data]);
  useEffect(() => {
    langRef.current = lang;
  }, [lang]);
  useEffect(() => {
    onSelectRef.current = onSelect;
  }, [onSelect]);

  const { mapRegionLabel } = getDict(lang);

  /** Убирает пульсирующее кольцо выбранной станции. */
  const clearPulse = () => {
    pulseMarkerRef.current?.remove();
    pulseMarkerRef.current = null;
  };

  /** Ставит пульсирующее кольцо (только без prefers-reduced-motion). */
  const showPulse = (map: maplibregl.Map, station: StationFeature) => {
    clearPulse();
    if (prefersReducedMotion()) {
      return;
    }
    const el = document.createElement("div");
    el.className = "station-pulse";
    el.setAttribute("aria-hidden", "true");
    pulseMarkerRef.current = new maplibregl.Marker({ element: el })
      .setLngLat(station.geometry.coordinates)
      .addTo(map);
  };

  /** Открывает popup для станции, закрыв предыдущий. */
  const openStationPopup = (map: maplibregl.Map, station: StationFeature) => {
    const currentLang = langRef.current;
    const dict = getDict(currentLang);
    const lines = new Map<string, LineFeature>(
      (dataRef.current?.features ?? [])
        .filter(isLineFeature)
        .map((line) => [line.properties.code, line]),
    );

    popupRef.current?.remove();
    const popup = new maplibregl.Popup({
      offset: station.properties.is_transfer ? 18 : 14,
      closeButton: true,
    })
      .setLngLat(station.geometry.coordinates)
      .setDOMContent(buildPopupContent(station, lines, currentLang))
      .addTo(map);

    // aria-label крестика — из словаря (у MapLibre он англоязычный)
    const closeBtn = popup
      .getElement()
      .querySelector(".maplibregl-popup-close-button");
    closeBtn?.setAttribute("aria-label", dict.popupClose);
    closeBtn?.setAttribute("title", dict.popupClose);

    // Закрытие popup гасит и пульсирующее кольцо
    popup.on("close", () => clearPulse());
    popupRef.current = popup;
  };

  // Инициализация карты — один раз
  useEffect(() => {
    if (!containerRef.current || mapRef.current) {
      return;
    }

    const map = new maplibregl.Map({
      container: containerRef.current,
      // Офлайн-стиль: только фон, без внешних tiles/glyphs/sprites
      style: {
        version: 8,
        sources: {},
        layers: [
          {
            id: BACKGROUND_LAYER_ID,
            type: "background",
            paint: { "background-color": MAP_THEME.light.bg },
          },
        ],
      },
      center: MAP_CENTER,
      zoom: MAP_ZOOM,
      attributionControl: false,
    });
    map.addControl(
      new maplibregl.NavigationControl({ showCompass: false }),
      "top-right",
    );

    map.on("load", () => setMapReady(true));

    // Клик по станции: единый путь через onSelect (панель + карта в синхроне)
    map.on("click", STATIONS_LAYER_ID, (e: MapLayerMouseEvent) => {
      const code = e.features?.[0]?.properties?.code as string | undefined;
      if (code) {
        onSelectRef.current(code);
      }
    });

    // Hover: курсор pointer + увеличение радиуса через feature-state
    const setHover = (id: string | number | null) => {
      if (hoveredIdRef.current === id) {
        return;
      }
      if (hoveredIdRef.current !== null) {
        map.setFeatureState(
          { source: SOURCE_ID, id: hoveredIdRef.current },
          { hover: false },
        );
      }
      hoveredIdRef.current = id;
      if (id !== null) {
        map.setFeatureState({ source: SOURCE_ID, id }, { hover: true });
      }
    };

    map.on("mousemove", STATIONS_LAYER_ID, (e: MapLayerMouseEvent) => {
      map.getCanvas().style.cursor = "pointer";
      setHover(e.features?.[0]?.id ?? null);
    });
    map.on("mouseleave", STATIONS_LAYER_ID, () => {
      map.getCanvas().style.cursor = "";
      setHover(null);
    });

    mapRef.current = map;

    return () => {
      popupRef.current?.remove();
      popupRef.current = null;
      pulseMarkerRef.current?.remove();
      pulseMarkerRef.current = null;
      hoveredIdRef.current = null;
      map.remove();
      mapRef.current = null;
      setMapReady(false);
    };
  }, []);

  // Добавление/обновление GeoJSON-источника и слоёв
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !mapReady || !data) {
      return;
    }

    const geojson = data as unknown as FeatureCollection;
    const existing = map.getSource(SOURCE_ID) as GeoJSONSource | undefined;
    if (existing) {
      existing.setData(geojson);
      return;
    }

    const colors = MAP_THEME[theme];

    // promoteId: стабильный code фичи — id для feature-state (hover)
    map.addSource(SOURCE_ID, {
      type: "geojson",
      data: geojson,
      promoteId: "code",
    });

    // Белая подложка линий (классическая метро-схема; glow в тёмной теме)
    map.addLayer({
      id: LINES_CASING_LAYER_ID,
      type: "line",
      source: SOURCE_ID,
      filter: ["==", ["get", "feature_type"], "line"],
      layout: { "line-cap": "round", "line-join": "round" },
      paint: {
        "line-color": colors.casing,
        "line-opacity": colors.casingOpacity,
        "line-width": 9,
      },
    });

    // Линии: цвет из свойства color_hex фичи
    map.addLayer({
      id: LINES_LAYER_ID,
      type: "line",
      source: SOURCE_ID,
      filter: ["==", ["get", "feature_type"], "line"],
      layout: { "line-cap": "round", "line-join": "round" },
      paint: {
        "line-color": ["get", "color_hex"],
        "line-width": 5,
      },
    });

    // Станции: кружок с обводкой; пересадочные крупнее; hover +1.5
    map.addLayer({
      id: STATIONS_LAYER_ID,
      type: "circle",
      source: SOURCE_ID,
      filter: ["==", ["get", "feature_type"], "station"],
      paint: {
        "circle-radius": STATION_RADIUS,
        "circle-color": colors.stationFill,
        "circle-stroke-width": 2.5,
        "circle-stroke-color": colors.stationStroke,
      },
    });

    // Пересадка: внутреннее кольцо (второй circle-слой)
    map.addLayer({
      id: STATIONS_TRANSFER_LAYER_ID,
      type: "circle",
      source: SOURCE_ID,
      filter: [
        "all",
        ["==", ["get", "feature_type"], "station"],
        ["==", ["get", "is_transfer"], true],
      ],
      paint: {
        "circle-radius": 4,
        "circle-color": colors.stationFill,
        "circle-stroke-width": 2,
        "circle-stroke-color": colors.stationStroke,
      },
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data, mapReady]);

  // Смена темы: перекраска фонового слоя и слоёв сети
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !mapReady) {
      return;
    }
    const colors = MAP_THEME[theme];
    map.setPaintProperty(BACKGROUND_LAYER_ID, "background-color", colors.bg);
    if (map.getLayer(LINES_CASING_LAYER_ID)) {
      map.setPaintProperty(LINES_CASING_LAYER_ID, "line-color", colors.casing);
      map.setPaintProperty(
        LINES_CASING_LAYER_ID,
        "line-opacity",
        colors.casingOpacity,
      );
    }
    if (map.getLayer(STATIONS_LAYER_ID)) {
      map.setPaintProperty(
        STATIONS_LAYER_ID,
        "circle-color",
        colors.stationFill,
      );
      map.setPaintProperty(
        STATIONS_LAYER_ID,
        "circle-stroke-color",
        colors.stationStroke,
      );
    }
    if (map.getLayer(STATIONS_TRANSFER_LAYER_ID)) {
      map.setPaintProperty(
        STATIONS_TRANSFER_LAYER_ID,
        "circle-color",
        colors.stationFill,
      );
      map.setPaintProperty(
        STATIONS_TRANSFER_LAYER_ID,
        "circle-stroke-color",
        colors.stationStroke,
      );
    }
  }, [theme, mapReady]);

  // Выбор станции (панель или клик по карте): flyTo + popup + пульс
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !mapReady || !data || !selection) {
      return;
    }
    const station = data.features
      .filter(isStationFeature)
      .find((f) => f.properties.code === selection.code);
    if (!station) {
      return;
    }

    map.flyTo({
      center: station.geometry.coordinates,
      zoom: Math.max(map.getZoom(), 13),
      // A11Y: при prefers-reduced-motion — мгновенный переход без анимации
      animate: !prefersReducedMotion(),
    });
    openStationPopup(map, station);
    showPulse(map, station);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selection, mapReady]);

  // Смена языка: закрываем popup, чтобы не показывать устаревший язык
  // (его закрытие гасит и пульс — см. обработчик close).
  useEffect(() => {
    popupRef.current?.remove();
    popupRef.current = null;
  }, [lang]);

  return (
    <div
      ref={containerRef}
      role="region"
      aria-label={mapRegionLabel}
      // Не `absolute inset-0`: MapLibre вешает на контейнер .maplibregl-map
      // (position: relative), который может перебить Tailwind-утилиту .absolute
      // в зависимости от порядка CSS в бандле — контейнер схлопывается в h=0.
      className="h-full w-full"
    />
  );
}
