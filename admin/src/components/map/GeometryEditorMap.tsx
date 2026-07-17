"use client";

/**
 * Карта-редактор геометрии для форм линии и станции (MapLibre GL).
 *
 * Режимы:
 * - "path"  — трасса линии: клик добавляет точку, точки соединяются на глазах
 *             оператора; вершину можно перетащить, выбрать и удалить, а в
 *             середину сегмента — вставить новую (полупрозрачные «мидпоинты»);
 * - "point" — координата станции: клик ставит точку, её можно перетащить.
 *
 * Подложка и палитра — общие с картой сети (lib/map-config): при отсутствии
 * сети карта деградирует к локальному фону, тёмная тема перекрашивает слои.
 *
 * Рисование мышью недоступно с клавиатуры — клавиатурный путь обеспечивают
 * числовые поля формы, поэтому карта здесь дополняет ввод, а не заменяет его
 * (docs/dev-conventions.md §5: у карты всегда есть list-mode).
 */

import { useCallback, useEffect, useRef, useState } from "react";
import maplibregl from "maplibre-gl";
import type { GeoJSONSource, MapLayerMouseEvent, MapMouseEvent } from "maplibre-gl";
import type { Feature, FeatureCollection } from "geojson";
import "maplibre-gl/dist/maplibre-gl.css";
import {
  MAP_ATTRIBUTION,
  MAP_CENTER,
  MAP_THEME,
  MAP_ZOOM,
  BACKGROUND_LAYER_ID,
  loadMapStyle,
} from "@/lib/map-config";
import type { LngLat, NetworkGeoJson } from "@/lib/types";
import type { ResolvedTheme } from "@/shared/ThemeProvider";

const CONTEXT_SOURCE_ID = "network-context";
const CONTEXT_LINES_LAYER_ID = "context-lines";
const CONTEXT_STATIONS_LAYER_ID = "context-stations";

const DRAFT_SOURCE_ID = "draft";
const DRAFT_LINE_LAYER_ID = "draft-line";
const DRAFT_MIDPOINTS_LAYER_ID = "draft-midpoints";
const DRAFT_VERTICES_LAYER_ID = "draft-vertices";

export type EditorMode = "path" | "point";

type Props = {
  mode: EditorMode;
  /** Черновая геометрия: список точек (path) либо одна точка (point). */
  value: LngLat[];
  onChange: (next: LngLat[]) => void;
  /** Выбранная вершина (для удаления кнопкой) — только режим "path". */
  selectedIndex: number | null;
  onSelectIndex: (index: number | null) => void;
  /**
   * Ветвистая трасса только для показа (режим чтения): сегменты рисуются как
   * MultiLineString. Склеивать их в один список нельзя — между концом одного
   * сегмента и началом другого появилась бы несуществующая перемычка.
   */
  readOnlySegments?: LngLat[][];
  /** Сеть для контекста (остальные линии/станции — приглушённо). */
  context: NetworkGeoJson | null;
  /** Цвет черновой линии (цвет редактируемой линии). */
  color?: string;
  theme: ResolvedTheme;
  /** Только чтение: рисование запрещено (многосегментная трасса). */
  readOnly?: boolean;
  regionLabel: string;
  zoomInLabel: string;
  zoomOutLabel: string;
};

