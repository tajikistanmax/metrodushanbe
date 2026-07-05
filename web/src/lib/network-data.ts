/**
 * Загрузка данных сети метро с деградацией в офлайн (dev-conventions.md, §8):
 * 1) пробуем API GET {NEXT_PUBLIC_API_BASE}/network/geojson с таймаутом
 *    API_TIMEOUT_MS (общий хелпер fetchApiJson из lib/api.ts);
 * 2) при любой ошибке (нет сети, не 2xx, таймаут, битый JSON) — читаем
 *    бандл-копию /data/demo-network.geojson.
 */

import { fetchApiJson } from "./api";
import type { DataSource, NetworkGeoJson } from "./types";

/** Путь к офлайн-копии демо-данных внутри web/public. */
const DEMO_DATA_URL = "/data/demo-network.geojson";

export type NetworkDataResult = {
  data: NetworkGeoJson;
  source: DataSource;
};

/** Минимальная проверка, что ответ похож на FeatureCollection сети. */
function assertNetworkGeoJson(value: unknown): NetworkGeoJson {
  const candidate = value as NetworkGeoJson | null;
  if (
    !candidate ||
    candidate.type !== "FeatureCollection" ||
    !Array.isArray(candidate.features)
  ) {
    throw new Error("Ответ не является FeatureCollection сети метро");
  }
  return candidate;
}

async function fetchFromApi(): Promise<NetworkGeoJson> {
  const payload = await fetchApiJson(
    "/network/geojson",
    "application/geo+json, application/json",
  );
  return assertNetworkGeoJson(payload);
}

async function fetchDemoFallback(): Promise<NetworkGeoJson> {
  const response = await fetch(DEMO_DATA_URL);
  if (!response.ok) {
    throw new Error(`Демо-данные недоступны: HTTP ${response.status}`);
  }
  return assertNetworkGeoJson(await response.json());
}

/**
 * Возвращает данные сети и фактический источник:
 * `api` — живой бэкенд, `demo` — офлайн-fallback из бандла.
 */
export async function loadNetworkData(): Promise<NetworkDataResult> {
  try {
    const data = await fetchFromApi();
    return { data, source: "api" };
  } catch {
    const data = await fetchDemoFallback();
    return { data, source: "demo" };
  }
}
