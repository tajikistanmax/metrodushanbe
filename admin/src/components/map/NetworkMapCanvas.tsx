"use client";

/**
 * Настоящая карта сети в операционной консоли (MapLibre GL) — та же подложка,
 * что на публичном портале (web/src/components/NetworkMap.tsx):
 * - базовый стиль из env, при отсутствии сети — локальный плоский фон;
 * - линии: белая обводка (width 9) + цветная жила (width 5);
 * - станции: кружки с обводкой, пересадка — двойное кольцо;
 * - тёмная тема: перекраска слоёв при смене data-theme.
 *
 * Компонент только отображает сеть; правка геометрии — в GeometryEditorMap.
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
import {
  MAP_ATTRIBUTION,
  MAP_CENTER,
  MAP_THEME,
  MAP_ZOOM,
  BACKGROUND_LAYER_ID,
  loadMapStyle,
} from "@/lib/map-config";
import type { NetworkGeoJson } from "@/lib/types";
import type { ResolvedTheme } from "@/shared/ThemeProvider";

const SOURCE_ID = "metro-network";
const LINES_CASING_LAYER_ID = "metro-lines-casing";
const LINES_LAYER_ID = "metro-lines";
const STATIONS_LAYER_ID = "metro-stations";
const STATIONS_TRANSFER_LAYER_ID = "metro-stations-transfer";

/** Радиус кружка: 6.5 / пересадка 10, на hover +1.5 (feature-state). */
const STATION_RADIUS: ExpressionSpecification = [
  "+",
  ["case", ["boolean", ["get", "is_transfer"], false], 10, 6.5],
  ["case", ["boolean", ["feature-state", "hover"], false], 1.5, 0],
];

type Props = {
  data: NetworkGeoJson;
  theme: ResolvedTheme;
  /** Код выбранной станции — карта центрируется на ней. */
  selectedCode: string | null;
  /** Клик по станции на карте (синхронизация с панелью). */
  onStationSelect: (code: string) => void;
  /** Доступное имя региона карты. */
  regionLabel: string;
  /** Заголовки кнопок зума (MapLibre по умолчанию англоязычен). */
  zoomInLabel: string;
  zoomOutLabel: string;
};

function prefersReducedMotion(): boolean {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

export default function NetworkMapCanvas({
  data,
  theme,
  selectedCode,
  onStationSelect,
  regionLabel,
  zoomInLabel,
  zoomOutLabel,
}: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const hoveredIdRef = useRef<string | number | null>(null);
  const remoteBasemapRef = useRef(false);
  const [mapReady, setMapReady] = useState(false);

  const onSelectRef = useRef(onStationSelect);
  useEffect(() => {
    onSelectRef.current = onStationSelect;
  }, [onStationSelect]);

  // Инициализация карты — один раз.
  useEffect(() => {
    if (!containerRef.current || mapRef.current) {
      return;
    }
    let disposed = false;
    const controller = new AbortController();
    const timeoutId = window.setTimeout(() => controller.abort(), 4_500);

    async function initialise() {
      const { style, remote } = await loadMapStyle(controller.signal);
      window.clearTimeout(timeoutId);
      if (disposed || !containerRef.current) return;
      remoteBasemapRef.current = remote;

      const map = new maplibregl.Map({
        container: containerRef.current,
        style,
        center: MAP_CENTER,
        zoom: MAP_ZOOM,
        attributionControl: remote
          ? { compact: true, customAttribution: MAP_ATTRIBUTION }
          : false,
      });
      const nav = new maplibregl.NavigationControl({ showCompass: false });
      map.addControl(nav, "top-right");
      map.on("load", () => setMapReady(true));

      map.on("click", STATIONS_LAYER_ID, (e: MapLayerMouseEvent) => {
        const code = e.features?.[0]?.properties?.code as string | undefined;
        if (code) onSelectRef.current(code);
      });

      const setHover = (id: string | number | null) => {
        if (hoveredIdRef.current === id) return;
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
    }

    void initialise();
    return () => {
      disposed = true;
      controller.abort();
      window.clearTimeout(timeoutId);
      hoveredIdRef.current = null;
      mapRef.current?.remove();
      mapRef.current = null;
      remoteBasemapRef.current = false;
      setMapReady(false);
    };
  }, []);

  // Локализация кнопок зума: MapLibre ставит английские title/aria-label.
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !mapReady) return;
    const root = map.getContainer();
    const apply = (selector: string, label: string) => {
      const btn = root.querySelector(selector);
      btn?.setAttribute("aria-label", label);
      btn?.setAttribute("title", label);
    };
    apply(".maplibregl-ctrl-zoom-in", zoomInLabel);
    apply(".maplibregl-ctrl-zoom-out", zoomOutLabel);
  }, [mapReady, zoomInLabel, zoomOutLabel]);

  // Источник и слои сети.
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !mapReady) return;

    const geojson = data as unknown as FeatureCollection;
    const existing = map.getSource(SOURCE_ID) as GeoJSONSource | undefined;
    if (existing) {
      existing.setData(geojson);
      return;
    }
    const colors = MAP_THEME[theme];
    map.addSource(SOURCE_ID, { type: "geojson", data: geojson, promoteId: "code" });

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
    map.addLayer({
      id: LINES_LAYER_ID,
      type: "line",
      source: SOURCE_ID,
      filter: ["==", ["get", "feature_type"], "line"],
      layout: { "line-cap": "round", "line-join": "round" },
      paint: { "line-color": ["get", "color_hex"], "line-width": 5 },
    });
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

  // Смена темы: перекраска фона и слоёв сети.
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !mapReady) return;
    const colors = MAP_THEME[theme];
    if (!remoteBasemapRef.current && map.getLayer(BACKGROUND_LAYER_ID)) {
      map.setPaintProperty(BACKGROUND_LAYER_ID, "background-color", colors.bg);
    }
    if (map.getLayer(LINES_CASING_LAYER_ID)) {
      map.setPaintProperty(LINES_CASING_LAYER_ID, "line-color", colors.casing);
      map.setPaintProperty(LINES_CASING_LAYER_ID, "line-opacity", colors.casingOpacity);
    }
    for (const id of [STATIONS_LAYER_ID, STATIONS_TRANSFER_LAYER_ID]) {
      if (map.getLayer(id)) {
        map.setPaintProperty(id, "circle-color", colors.stationFill);
        map.setPaintProperty(id, "circle-stroke-color", colors.stationStroke);
      }
    }
  }, [theme, mapReady]);

  // Выбор станции в панели — центрируем карту.
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !mapReady || !selectedCode) return;
    const station = data.features.find(
      (f) => f.properties.feature_type === "station" && f.properties.code === selectedCode,
    );
    if (!station || station.geometry.type !== "Point") return;
    map.flyTo({
      center: station.geometry.coordinates,
      zoom: Math.max(map.getZoom(), 13),
      animate: !prefersReducedMotion(),
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedCode, mapReady]);

  return <div ref={containerRef} role="region" aria-label={regionLabel} className="h-full w-full" />;
}
