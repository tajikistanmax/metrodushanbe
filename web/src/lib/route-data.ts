/**
 * Построение маршрута «откуда/куда» (GET /api/v1/routes) с офлайн-деградацией
 * (dev-conventions.md, §8): общий хелпер fetchApiJson из lib/api.ts (таймаут
 * API_TIMEOUT_MS). Различаем ДВА исхода:
 *  - «ошибка/офлайн» (нет сети, не 2xx — в т.ч. 404 route.station_not_found,
 *    таймаут, битый JSON, несоответствие контракту) → `null`;
 *  - «нет пути» — валидный ответ с found:false — пробрасывается как есть
 *    (это НЕ ошибка сети, а полноценный результат построения).
 *
 * Время в пути — ОЦЕНОЧНОЕ, до публикации реального расписания.
 */

import { fetchApiJson } from "./api";
import type { I18nName, Route, RouteLeg, RouteStop } from "./types";

/** Мультиязычное название: все три языка обязательны и являются строками. */
function isI18nName(value: unknown): value is I18nName {
  const candidate = value as I18nName | null;
  return (
    typeof candidate === "object" &&
    candidate !== null &&
    typeof candidate.tg === "string" &&
    typeof candidate.ru === "string" &&
    typeof candidate.en === "string"
  );
}

/**
 * Проверка участка маршрута по контракту RouteLeg. Невалидный участок
 * отбрасывается поштучно (null), маршрут при этом остаётся валидным.
 */
function parseLeg(value: unknown): RouteLeg | null {
  const candidate = value as RouteLeg | null;
  if (
    !candidate ||
    typeof candidate !== "object" ||
    typeof candidate.lineCode !== "string" ||
    !isI18nName(candidate.lineName) ||
    typeof candidate.colorHex !== "string" ||
    !Array.isArray(candidate.stations) ||
    typeof candidate.segmentCount !== "number" ||
    typeof candidate.estimatedMinutes !== "number"
  ) {
    return null;
  }
  return {
    lineCode: candidate.lineCode,
    lineName: candidate.lineName,
    colorHex: candidate.colorHex,
    stations: candidate.stations.filter(
      (code): code is string => typeof code === "string",
    ),
    segmentCount: candidate.segmentCount,
    estimatedMinutes: candidate.estimatedMinutes,
  };
}

/** Проверка остановки по контракту RouteStop; невалидная — отбрасывается. */
function parseStop(value: unknown): RouteStop | null {
  const candidate = value as RouteStop | null;
  if (
    !candidate ||
    typeof candidate !== "object" ||
    typeof candidate.code !== "string" ||
    !isI18nName(candidate.name) ||
    typeof candidate.lineCode !== "string" ||
    typeof candidate.transfer !== "boolean"
  ) {
    return null;
  }
  return {
    code: candidate.code,
    name: candidate.name,
    lineCode: candidate.lineCode,
    transfer: candidate.transfer,
  };
}

/**
 * Проверка ответа на соответствие контракту Route. Несоответствие обязательных
 * полей (from/to/found/числа/массивы) — `null` (трактуется как ошибка).
 * found:false с пустыми legs/stops — валиден.
 */
function parseRoute(value: unknown): Route | null {
  const candidate = value as Route | null;
  if (
    !candidate ||
    typeof candidate !== "object" ||
    typeof candidate.from !== "string" ||
    typeof candidate.to !== "string" ||
    typeof candidate.found !== "boolean" ||
    typeof candidate.estimatedMinutes !== "number" ||
    typeof candidate.transfers !== "number" ||
    typeof candidate.segmentCount !== "number" ||
    !Array.isArray(candidate.legs) ||
    !Array.isArray(candidate.stops)
  ) {
    return null;
  }
  return {
    from: candidate.from,
    to: candidate.to,
    found: candidate.found,
    estimatedMinutes: candidate.estimatedMinutes,
    transfers: candidate.transfers,
    segmentCount: candidate.segmentCount,
    legs: candidate.legs
      .map(parseLeg)
      .filter((leg): leg is RouteLeg => leg !== null),
    stops: candidate.stops
      .map(parseStop)
      .filter((stop): stop is RouteStop => stop !== null),
  };
}

/**
 * Строит маршрут между станциями по их кодам. Возвращает:
 *  - `Route` (в т.ч. found:false — «нет пути») при валидном ответе backend;
 *  - `null` при недоступном/ошибочном backend (в т.ч. 404 несуществующего
 *    кода) — вызывающий код показывает сообщение об ошибке сети.
 * Никогда не бросает исключение.
 */
export async function loadRoute(
  from: string,
  to: string,
): Promise<Route | null> {
  try {
    const payload = await fetchApiJson(
      `/routes?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
    );
    return parseRoute(payload);
  } catch {
    // Офлайн-принцип: любая ошибка сети/HTTP — null (не «нет пути»)
    return null;
  }
}
