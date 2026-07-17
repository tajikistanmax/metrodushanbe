/**
 * Общая конфигурация карт операционной консоли (MapLibre GL).
 *
 * Повторяет приёмы публичного портала (web/src/components/NetworkMap.tsx):
 * - базовый стиль берётся из NEXT_PUBLIC_MAP_STYLE_URL;
 * - при недоступности сети карта деградирует к локальному плоскому стилю
 *   (OFFLINE_STYLE) — консоль обязана работать без интернета;
 * - палитра слоёв задаётся темой (MAP_THEME), тёмная тема перекрашивается.
 *
 * Модуль изоморфный (без "server-only"): импортируется клиентскими картами.
 */

import type { StyleSpecification } from "maplibre-gl";
import type { ResolvedTheme } from "@/shared/ThemeProvider";
import type { LngLat, NetworkFeature, NetworkGeoJson } from "./types";

/** Центр Душанбе и стартовый зум — те же, что на публичной карте. */
export const MAP_CENTER: [number, number] = [68.787, 38.574];
export const MAP_ZOOM = 11.5;

export const MAP_STYLE_URL =
  process.env.NEXT_PUBLIC_MAP_STYLE_URL ??
  "https://tiles.openfreemap.org/styles/liberty";

export const BACKGROUND_LAYER_ID = "background";

/** Палитра карты по теме (арт-директива §5/§7) — зеркало web/NetworkMap. */
export const MAP_THEME: Record<
  ResolvedTheme,
  {
    bg: string;
    casing: string;
    casingOpacity: number;
    stationFill: string;
    stationStroke: string;
    /** Цвет черновой геометрии в редакторе (брендовый красный / светлый). */
    draft: string;
    draftHandleFill: string;
    draftHandleStroke: string;
  }
> = {
  light: {
    bg: "#EDF1F5",
    casing: "#FFFFFF",
    casingOpacity: 1,
    stationFill: "#FFFFFF",
    stationStroke: "#082742",
    draft: "#E21B2D",
    draftHandleFill: "#FFFFFF",
    draftHandleStroke: "#082742",
  },
  dark: {
    bg: "#0B1622",
    casing: "#FFFFFF",
    casingOpacity: 0.85,
    stationFill: "#0F1D2E",
    stationStroke: "#F2F5F8",
    draft: "#FF5A67",
    draftHandleFill: "#0F1D2E",
    draftHandleStroke: "#F2F5F8",
  },
};

/** Локальный плоский стиль — фон без сети (офлайн-деградация, §8). */
export const OFFLINE_STYLE: StyleSpecification = {
  version: 8,
  sources: {},
  layers: [
    {
      id: BACKGROUND_LAYER_ID,
      type: "background",
      paint: { "background-color": MAP_THEME.light.bg },
    },
  ],
};

/**
 * Загрузка базового стиля с таймаутом. Возвращает удалённый стиль, если он
 * доступен, иначе — OFFLINE_STYLE. `remote` сообщает, какой стиль применён:
 * от этого зависит и атрибуция, и перекраска фонового слоя по теме.
 */
export async function loadMapStyle(
  signal: AbortSignal,
): Promise<{ style: StyleSpecification; remote: boolean }> {
  try {
    const response = await fetch(MAP_STYLE_URL, {
      signal,
      headers: { Accept: "application/json" },
    });
    if (!response.ok) {
      throw new Error(`Map style HTTP ${response.status}`);
    }
    return { style: (await response.json()) as StyleSpecification, remote: true };
  } catch {
    return { style: OFFLINE_STYLE, remote: false };
  }
}

export const MAP_ATTRIBUTION =
  '<a href="https://openfreemap.org" target="_blank">OpenFreeMap</a> · <a href="https://www.openstreetmap.org/copyright" target="_blank">© OpenStreetMap</a>';

// --- Геометрия трассы линии ------------------------------------------------

/**
 * Сегменты трассы линии из фичи GeoJSON.
 *
 * Backend (GeoJsonBuilder.lineGeometry) разворачивает MultiLineString из одного
 * сегмента в LineString и отдаёт MultiLineString только когда сегментов больше
 * одного. Поэтому число сегментов здесь — достоверный признак ветвистой трассы.
 */
export function lineSegments(feature: NetworkFeature): LngLat[][] {
  const geometry = feature.geometry;
  if (geometry.type === "LineString") {
    return [geometry.coordinates];
  }
  if (geometry.type === "MultiLineString") {
    return geometry.coordinates;
  }
  return [];
}

/** Трасса линии по коду: сегменты из /network/geojson (пусто, если геометрии нет). */
export function lineSegmentsByCode(
  network: NetworkGeoJson | null,
  code: string,
): LngLat[][] {
  const feature = (network?.features ?? []).find(
    (f) => f.properties.feature_type === "line" && f.properties.code === code,
  );
  return feature ? lineSegments(feature) : [];
}
