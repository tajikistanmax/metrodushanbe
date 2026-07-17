import {
  isLineFeature,
  isStationFeature,
  type LineFeature,
  type NetworkGeoJson,
  type Route,
  type RouteLeg,
  type RouteStop,
  type StationFeature,
} from "./types";

const SEGMENT_MINUTES = 2;
const TRANSFER_MINUTES = 5;

const CLOSED_LINE_STATUSES = new Set(["suspended", "decommissioned"]);
const CLOSED_STATION_STATUSES = new Set([
  "temporarily_closed",
  "decommissioned",
]);

type Node = {
  key: string;
  stationCode: string;
  lineCode: string;
};

type Edge = {
  to: string;
  minutes: number;
  transfer: boolean;
};

type QueueState = {
  key: string;
  minutes: number;
  transfers: number;
};

function nodeKey(stationCode: string, lineCode: string): string {
  return `${stationCode}\u0000${lineCode}`;
}

function distanceSq(a: [number, number], b: [number, number]): number {
  const dx = a[0] - b[0];
  const dy = a[1] - b[1];
  return dx * dx + dy * dy;
}

function nearestVertexIndex(station: StationFeature, line: LineFeature): number {
  let bestIndex = 0;
  let bestDistance = Number.POSITIVE_INFINITY;

  line.geometry.coordinates.forEach((vertex, index) => {
    const distance = distanceSq(station.geometry.coordinates, vertex);
    if (distance < bestDistance) {
      bestDistance = distance;
      bestIndex = index;
    }
  });

  return bestIndex;
}

function addEdge(
  adjacency: Map<string, Edge[]>,
  from: string,
  to: string,
  minutes: number,
  transfer: boolean,
) {
  const edges = adjacency.get(from) ?? [];
  edges.push({ to, minutes, transfer });
  adjacency.set(from, edges);
}

function isBetter(
  minutes: number,
  transfers: number,
  bestMinutes: number | undefined,
  bestTransfers: number | undefined,
): boolean {
  return (
    bestMinutes === undefined ||
    minutes < bestMinutes ||
    (minutes === bestMinutes && transfers < (bestTransfers ?? Infinity))
  );
}

/**
 * Локальный эквивалент backend-маршрутизации для демонстрации и PWA offline.
 * Узел графа — платформа конкретной линии; Дейкстра ранжирует по времени,
 * затем по числу пересадок. Оценки совпадают с RoutingService.
 */
