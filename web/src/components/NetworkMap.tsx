"use client";

/**
 * Карта сети метро на MapLibre GL — полностью офлайн:
 * - стиль без внешних тайлов/глифов: один background-слой + GeoJSON-источник;
 * - подписи станций — через Popup по клику (symbol/text-слои не используются,
 *   т.к. требуют glyphs-сервер);
 * - при prefers-reduced-motion анимация flyTo отключается.
 */

import { useEffect, useRef, useState } from "react";
import maplibregl from "maplibre-gl";
import type { GeoJSONSource, MapLayerMouseEvent } from "maplibre-gl";
import type { FeatureCollection } from "geojson";
import "maplibre-gl/dist/maplibre-gl.css";
import { getDict, pickName, type Lang } from "@/lib/i18n";
import {
  isStationFeature,
  type NetworkGeoJson,
  type StationFeature,
} from "@/lib/types";

/** Центр Душанбе и стартовый зум (ТЗ, публичная карта). */
const MAP_CENTER: [number, number] = [68.787, 38.574];
const MAP_ZOOM = 11.5;

const SOURCE_ID = "metro-network";
const LINES_LAYER_ID = "metro-lines";
const STATIONS_LAYER_ID = "metro-stations";

export type MapSelection = {
  /** Код выбранной станции (ST-*). */
  code: string;
  /** Счётчик, чтобы повторный клик по той же станции снова открывал popup. */
  seq: number;
};

type NetworkMapProps = {
  data: NetworkGeoJson | null;
  lang: Lang;
  selection: MapSelection | null;
};

/** Пользователь просит уменьшить анимацию? */
function prefersReducedMotion(): boolean {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

/** Содержимое popup станции строится через DOM API (без innerHTML). */
function buildPopupContent(station: StationFeature, lang: Lang): HTMLElement {
  const dict = getDict(lang);
  const props = station.properties;

  const root = document.createElement("div");
  root.style.minWidth = "180px";

  const title = document.createElement("strong");
  title.textContent = pickName(props.name, lang);
  title.style.display = "block";
  title.style.fontSize = "14px";
  title.style.marginBottom = "4px";
  root.appendChild(title);

  if (props.is_transfer) {
    const badge = document.createElement("span");
    badge.textContent = dict.transferBadge;
    badge.style.display = "inline-block";
    badge.style.fontSize = "11px";
    badge.style.fontWeight = "600";
    badge.style.padding = "1px 6px";
    badge.style.marginBottom = "6px";
    badge.style.borderRadius = "999px";
    badge.style.background = "var(--surface-muted)";
    badge.style.color = "var(--brand-navy)";
    root.appendChild(badge);
  }

  const addRow = (label: string, value: string) => {
    const row = document.createElement("div");
    row.style.fontSize = "12px";
    row.style.color = "var(--text-secondary)";
    row.style.marginTop = "2px";
    const term = document.createElement("span");
    term.textContent = `${label}: `;
    term.style.fontWeight = "600";
    row.appendChild(term);
    row.appendChild(document.createTextNode(value));
    root.appendChild(row);
  };

  addRow(dict.popupLines, props.lines.join(", "));
  addRow(dict.popupStatus, dict.status[props.status]);
  if (props.accessibility.length > 0) {
    addRow(
      dict.popupAccessibility,
      props.accessibility.map((f) => dict.accessibility[f]).join(", "),
    );
  }

  return root;
}

export default function NetworkMap({ data, lang, selection }: NetworkMapProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const popupRef = useRef<maplibregl.Popup | null>(null);
  const [mapReady, setMapReady] = useState(false);

  // Актуальные значения для обработчиков, привязанных один раз
  const dataRef = useRef<NetworkGeoJson | null>(data);
  const langRef = useRef<Lang>(lang);
  useEffect(() => {
    dataRef.current = data;
  }, [data]);
  useEffect(() => {
    langRef.current = lang;
  }, [lang]);

  const { mapRegionLabel } = getDict(lang);

  /** Открывает popup для станции, закрыв предыдущий. */
  const openStationPopup = (map: maplibregl.Map, station: StationFeature) => {
    popupRef.current?.remove();
    popupRef.current = new maplibregl.Popup({ offset: 14, closeButton: true })
      .setLngLat(station.geometry.coordinates)
      .setDOMContent(buildPopupContent(station, langRef.current))
      .addTo(map);
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
            id: "background",
            type: "background",
            paint: { "background-color": "#F2F5F8" },
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

    // Клик по станции — popup с названием на текущем языке
    map.on("click", STATIONS_LAYER_ID, (e: MapLayerMouseEvent) => {
      const code = e.features?.[0]?.properties?.code as string | undefined;
      const currentData = dataRef.current;
      if (!code || !currentData) {
        return;
      }
      // Полные типизированные свойства берём из исходных данных по коду:
      // MapLibre сериализует вложенные свойства фич в строки.
      const station = currentData.features
        .filter(isStationFeature)
        .find((f) => f.properties.code === code);
      if (station) {
        openStationPopup(map, station);
      }
    });

    map.on("mouseenter", STATIONS_LAYER_ID, () => {
      map.getCanvas().style.cursor = "pointer";
    });
    map.on("mouseleave", STATIONS_LAYER_ID, () => {
      map.getCanvas().style.cursor = "";
    });

    mapRef.current = map;

    return () => {
      popupRef.current?.remove();
      popupRef.current = null;
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

    map.addSource(SOURCE_ID, { type: "geojson", data: geojson });

    // Линии: цвет из свойства color_hex фичи
    map.addLayer({
      id: LINES_LAYER_ID,
      type: "line",
      source: SOURCE_ID,
      filter: ["==", ["get", "feature_type"], "line"],
      layout: { "line-cap": "round", "line-join": "round" },
      paint: {
        "line-color": ["get", "color_hex"],
        "line-width": 4,
      },
    });

    // Станции: белый круг с обводкой navy; пересадочные — крупнее
    map.addLayer({
      id: STATIONS_LAYER_ID,
      type: "circle",
      source: SOURCE_ID,
      filter: ["==", ["get", "feature_type"], "station"],
      paint: {
        "circle-radius": [
          "case",
          ["boolean", ["get", "is_transfer"], false],
          9,
          6,
        ],
        "circle-color": "#FFFFFF",
        "circle-stroke-width": 2.5,
        "circle-stroke-color": "#082742",
      },
    });
  }, [data, mapReady]);

  // Выбор станции из списка: flyTo + popup
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selection, mapReady]);

  // Смена языка: открытый popup перестраиваем на новом языке не требуется —
  // popup создаётся в момент клика; закрываем его, чтобы не показывать
  // устаревший язык.
  useEffect(() => {
    popupRef.current?.remove();
    popupRef.current = null;
  }, [lang]);

  return (
    <div
      ref={containerRef}
      role="region"
      aria-label={mapRegionLabel}
      className="h-[420px] w-full rounded-xl border border-brand-navy/15 sm:h-[520px]"
    />
  );
}
