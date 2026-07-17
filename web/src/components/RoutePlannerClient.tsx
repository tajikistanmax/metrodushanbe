"use client";

/**
 * Маршрутный поиск «откуда/куда» (GET /routes). Станции для выбора берём из
 * данных сети (loadNetworkData, API → офлайн-демо), маршрут строим через
 * loadRoute с офлайн-деградацией. Различаем три исхода построения:
 *  - недоступный backend (loadRoute → null) — локальный расчёт по данным сети;
 *  - валидный ответ found:false — «пути нет»;
 *  - found:true — участки по линиям, пересадки и ОЦЕНОЧНОЕ время.
 * Время явно помечено как оценочное (до реального расписания).
 */

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { lineBadgeLabel, pickName } from "@/lib/i18n";
import { loadNetworkData, type NetworkDataResult } from "@/lib/network-data";
import { buildOfflineRoute } from "@/lib/offline-route";
import { loadRoute } from "@/lib/route-data";
import {
  isLineFeature,
  isStationFeature,
  type LineFeature,
  type NetworkGeoJson,
  type Route,
  type StationFeature,
} from "@/lib/types";
import Header from "./Header";
import { useI18n } from "./I18nProvider";

/** id основного контейнера — цель skip-link (A11Y). */
const MAIN_ID = "route-content";

type LineGroup = {
  line: LineFeature;
  stations: StationFeature[];
};

/** Квадрат расстояния между точками [lng, lat] (для сортировки вдоль трассы). */
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

/** Группировка станций по линиям с сортировкой вдоль трассы (как в панели). */
function groupByLine(data: NetworkGeoJson): LineGroup[] {
  const lines = data.features
    .filter(isLineFeature)
    .sort((a, b) => a.properties.sort_order - b.properties.sort_order);
  const stations = data.features.filter(isStationFeature);

  return lines.map((line) => ({
    line,
    stations: stations
      .filter((s) => s.properties.lines.includes(line.properties.code))
      .sort(
        (a, b) => nearestVertexIndex(a, line) - nearestVertexIndex(b, line),
      ),
  }));
}

/** Состояние построения маршрута. */
type Status = "idle" | "loading" | "done" | "error";

/**
 * Иконка пересадки (две встречные стрелки). Смысл дублируется текстом рядом —
 * не только цветом (WCAG 2.2, SC 1.4.1).
 */
function TransferIcon() {
  return (
    <svg
      aria-hidden="true"
      focusable="false"
      width={16}
      height={16}
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M4 5h8M4 5l2.2-2.2M4 5l2.2 2.2" />
      <path d="M12 11H4M12 11l-2.2-2.2M12 11l-2.2 2.2" />
    </svg>
  );
}

/** Выпадающий список выбора станции, сгруппированный по линиям (optgroup). */
function StationSelect({
  id,
  label,
  placeholder,
  value,
  groups,
  onChange,
}: {
  id: string;
  label: string;
  placeholder: string;
  value: string;
  groups: LineGroup[];
  onChange: (code: string) => void;
}) {
  const { lang } = useI18n();
  return (
    <div className="flex-1">
      <label
        htmlFor={id}
        className="mb-1 block text-xs font-bold uppercase tracking-wide text-text-secondary"
      >
        {label}
      </label>
      <select
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="h-11 w-full rounded-xl border border-[var(--panel-border)] bg-[var(--field-bg)] px-3 text-sm font-semibold text-[var(--text-primary)]"
      >
        <option value="">{placeholder}</option>
        {groups.map((group) => (
          <optgroup
            key={group.line.properties.code}
            label={`${lineBadgeLabel(group.line.properties.code, lang)} · ${pickName(
              group.line.properties.name,
              lang,
            )}`}
          >
            {group.stations.map((station) => (
              <option
                key={`${group.line.properties.code}-${station.properties.code}`}
                value={station.properties.code}
              >
                {pickName(station.properties.name, lang)}
              </option>
            ))}
          </optgroup>
        ))}
      </select>
    </div>
  );
}

/** Плитка сводки маршрута (значение + подпись). */
function SummaryTile({ value, label }: { value: string; label: string }) {
  return (
    <div className="rounded-xl border border-[var(--panel-border)] bg-[var(--control-hover)] px-3 py-2.5 text-center">
      <div className="text-lg font-extrabold leading-tight text-[var(--text-primary)]">
        {value}
      </div>
      <div className="mt-0.5 text-[11px] font-semibold uppercase tracking-wide text-text-secondary">
        {label}
      </div>
    </div>
  );
}

