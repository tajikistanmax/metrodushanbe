/**
 * Загрузка детальной карточки станции с офлайн-деградацией
 * (dev-conventions.md, §8): пробуем API GET {NEXT_PUBLIC_API_BASE}/stations/{code}
 * с таймаутом; при ЛЮБОЙ ошибке (нет сети, не 2xx, таймаут, битый JSON,
 * несоответствие контракту) возвращаем `null` — панель показывает базовую
 * информацию из данных сети и отметку «детали недоступны».
 *
 * Для деталей отдельной станции офлайн-копии в бандле нет (в отличие от
 * network-data.ts), поэтому fallback — именно `null`, а не демо-данные.
 */

import type {
  AccessibilityFeatureStatus,
  I18nName,
  LngLat,
  StationAccessibilityFeature,
  StationDetail,
  StationExit,
} from "./types";

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080/api/v1";

/** Таймаут запроса к API, мс (как в network-data.ts). */
const API_TIMEOUT_MS = 2000;

const FEATURE_STATUSES: readonly AccessibilityFeatureStatus[] = [
  "available",
  "out_of_service",
  "planned",
];

function isI18nName(value: unknown): value is I18nName {
  if (!value || typeof value !== "object") {
    return false;
  }
  const name = value as Record<string, unknown>;
  return (
    typeof name.tg === "string" &&
    typeof name.ru === "string" &&
    typeof name.en === "string"
  );
}

function isLngLat(value: unknown): value is LngLat {
  return (
    Array.isArray(value) &&
    value.length === 2 &&
    typeof value[0] === "number" &&
    typeof value[1] === "number"
  );
}

function parseExit(value: unknown): StationExit | null {
  if (!value || typeof value !== "object") {
    return null;
  }
  const exit = value as Record<string, unknown>;
  if (
    typeof exit.code !== "string" ||
    !isI18nName(exit.name) ||
    typeof exit.isAccessible !== "boolean" ||
    !isLngLat(exit.coordinates)
  ) {
    return null;
  }
  return {
    code: exit.code,
    name: exit.name,
    isAccessible: exit.isAccessible,
    coordinates: exit.coordinates,
  };
}

function parseFeature(value: unknown): StationAccessibilityFeature | null {
  if (!value || typeof value !== "object") {
    return null;
  }
  const feature = value as Record<string, unknown>;
  if (
    typeof feature.type !== "string" ||
    !isI18nName(feature.description) ||
    typeof feature.status !== "string" ||
    !FEATURE_STATUSES.includes(feature.status as AccessibilityFeatureStatus)
  ) {
    return null;
  }
  return {
    type: feature.type as StationAccessibilityFeature["type"],
    description: feature.description,
    status: feature.status as AccessibilityFeatureStatus,
  };
}

/** Проверка ответа на соответствие контракту GET /stations/{code}. */
function assertStationDetail(value: unknown): StationDetail {
  if (!value || typeof value !== "object") {
    throw new Error("Ответ не является объектом станции");
  }
  const raw = value as Record<string, unknown>;
  if (typeof raw.code !== "string" || !isI18nName(raw.name)) {
    throw new Error("Ответ не соответствует контракту станции");
  }

  const exits = Array.isArray(raw.exits)
    ? raw.exits.map(parseExit).filter((e): e is StationExit => e !== null)
    : [];
  const accessibilityFeatures = Array.isArray(raw.accessibilityFeatures)
    ? raw.accessibilityFeatures
        .map(parseFeature)
        .filter((f): f is StationAccessibilityFeature => f !== null)
    : [];

  return {
    code: raw.code,
    name: raw.name,
    status: raw.status as StationDetail["status"],
    lines: Array.isArray(raw.lines)
      ? raw.lines.filter((l): l is string => typeof l === "string")
      : [],
    isTransfer: raw.isTransfer === true,
    accessibility: Array.isArray(raw.accessibility)
      ? (raw.accessibility.filter(
          (a) => typeof a === "string",
        ) as StationDetail["accessibility"])
      : [],
    coordinates: isLngLat(raw.coordinates) ? raw.coordinates : [0, 0],
    exits,
    accessibilityFeatures,
  };
}

/**
 * Возвращает детали станции по коду или `null` при любой ошибке/офлайне.
 * Никогда не бросает исключение — вызывающий код не обязан её ловить.
 */
export async function loadStationDetail(
  code: string,
): Promise<StationDetail | null> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), API_TIMEOUT_MS);
  try {
    const response = await fetch(
      `${API_BASE}/stations/${encodeURIComponent(code)}`,
      {
        signal: controller.signal,
        headers: { Accept: "application/json" },
        cache: "no-store",
      },
    );
    if (!response.ok) {
      return null;
    }
    return assertStationDetail(await response.json());
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}