export function buildOfflineRoute(
  network: NetworkGeoJson,
  from: string,
  to: string,
): Route | null {
  const lines = network.features
    .filter(isLineFeature)
    .filter((line) => !CLOSED_LINE_STATUSES.has(line.properties.status));
  const stations = network.features
    .filter(isStationFeature)
    .filter(
      (station) => !CLOSED_STATION_STATUSES.has(station.properties.status),
    );

  const lineByCode = new Map(lines.map((line) => [line.properties.code, line]));
  const stationByCode = new Map(
    stations.map((station) => [station.properties.code, station]),
  );

  if (!stationByCode.has(from) || !stationByCode.has(to)) {
    return null;
  }

  const nodes = new Map<string, Node>();
  const adjacency = new Map<string, Edge[]>();
  const openLinesByStation = new Map<string, string[]>();

  for (const line of lines) {
    const lineCode = line.properties.code;
    const orderedStations = stations
      .filter((station) => station.properties.lines.includes(lineCode))
      .sort(
        (a, b) =>
          nearestVertexIndex(a, line) - nearestVertexIndex(b, line),
      );

    for (const station of orderedStations) {
      const key = nodeKey(station.properties.code, lineCode);
      nodes.set(key, {
        key,
        stationCode: station.properties.code,
        lineCode,
      });
      adjacency.set(key, adjacency.get(key) ?? []);
      const stationLines = openLinesByStation.get(station.properties.code) ?? [];
      if (!stationLines.includes(lineCode)) {
        stationLines.push(lineCode);
      }
      openLinesByStation.set(station.properties.code, stationLines);
    }

    for (let index = 0; index < orderedStations.length - 1; index += 1) {
      const current = nodeKey(orderedStations[index].properties.code, lineCode);
      const next = nodeKey(orderedStations[index + 1].properties.code, lineCode);
      addEdge(adjacency, current, next, SEGMENT_MINUTES, false);
      addEdge(adjacency, next, current, SEGMENT_MINUTES, false);
    }
  }

  for (const [stationCode, stationLines] of openLinesByStation) {
    const station = stationByCode.get(stationCode);
    if (!station?.properties.is_transfer || stationLines.length < 2) {
      continue;
    }
    for (let left = 0; left < stationLines.length; left += 1) {
      for (let right = left + 1; right < stationLines.length; right += 1) {
        const first = nodeKey(stationCode, stationLines[left]);
        const second = nodeKey(stationCode, stationLines[right]);
        addEdge(adjacency, first, second, TRANSFER_MINUTES, true);
        addEdge(adjacency, second, first, TRANSFER_MINUTES, true);
      }
    }
  }

  const sourceKeys = (openLinesByStation.get(from) ?? []).map((lineCode) =>
    nodeKey(from, lineCode),
  );
  const targetLines = openLinesByStation.get(to) ?? [];
  if (sourceKeys.length === 0 || targetLines.length === 0) {
    return {
      from,
      to,
      found: false,
      estimatedMinutes: 0,
      transfers: 0,
      segmentCount: 0,
      legs: [],
      stops: [],
    };
  }

  const queue: QueueState[] = sourceKeys.map((key) => ({
    key,
    minutes: 0,
    transfers: 0,
  }));
  const bestMinutes = new Map(sourceKeys.map((key) => [key, 0]));
  const bestTransfers = new Map(sourceKeys.map((key) => [key, 0]));
  const previous = new Map<string, string>();
  const settled = new Set<string>();
  let targetKey: string | null = null;

  while (queue.length > 0) {
    queue.sort(
      (a, b) => a.minutes - b.minutes || a.transfers - b.transfers,
    );
    const current = queue.shift();
    if (!current || settled.has(current.key)) {
      continue;
    }
    settled.add(current.key);

    const currentNode = nodes.get(current.key);
    if (currentNode?.stationCode === to) {
      targetKey = current.key;
      break;
    }

    for (const edge of adjacency.get(current.key) ?? []) {
      if (settled.has(edge.to)) {
        continue;
      }
      const minutes = current.minutes + edge.minutes;
      const transfers = current.transfers + (edge.transfer ? 1 : 0);
      if (
        isBetter(
          minutes,
          transfers,
          bestMinutes.get(edge.to),
          bestTransfers.get(edge.to),
        )
      ) {
        bestMinutes.set(edge.to, minutes);
        bestTransfers.set(edge.to, transfers);
        previous.set(edge.to, current.key);
        queue.push({ key: edge.to, minutes, transfers });
      }
    }
  }

  if (!targetKey) {
    return {
      from,
      to,
      found: false,
      estimatedMinutes: 0,
      transfers: 0,
      segmentCount: 0,
      legs: [],
      stops: [],
    };
  }

  const path: Node[] = [];
  let cursor: string | undefined = targetKey;
  while (cursor) {
    const node = nodes.get(cursor);
    if (!node) {
      return null;
    }
    path.unshift(node);
    cursor = previous.get(cursor);
  }

  const legGroups: Node[][] = [];
  for (const node of path) {
    const currentGroup = legGroups[legGroups.length - 1];
    if (!currentGroup || currentGroup[0].lineCode !== node.lineCode) {
      legGroups.push([node]);
    } else {
      currentGroup.push(node);
    }
  }

  const legs: RouteLeg[] = legGroups.map((group) => {
    const lineCode = group[0].lineCode;
    const line = lineByCode.get(lineCode);
    const stationCodes = group.map((node) => node.stationCode);
    const segmentCount = Math.max(0, stationCodes.length - 1);
    return {
      lineCode,
      lineName: line?.properties.name ?? {
        tg: lineCode,
        ru: lineCode,
        en: lineCode,
      },
      colorHex: line?.properties.color_hex ?? "#64748B",
      stations: stationCodes,
      segmentCount,
      estimatedMinutes: segmentCount * SEGMENT_MINUTES,
    };
  });

  const stops: RouteStop[] = [];
  legGroups.forEach((group, legIndex) => {
    group.forEach((node, nodeIndex) => {
      if (legIndex > 0 && nodeIndex === 0) {
        return;
      }
      const station = stationByCode.get(node.stationCode);
      stops.push({
        code: node.stationCode,
        name: station?.properties.name ?? {
          tg: node.stationCode,
          ru: node.stationCode,
          en: node.stationCode,
        },
        lineCode: node.lineCode,
        transfer:
          legIndex < legGroups.length - 1 && nodeIndex === group.length - 1,
      });
    });
  });

  const segmentCount = legs.reduce(
    (total, leg) => total + leg.segmentCount,
    0,
  );
  const transfers = Math.max(0, legs.length - 1);

  return {
    from,
    to,
    found: true,
    estimatedMinutes:
      segmentCount * SEGMENT_MINUTES + transfers * TRANSFER_MINUTES,
    transfers,
    segmentCount,
    legs,
    stops,
  };
}