function prefersReducedMotion(): boolean {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

/** Черновая геометрия → FeatureCollection: линия, вершины и мидпоинты. */
function draftCollection(
  mode: EditorMode,
  points: LngLat[],
  selectedIndex: number | null,
  readOnlySegments?: LngLat[][],
): FeatureCollection {
  const features: Feature[] = [];

  // Режим чтения ветвистой трассы: сегменты как есть, без мидпоинтов —
  // вставлять точки в геометрию, которую нельзя сохранить, бессмысленно.
  if (readOnlySegments && readOnlySegments.length > 1) {
    features.push({
      type: "Feature",
      properties: { kind: "line" },
      geometry: { type: "MultiLineString", coordinates: readOnlySegments },
    });
    for (const segment of readOnlySegments) {
      for (const p of segment) {
        features.push({
          type: "Feature",
          properties: { kind: "vertex", index: -1, selected: false },
          geometry: { type: "Point", coordinates: p },
        });
      }
    }
    return { type: "FeatureCollection", features };
  }

  if (mode === "path" && points.length >= 2) {
    features.push({
      type: "Feature",
      properties: { kind: "line" },
      geometry: { type: "LineString", coordinates: points },
    });
    // Мидпоинты: клик по ним вставляет вершину в середину сегмента.
    for (let i = 0; i < points.length - 1; i++) {
      const a = points[i];
      const b = points[i + 1];
      features.push({
        type: "Feature",
        // insertAt: индекс, по которому встанет новая вершина
        properties: { kind: "midpoint", insertAt: i + 1 },
        geometry: {
          type: "Point",
          coordinates: [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2],
        },
      });
    }
  }

  points.forEach((p, index) => {
    features.push({
      type: "Feature",
      properties: {
        kind: "vertex",
        index,
        selected: index === selectedIndex,
        label: String(index + 1),
      },
      geometry: { type: "Point", coordinates: p },
    });
  });

  return { type: "FeatureCollection", features };
}

/** Контекст: сеть без редактируемой геометрии — приглушённая подложка. */
function contextCollection(network: NetworkGeoJson | null): FeatureCollection {
  return {
    type: "FeatureCollection",
    features: (network?.features ?? []) as unknown as Feature[],
  };
}

export default function GeometryEditorMap({
  mode,
  value,
  onChange,
  selectedIndex,
  onSelectIndex,
  readOnlySegments,
  context,
  color,
  theme,
  readOnly = false,
  regionLabel,
  zoomInLabel,
  zoomOutLabel,
}: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const remoteBasemapRef = useRef(false);
  const [mapReady, setMapReady] = useState(false);
  const fittedRef = useRef(false);

  // Актуальные значения для обработчиков, привязанных один раз.
  const valueRef = useRef(value);
  const modeRef = useRef(mode);
  const readOnlyRef = useRef(readOnly);
  const onChangeRef = useRef(onChange);
  const onSelectRef = useRef(onSelectIndex);
  const segmentsRef = useRef(readOnlySegments);
  const draggingRef = useRef<number | null>(null);
  useEffect(() => {
    segmentsRef.current = readOnlySegments;
  }, [readOnlySegments]);
  useEffect(() => {
    valueRef.current = value;
  }, [value]);
  useEffect(() => {
    modeRef.current = mode;
  }, [mode]);
  useEffect(() => {
    readOnlyRef.current = readOnly;
  }, [readOnly]);
  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);
  useEffect(() => {
    onSelectRef.current = onSelectIndex;
  }, [onSelectIndex]);

  const syncDraft = useCallback(
    (points: LngLat[], selected: number | null) => {
      const map = mapRef.current;
      const source = map?.getSource(DRAFT_SOURCE_ID) as GeoJSONSource | undefined;
      source?.setData(
        draftCollection(modeRef.current, points, selected, segmentsRef.current),
      );
    },
    [],
  );

  // Инициализация карты — один раз.
  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;
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
      map.addControl(new maplibregl.NavigationControl({ showCompass: false }), "top-right");
      map.on("load", () => setMapReady(true));

      // --- Клик по карте: добавить точку / поставить точку ---
      map.on("click", (e: MapMouseEvent) => {
        if (readOnlyRef.current) return;
        // Клик по вершине/мидпоинту обрабатывают их собственные слои.
        const hits = map.queryRenderedFeatures(e.point, {
          layers: [DRAFT_VERTICES_LAYER_ID, DRAFT_MIDPOINTS_LAYER_ID].filter((id) =>
            map.getLayer(id),
          ),
        });
        if (hits.length > 0) return;

        const next: LngLat = [e.lngLat.lng, e.lngLat.lat];
        if (modeRef.current === "point") {
          onChangeRef.current([next]);
          onSelectRef.current(0);
        } else {
          onChangeRef.current([...valueRef.current, next]);
          onSelectRef.current(valueRef.current.length);
        }
      });

      // --- Клик по вершине: выбрать (для удаления кнопкой) ---
      map.on("click", DRAFT_VERTICES_LAYER_ID, (e: MapLayerMouseEvent) => {
        e.preventDefault();
        const index = e.features?.[0]?.properties?.index;
        if (typeof index === "number") onSelectRef.current(index);
      });

      // --- Клик по мидпоинту: вставить вершину в середину сегмента ---
      map.on("click", DRAFT_MIDPOINTS_LAYER_ID, (e: MapLayerMouseEvent) => {
        if (readOnlyRef.current) return;
        e.preventDefault();
        const insertAt = e.features?.[0]?.properties?.insertAt;
        if (typeof insertAt !== "number") return;
        const next = [...valueRef.current];
        next.splice(insertAt, 0, [e.lngLat.lng, e.lngLat.lat]);
        onChangeRef.current(next);
        onSelectRef.current(insertAt);
      });

      // --- Перетаскивание вершины ---
      const onMove = (e: MapMouseEvent) => {
        const index = draggingRef.current;
        if (index === null) return;
        const next = [...valueRef.current];
        next[index] = [e.lngLat.lng, e.lngLat.lat];
        // Во время перетаскивания обновляем только источник карты: пере-рендер
        // React на каждый mousemove сделал бы перетаскивание рваным.
        valueRef.current = next;
        syncDraft(next, index);
      };
      const endDrag = () => {
        if (draggingRef.current === null) return;
        draggingRef.current = null;
        map.getCanvas().style.cursor = "";
        map.dragPan.enable();
        map.off("mousemove", onMove);
        // Коммит в React — один раз, по отпусканию.
        onChangeRef.current(valueRef.current);
      };

      map.on("mousedown", DRAFT_VERTICES_LAYER_ID, (e: MapLayerMouseEvent) => {
        if (readOnlyRef.current) return;
        const index = e.features?.[0]?.properties?.index;
        if (typeof index !== "number") return;
        e.preventDefault();
        draggingRef.current = index;
        onSelectRef.current(index);
        map.getCanvas().style.cursor = "grabbing";
        map.dragPan.disable();
        map.on("mousemove", onMove);
        map.once("mouseup", endDrag);
      });

      map.on("mouseenter", DRAFT_VERTICES_LAYER_ID, () => {
        if (!readOnlyRef.current) map.getCanvas().style.cursor = "grab";
      });
      map.on("mouseleave", DRAFT_VERTICES_LAYER_ID, () => {
        if (draggingRef.current === null) map.getCanvas().style.cursor = "";
      });
      map.on("mouseenter", DRAFT_MIDPOINTS_LAYER_ID, () => {
        if (!readOnlyRef.current) map.getCanvas().style.cursor = "copy";
      });
      map.on("mouseleave", DRAFT_MIDPOINTS_LAYER_ID, () => {
        if (draggingRef.current === null) map.getCanvas().style.cursor = "";
      });

      mapRef.current = map;
    }

    void initialise();
    return () => {
      disposed = true;
      controller.abort();
      window.clearTimeout(timeoutId);
      draggingRef.current = null;
      mapRef.current?.remove();
      mapRef.current = null;
      remoteBasemapRef.current = false;
      fittedRef.current = false;
      setMapReady(false);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Локализация кнопок зума.
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

  // Слои: контекст сети (приглушённо) + черновая геометрия поверх.
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !mapReady) return;
    const colors = MAP_THEME[theme];
    const draftColor = color ?? colors.draft;

    if (!map.getSource(CONTEXT_SOURCE_ID)) {
      map.addSource(CONTEXT_SOURCE_ID, {
        type: "geojson",
        data: contextCollection(context),
      });
      map.addLayer({
        id: CONTEXT_LINES_LAYER_ID,
        type: "line",
        source: CONTEXT_SOURCE_ID,
        filter: ["==", ["get", "feature_type"], "line"],
        layout: { "line-cap": "round", "line-join": "round" },
        paint: {
          "line-color": ["coalesce", ["get", "color_hex"], colors.stationStroke],
          "line-width": 4,
          "line-opacity": 0.35,
        },
      });
      map.addLayer({
        id: CONTEXT_STATIONS_LAYER_ID,
        type: "circle",
        source: CONTEXT_SOURCE_ID,
        filter: ["==", ["get", "feature_type"], "station"],
        paint: {
          "circle-radius": 4,
          "circle-color": colors.stationFill,
          "circle-stroke-width": 1.5,
          "circle-stroke-color": colors.stationStroke,
          "circle-opacity": 0.55,
          "circle-stroke-opacity": 0.55,
        },
      });
    }

    if (!map.getSource(DRAFT_SOURCE_ID)) {
      map.addSource(DRAFT_SOURCE_ID, {
        type: "geojson",
        data: draftCollection(mode, value, selectedIndex, readOnlySegments),
      });
      // Белая обводка + цветная жила — как на карте сети.
      map.addLayer({
        id: `${DRAFT_LINE_LAYER_ID}-casing`,
        type: "line",
        source: DRAFT_SOURCE_ID,
        filter: ["==", ["get", "kind"], "line"],
        layout: { "line-cap": "round", "line-join": "round" },
        paint: {
          "line-color": colors.casing,
          "line-opacity": colors.casingOpacity,
          "line-width": 9,
        },
      });
      map.addLayer({
        id: DRAFT_LINE_LAYER_ID,
        type: "line",
        source: DRAFT_SOURCE_ID,
        filter: ["==", ["get", "kind"], "line"],
        layout: { "line-cap": "round", "line-join": "round" },
        paint: { "line-color": draftColor, "line-width": 5 },
      });
      map.addLayer({
        id: DRAFT_MIDPOINTS_LAYER_ID,
        type: "circle",
        source: DRAFT_SOURCE_ID,
        filter: ["==", ["get", "kind"], "midpoint"],
        paint: {
          "circle-radius": 4,
          "circle-color": colors.casing,
          "circle-opacity": 0.9,
          "circle-stroke-width": 1.5,
          "circle-stroke-color": draftColor,
          "circle-stroke-opacity": 0.9,
        },
      });
      map.addLayer({
        id: DRAFT_VERTICES_LAYER_ID,
        type: "circle",
        source: DRAFT_SOURCE_ID,
        filter: ["==", ["get", "kind"], "vertex"],
        paint: {
          // Выбранная вершина крупнее — состояние читается не только цветом.
          "circle-radius": ["case", ["get", "selected"], 9, 6.5],
          "circle-color": colors.draftHandleFill,
          "circle-stroke-width": ["case", ["get", "selected"], 4, 2.5],
          "circle-stroke-color": draftColor,
        },
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mapReady, theme]);

  // Контекст сети обновился.
  useEffect(() => {
    const source = mapRef.current?.getSource(CONTEXT_SOURCE_ID) as
      | GeoJSONSource
      | undefined;
    source?.setData(contextCollection(context));
  }, [context, mapReady]);

  // Черновая геометрия обновилась (клик/кнопки/числовой ввод).
  useEffect(() => {
    if (!mapReady) return;
    syncDraft(value, selectedIndex);
  }, [value, selectedIndex, mapReady, syncDraft]);

  // Перекраска по теме.
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !mapReady) return;
    const colors = MAP_THEME[theme];
    const draftColor = color ?? colors.draft;
    if (!remoteBasemapRef.current && map.getLayer(BACKGROUND_LAYER_ID)) {
      map.setPaintProperty(BACKGROUND_LAYER_ID, "background-color", colors.bg);
    }
    if (map.getLayer(`${DRAFT_LINE_LAYER_ID}-casing`)) {
      map.setPaintProperty(`${DRAFT_LINE_LAYER_ID}-casing`, "line-color", colors.casing);
      map.setPaintProperty(
        `${DRAFT_LINE_LAYER_ID}-casing`,
        "line-opacity",
        colors.casingOpacity,
      );
    }
    if (map.getLayer(DRAFT_LINE_LAYER_ID)) {
      map.setPaintProperty(DRAFT_LINE_LAYER_ID, "line-color", draftColor);
    }
    if (map.getLayer(DRAFT_VERTICES_LAYER_ID)) {
      map.setPaintProperty(DRAFT_VERTICES_LAYER_ID, "circle-color", colors.draftHandleFill);
      map.setPaintProperty(DRAFT_VERTICES_LAYER_ID, "circle-stroke-color", draftColor);
    }
    if (map.getLayer(DRAFT_MIDPOINTS_LAYER_ID)) {
      map.setPaintProperty(DRAFT_MIDPOINTS_LAYER_ID, "circle-color", colors.casing);
      map.setPaintProperty(DRAFT_MIDPOINTS_LAYER_ID, "circle-stroke-color", draftColor);
    }
    if (map.getLayer(CONTEXT_STATIONS_LAYER_ID)) {
      map.setPaintProperty(CONTEXT_STATIONS_LAYER_ID, "circle-color", colors.stationFill);
      map.setPaintProperty(
        CONTEXT_STATIONS_LAYER_ID,
        "circle-stroke-color",
        colors.stationStroke,
      );
    }
  }, [theme, color, mapReady]);

  // Первая подгонка вида под существующую геометрию (один раз).
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !mapReady || fittedRef.current) return;
    // В режиме чтения подгоняем вид под ветвистую трассу — она и есть геометрия.
    const fitPoints = readOnlySegments?.length ? readOnlySegments.flat() : value;
    if (fitPoints.length === 0) return;
    fittedRef.current = true;
    if (fitPoints.length === 1) {
      map.jumpTo({ center: fitPoints[0], zoom: Math.max(map.getZoom(), 14) });
      return;
    }
    const bounds = fitPoints.reduce(
      (acc, p) => acc.extend(p),
      new maplibregl.LngLatBounds(fitPoints[0], fitPoints[0]),
    );
    map.fitBounds(bounds, { padding: 48, animate: !prefersReducedMotion() });
  }, [value, readOnlySegments, mapReady]);

  return (
    <div
      ref={containerRef}
      role="region"
      aria-label={regionLabel}
      className="h-full w-full"
    />
  );
}