export default function RoutePlannerClient() {
  const { lang, dict } = useI18n();
  const [network, setNetwork] = useState<NetworkDataResult | null>(null);
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [route, setRoute] = useState<Route | null>(null);
  const [status, setStatus] = useState<Status>("idle");
  // Монотонный счётчик запросов — результат устаревшего построения не должен
  // перезаписать актуальный (гонки при повторных нажатиях).
  const reqRef = useRef(0);

  useEffect(() => {
    let cancelled = false;
    loadNetworkData()
      .then((res) => {
        if (!cancelled) {
          setNetwork(res);
        }
      })
      .catch(() => {
        // Данные сети недоступны даже офлайн — селекты останутся пустыми
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const groups = useMemo(
    () => (network ? groupByLine(network.data) : []),
    [network],
  );

  const sameStation = from !== "" && from === to;
  const canSubmit = from !== "" && to !== "" && !sameStation;

  const handleSubmit = useCallback(() => {
    if (!canSubmit) {
      return;
    }
    const id = ++reqRef.current;
    setStatus("loading");
    setRoute(null);
    loadRoute(from, to).then((res) => {
      if (id !== reqRef.current) {
        return; // устаревший ответ — игнорируем
      }
      if (res === null) {
        const offlineRoute = network
          ? buildOfflineRoute(network.data, from, to)
          : null;
        if (offlineRoute === null) {
          setStatus("error");
          return;
        }
        setRoute(offlineRoute);
        setStatus("done");
        return;
      }
      setRoute(res);
      setStatus("done");
    });
  }, [canSubmit, from, network, to]);

  const handleSwap = useCallback(() => {
    setFrom(to);
    setTo(from);
  }, [from, to]);

  const showRoute = status === "done" && route !== null && route.found;
  const showNotFound = status === "done" && route !== null && !route.found;

  return (
    <div className="flex min-h-dvh w-full flex-col">
      {/* Skip-link — первый фокусируемый элемент страницы (A11Y) */}
      <a href={`#${MAIN_ID}`} className="skip-link">
        {dict.route.skipToContent}
      </a>

      <Header source={network?.source ?? null} />

      <main
        id={MAIN_ID}
        className="mx-auto w-full max-w-2xl flex-1 px-4 py-8 sm:px-6"
      >
        <h1 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
          {dict.route.heading}
        </h1>
        <p className="mt-2 text-sm leading-relaxed text-text-secondary">
          {dict.route.intro}
        </p>

        {/* Форма выбора «откуда»/«куда». onSubmit — чтобы работал Enter. */}
        <form
          onSubmit={(event) => {
            event.preventDefault();
            handleSubmit();
          }}
          className="mt-6 rounded-2xl border border-[var(--panel-border)] bg-[var(--panel-bg)] p-4 shadow-[var(--shadow-card)] sm:p-5"
        >
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <StationSelect
              id="route-from"
              label={dict.route.fromLabel}
              placeholder={dict.route.fromPlaceholder}
              value={from}
              groups={groups}
              onChange={setFrom}
            />

            <button
              type="button"
              onClick={handleSwap}
              aria-label={dict.route.swap}
              title={dict.route.swap}
              className="flex h-11 w-11 shrink-0 items-center justify-center self-center rounded-xl border border-[var(--panel-border)] text-text-secondary transition-colors duration-150 ease-out hover:bg-[var(--control-hover)] hover:text-[var(--text-primary)] sm:mb-0 sm:self-end"
            >
              <svg
                aria-hidden="true"
                focusable="false"
                width={18}
                height={18}
                viewBox="0 0 18 18"
                fill="none"
                stroke="currentColor"
                strokeWidth={1.8}
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                {/* Вертикальный обмен: две встречные стрелки */}
                <path d="M6 3v12M6 3 3.5 5.5M6 3l2.5 2.5" />
                <path d="M12 15V3M12 15l2.5-2.5M12 15l-2.5-2.5" />
              </svg>
            </button>

            <StationSelect
              id="route-to"
              label={dict.route.toLabel}
              placeholder={dict.route.toPlaceholder}
              value={to}
              groups={groups}
              onChange={setTo}
            />
          </div>

          {/* Подсказки валидации: не только цветом — понятным текстом */}
          {sameStation && (
            <p role="alert" className="mt-3 text-sm font-semibold text-brand-red">
              {dict.route.sameStation}
            </p>
          )}

          <button
            type="submit"
            disabled={!canSubmit || status === "loading"}
            className="mt-4 flex h-11 w-full items-center justify-center rounded-xl bg-brand-red px-4 text-sm font-bold text-surface-light transition-opacity duration-150 ease-out hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-45"
          >
            {status === "loading" ? dict.route.building : dict.route.submit}
          </button>

          {!canSubmit && !sameStation && (
            <p className="mt-2 text-center text-xs text-text-secondary">
              {dict.route.selectBoth}
            </p>
          )}
        </form>

        {/* Область результата: живой регион для скринридеров */}
        <div aria-live="polite" className="mt-6">
          {status === "loading" && (
            <p role="status" className="text-text-secondary">
              {dict.route.building}
            </p>
          )}

          {status === "error" && (
            <p
              role="alert"
              className="rounded-xl border border-[var(--brand-red)] bg-[var(--control-hover)] px-4 py-3 text-sm font-semibold text-brand-red"
            >
              {dict.route.error}
            </p>
          )}

          {showNotFound && (
            <p
              role="status"
              className="rounded-xl border border-[var(--panel-border)] bg-[var(--control-hover)] px-4 py-3 text-sm font-semibold text-text-secondary"
            >
              {dict.route.notFound}
            </p>
          )}

          {showRoute && route && (
            <section aria-label={dict.route.heading} className="flex flex-col gap-5">
              {/* Заголовок маршрута: откуда → куда */}
              <h2 className="flex flex-wrap items-center gap-x-2 gap-y-1 text-lg font-bold sm:text-xl">
                <span>{pickName(route.stops[0]?.name, lang)}</span>
                <span aria-hidden="true" className="text-text-secondary">
                  →
                </span>
                <span>
                  {pickName(route.stops[route.stops.length - 1]?.name, lang)}
                </span>
              </h2>

              {/* Сводка: оценочное время, пересадки, число остановок */}
              <div className="grid grid-cols-3 gap-2">
                <SummaryTile
                  value={`${route.estimatedMinutes} ${dict.route.minutesSuffix}`}
                  label={dict.route.timeLabel}
                />
                <SummaryTile
                  value={
                    route.transfers === 0
                      ? dict.route.transfersNone
                      : String(route.transfers)
                  }
                  label={dict.route.transfersLabel}
                />
                <SummaryTile
                  value={String(route.stops.length)}
                  label={dict.route.stopsLabel}
                />
              </div>

              {/* Пометка оценочности времени — не только цветом, с иконкой */}
              <p className="flex items-start gap-2 text-xs text-text-secondary">
                <svg
                  aria-hidden="true"
                  focusable="false"
                  width={15}
                  height={15}
                  viewBox="0 0 16 16"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth={1.6}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  className="mt-0.5 shrink-0"
                >
                  <circle cx="8" cy="8" r="6.5" />
                  <path d="M8 7.2v3.6M8 5.2h.01" />
                </svg>
                <span>{dict.route.estimateNote}</span>
              </p>

              {/* Участки по линиям */}
              <section>
                <h3 className="text-[11px] font-bold uppercase tracking-wide text-text-secondary">
                  {dict.route.legsHeading}
                </h3>
                <ol className="mt-2 flex flex-col gap-2">
                  {route.legs.map((leg, index) => {
                    // Станция пересадки — конец участка (кроме последнего)
                    const transferCode = leg.stations[leg.stations.length - 1];
                    const transferName =
                      route.stops.find((s) => s.code === transferCode)?.name ??
                      null;
                    const showTransfer = index < route.legs.length - 1;
                    return (
                      <li key={`${leg.lineCode}-${index}`}>
                        <div className="flex items-center gap-2.5 rounded-xl border border-[var(--panel-border)] bg-[var(--panel-bg)] p-3">
                          <span
                            className="line-badge shrink-0"
                            style={{ background: leg.colorHex }}
                          >
                            {lineBadgeLabel(leg.lineCode, lang)}
                          </span>
                          <span className="min-w-0 flex-1">
                            <span className="block truncate font-semibold">
                              {pickName(leg.lineName, lang)}
                            </span>
                            <span className="block text-xs text-text-secondary">
                              {leg.stations.length} {dict.route.stopsCountSuffix}{" "}
                              · {leg.estimatedMinutes} {dict.route.minutesSuffix}
                            </span>
                          </span>
                        </div>
                        {showTransfer && transferName && (
                          <p className="flex items-center gap-1.5 py-1.5 pl-3 text-xs font-semibold text-text-secondary">
                            <TransferIcon />
                            <span>
                              {dict.route.transferAt}{" "}
                              {pickName(transferName, lang)}
                            </span>
                          </p>
                        )}
                      </li>
                    );
                  })}
                </ol>
              </section>

              {/* Остановки маршрута — вертикальный список с пометкой пересадок */}
              <section>
                <h3 className="text-[11px] font-bold uppercase tracking-wide text-text-secondary">
                  {dict.route.stopsHeading}
                </h3>
                <ol className="mt-2">
                  {route.stops.map((stop, index) => (
                    <li
                      key={`${stop.code}-${index}`}
                      className="flex items-center gap-2.5 py-1.5"
                    >
                      <span className="line-badge shrink-0">
                        {lineBadgeLabel(stop.lineCode, lang)}
                      </span>
                      <span className="min-w-0 flex-1 truncate text-sm font-semibold">
                        {pickName(stop.name, lang)}
                      </span>
                      {stop.transfer && (
                        <span className="flex shrink-0 items-center gap-1 rounded-full border border-[var(--panel-border)] px-2 py-0.5 text-[11px] font-semibold text-text-secondary">
                          <TransferIcon />
                          {dict.transferBadge}
                        </span>
                      )}
                    </li>
                  ))}
                </ol>
              </section>
            </section>
          )}
        </div>
      </main>
    </div>
  );
}
