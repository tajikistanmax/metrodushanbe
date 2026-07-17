/**
 * Ближайшие прибытия для карточки станции.
 *
 * Для каждой линии сначала читаем публичный API расписания. Если backend
 * недоступен, рассчитываем те же headway-based значения из демонстрационных
 * графиков V012. Демо-значения явно помечаются источником `demo` в UI.
 */

import { fetchApiJson } from "./api";
import type {
  Arrival,
  StationArrivals,
  StationArrivalsResult,
} from "./types";

const LIMIT = 3;
const DUSHANBE_TIME_ZONE = "Asia/Dushanbe";
const DAY_TYPES = ["weekday", "weekend", "holiday"] as const;

type DayType = (typeof DAY_TYPES)[number];
type DemoSchedule = {
  firstDeparture: string;
  lastDeparture: string;
  headwayMinutes: number;
};

/** Синхронизировано с backend migration V012__seed_line_schedule.sql. */
const DEMO_SCHEDULES: Record<
  string,
  Partial<Record<DayType, DemoSchedule>>
> = {
  L1: {
    weekday: {
      firstDeparture: "06:00",
      lastDeparture: "23:00",
      headwayMinutes: 5,
    },
    weekend: {
      firstDeparture: "06:30",
      lastDeparture: "22:30",
      headwayMinutes: 7,
    },
    holiday: {
      firstDeparture: "07:00",
      lastDeparture: "22:00",
      headwayMinutes: 8,
    },
  },
  L2: {
    weekday: {
      firstDeparture: "06:00",
      lastDeparture: "23:00",
      headwayMinutes: 6,
    },
    weekend: {
      firstDeparture: "06:30",
      lastDeparture: "22:30",
      headwayMinutes: 8,
    },
  },
};

function isDayType(value: unknown): value is DayType {
  return typeof value === "string" && DAY_TYPES.includes(value as DayType);
}

function parseArrival(value: unknown): Arrival | null {
  if (!value || typeof value !== "object") {
    return null;
  }
  const raw = value as Record<string, unknown>;
  if (
    typeof raw.time !== "string" ||
    !/^\d{2}:\d{2}$/.test(raw.time) ||
    typeof raw.etaMinutes !== "number" ||
    !Number.isInteger(raw.etaMinutes) ||
    raw.etaMinutes < 0
  ) {
    return null;
  }
  return { time: raw.time, etaMinutes: raw.etaMinutes };
}

function assertStationArrivals(value: unknown): StationArrivals {
  if (!value || typeof value !== "object") {
    throw new Error("Ответ прибытий не является объектом");
  }
  const raw = value as Record<string, unknown>;
  const arrivals = Array.isArray(raw.arrivals)
    ? raw.arrivals.map(parseArrival)
    : [];

  if (
    typeof raw.stationCode !== "string" ||
    typeof raw.lineCode !== "string" ||
    !isDayType(raw.dayType) ||
    typeof raw.serviceActive !== "boolean" ||
    !(
      raw.headwayMinutes === null ||
      (typeof raw.headwayMinutes === "number" &&
        Number.isInteger(raw.headwayMinutes) &&
        raw.headwayMinutes > 0)
    ) ||
    typeof raw.estimated !== "boolean" ||
    typeof raw.generatedAt !== "string" ||
    arrivals.some((arrival) => arrival === null)
  ) {
    throw new Error("Ответ прибытий не соответствует контракту");
  }

  return {
    stationCode: raw.stationCode,
    lineCode: raw.lineCode,
    dayType: raw.dayType,
    serviceActive: raw.serviceActive,
    headwayMinutes: raw.headwayMinutes,
    estimated: raw.estimated,
    generatedAt: raw.generatedAt,
    arrivals: arrivals as Arrival[],
  };
}

function minutesFromTime(value: string): number {
  const [hours, minutes] = value.split(":").map(Number);
  return hours * 60 + minutes;
}

function formatTime(totalMinutes: number): string {
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}`;
}

function dushanbeTime(now: Date): { dayType: DayType; minuteOfDay: number } {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: DUSHANBE_TIME_ZONE,
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(now);
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((candidate) => candidate.type === type)?.value ?? "";
  const weekday = part("weekday");
  const hour = Number(part("hour"));
  const minute = Number(part("minute"));

  return {
    dayType: weekday === "Sat" || weekday === "Sun" ? "weekend" : "weekday",
    minuteOfDay: hour * 60 + minute,
  };
}

function demoArrivals(
  stationCode: string,
  lineCode: string,
  now = new Date(),
): StationArrivals | null {
  const { dayType, minuteOfDay } = dushanbeTime(now);
  const schedule = DEMO_SCHEDULES[lineCode]?.[dayType];
  if (!schedule) {
    return null;
  }

  const first = minutesFromTime(schedule.firstDeparture);
  const last = minutesFromTime(schedule.lastDeparture);
  const serviceActive = minuteOfDay >= first && minuteOfDay <= last;
  const arrivals: Arrival[] = [];

  if (serviceActive) {
    const steps = Math.max(
      0,
      Math.ceil((minuteOfDay - first) / schedule.headwayMinutes),
    );
    for (
      let candidate = first + steps * schedule.headwayMinutes;
      candidate <= last && arrivals.length < LIMIT;
      candidate += schedule.headwayMinutes
    ) {
      arrivals.push({
        time: formatTime(candidate),
        etaMinutes: candidate - minuteOfDay,
      });
    }
  }

  return {
    stationCode,
    lineCode,
    dayType,
    serviceActive,
    headwayMinutes: serviceActive ? schedule.headwayMinutes : null,
    estimated: true,
    generatedAt: now.toISOString(),
    arrivals,
  };
}

async function loadLineArrivals(
  stationCode: string,
  lineCode: string,
): Promise<StationArrivalsResult | null> {
  try {
    const query = new URLSearchParams({ lineCode, limit: String(LIMIT) });
    const data = assertStationArrivals(
      await fetchApiJson(
        `/stations/${encodeURIComponent(stationCode)}/arrivals?${query}`,
      ),
    );
    if (data.stationCode !== stationCode || data.lineCode !== lineCode) {
      throw new Error("API вернул прибытия другой станции или линии");
    }
    return { data, source: "api" };
  } catch {
    const data = demoArrivals(stationCode, lineCode);
    return data ? { data, source: "demo" } : null;
  }
}

/**
 * Загружает прибытия всех линий станции параллельно. Никогда не бросает:
 * неизвестные линии без API и демо-графика просто не попадают в результат.
 */
export async function loadStationArrivals(
  stationCode: string,
  lineCodes: string[],
): Promise<StationArrivalsResult[]> {
  const results = await Promise.all(
    lineCodes.map((lineCode) => loadLineArrivals(stationCode, lineCode)),
  );
  return results.filter(
    (result): result is StationArrivalsResult => result !== null,
  );
}
