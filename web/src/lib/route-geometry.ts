/**
 * Геометрия построенного маршрута для подсветки на карте.
 *
 * Маршрут (Route) описан кодами станций, а не координатами: чтобы подсветить
 * его на карте, участок нужно уложить НА ТРАССУ линии, а не соединять станции
 * прямыми. Станции демо-данных лежат на вершинах LineString линии, поэтому для
 * каждой станции берём ближайшую вершину и режем трассу между крайними
 * вершинами участка. Если линия отсутствует в данных сети (участок из ответа
 * backend по неизвестной линии) — деградируем к ломаной по самим станциям:
 * маршрут всё равно виден, просто спрямлён.
 *
 * Источник данных здесь не важен: и loadRoute, и buildOfflineRoute отдают
 * один и тот же контракт Route, поэтому подсветка работает и офлайн (§8).
 */

import type { FeatureCollection, Feature, LineString } from "geojson";
import {
  isLineFeature,
  isStationFeature,
  type LineFeature,
  type NetworkGeoJson,
  type Route,
  type StationFeature,
} from "./types";

/** Пустая коллекция — «маршрута нет» для GeoJSON-источника карты. */
export const EMPTY_COLLECTION: FeatureCollection = {
  type: "FeatureCollection",
  features: [],
};

/** Квадрат расстояния между точками [lng, lat]. */
function distanceSq(a: [number, number], b: [number, number]): number {
  const dx = a[0] - b[0];
  const dy = a[1] - b[1];
  return dx * dx + dy * dy;
}

/** Индекс ближайшей вершины линии — станции демо-данных лежат на вершинах. */
function nearestVertexIndex(station: StationFeature, line: LineFeature): number {
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

function stationsByCode(data: NetworkGeoJson): Map<string, StationFeature> {
  return new Map(
    data.features
      .filter(isStationFeature)
      .map((station) => [station.properties.code, station]),
  );
}

function linesByCode(data: NetworkGeoJson): Map<string, LineFeature> {
  return new Map(
    data.features.filter(isLineFeature).map((line) => [line.properties.code, line]),
  );
}

/** Координаты станции по коду; null — станции нет в данных сети. */
export function stationCoords(
  data: NetworkGeoJson,
  code: string,
): [number, number] | null {
  return stationsByCode(data).get(code)?.geometry.coordinates ?? null;
}

/** Точка маршрута с координатами (для маркеров поверх карты). */
export type RoutePoint = {
  code: string;
  coords: [number, number];
};

/**
 * Станции пересадок построенного маршрута. Пересадка обязана читаться на карте
 * не только цветом — по этим точкам ставятся маркеры-кольца с иконкой.
 */
export function routeTransferPoints(
  data: NetworkGeoJson,
  route: Route,
): RoutePoint[] {
  const stations = stationsByCode(data);
  const points: RoutePoint[] = [];
  for (const stop of route.stops) {
    if (!stop.transfer) {
      continue;
    }
    const coords = stations.get(stop.code)?.geometry.coordinates;
    if (coords && !points.some((point) => point.code === stop.code)) {
      points.push({ code: stop.code, coords });
    }
  }
  return points;
}

/**
 * Коллекция участков маршрута: по одному LineString на участок со свойством
 * color_hex (цвет линии участка) и line_code. found:false и участки короче
 * двух точек дают пустую коллекцию — подсвечивать нечего.
 */
export function buildRouteGeoJson(
  data: NetworkGeoJson,
  route: Route,
): FeatureCollection {
  if (!route.found) {
    return EMPTY_COLLECTION;
  }

  const stations = stationsByCode(data);
  const lines = linesByCode(data);
  const features: Feature<LineString>[] = [];

  route.legs.forEach((leg, index) => {
    const legStations = leg.stations
      .map((code) => stations.get(code))
      .filter((station): station is StationFeature => station !== undefined);
    if (legStations.length < 2) {
      return; // участок из одной станции — рисовать нечего
    }

    const line = lines.get(leg.lineCode);
    let coordinates: [number, number][];

    if (line && line.geometry.coordinates.length >= 2) {
      const first = nearestVertexIndex(legStations[0], line);
      const last = nearestVertexIndex(legStations[legStations.length - 1], line);
      const start = Math.min(first, last);
      const end = Math.max(first, last);
      const slice = line.geometry.coordinates.slice(start, end + 1);
      // Направление участка — от первой станции к последней
      coordinates = (first <= last ? slice : [...slice].reverse()) as [
        number,
        number,
      ][];
    } else {
      coordinates = legStations.map((station) => station.geometry.coordinates);
    }

    if (coordinates.length < 2) {
      coordinates = legStations.map((station) => station.geometry.coordinates);
    }

    features.push({
      type: "Feature",
      id: index,
      properties: {
        color_hex: leg.colorHex,
        line_code: leg.lineCode,
      },
      geometry: { type: "LineString", coordinates },
    });
  });

  return { type: "FeatureCollection", features };
}

/** Границы маршрута [[west, south], [east, north]]; null — точек нет. */
export function routeBounds(
  collection: FeatureCollection,
): [[number, number], [number, number]] | null {
  let west = Number.POSITIVE_INFINITY;
  let south = Number.POSITIVE_INFINITY;
  let east = Number.NEGATIVE_INFINITY;
  let north = Number.NEGATIVE_INFINITY;

  for (const feature of collection.features) {
    if (feature.geometry.type !== "LineString") {
      continue;
    }
    for (const [lng, lat] of feature.geometry.coordinates) {
      west = Math.min(west, lng);
      east = Math.max(east, lng);
      south = Math.min(south, lat);
      north = Math.max(north, lat);
    }
  }

  if (!Number.isFinite(west) || !Number.isFinite(south)) {
    return null;
  }
  return [
    [west, south],
    [east, north],
  ];
}
