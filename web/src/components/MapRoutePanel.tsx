"use client";

/**
 * Панель режима «маршрут по карте»: два нажатия по станциям — и маршрут готов.
 *
 * ПОЧЕМУ ОТДЕЛЬНЫЙ РЕЖИМ. Клик по станции уже имел смысл — «посмотреть
 * станцию» (карточка + popup). Молча отобрать его нельзя, поэтому режим
 * маршрута включается явно кнопкой дока, а панель всё время говорит, что
 * произойдёт по следующему нажатию.
 *
 * ТРЕТЬЕ НАЖАТИЕ заменяет станцию НАЗНАЧЕНИЯ, а не начинает маршрут заново:
 * так каждое нажатие сразу даёт готовый маршрут (A→B, потом A→C), ничего не
 * пропадает, а сменить отправление можно «поменять местами» или «сбросить».
 *
 * Панель — текстовый дубль карты: состояния объявляются через aria-live, а
 * клавиатурные пути (список станций, планировщик /route) названы явно.
 */

import Link from "next/link";
import { lineBadgeLabel, pickName } from "@/lib/i18n";
import type { NetworkGeoJson, Route, StationFeature } from "@/lib/types";
import { isStationFeature } from "@/lib/types";
import { Alert } from "@/shared/ui";
import { useI18n } from "./I18nProvider";

/** Состояние построения — то же различение исходов, что и на /route. */
export type MapRouteStatus = "idle" | "loading" | "done" | "error";

type MapRoutePanelProps = {
  data: NetworkGeoJson | null;
  from: string | null;
  to: string | null;
  route: Route | null;
  status: MapRouteStatus;
  /** Пользователь нажал ту же станцию второй раз — маршрута из А в А нет. */
  sameStationNotice: boolean;
  onSwap: () => void;
  onReset: () => void;
  onClose: () => void;
};

function stationName(
  data: NetworkGeoJson | null,
  code: string | null,
): StationFeature | null {
  if (!data || !code) {
    return null;
  }
  return (
    data.features.find(
      (f): f is StationFeature => isStationFeature(f) && f.properties.code === code,
    ) ?? null
  );
}

/** Строка выбранной точки: буква + название либо «не выбрана». */
function EndpointRow({
  letter,
  label,
  name,
}: {
  letter: string;
  label: string;
  name: string | null;
}) {
  return (
    <div className="flex items-center gap-2">
      <span
        aria-hidden="true"
        className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full border-[2px] border-[var(--text-primary)] text-caption font-extrabold"
      >
        {letter}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-caption font-semibold uppercase tracking-[0.08em] text-text-secondary">
          {label}
        </span>
        <span
          className={`block truncate text-small font-semibold ${
            name ? "" : "text-text-secondary"
          }`}
        >
          {name}
        </span>
      </span>
    </div>
  );
}

export default function MapRoutePanel({
  data,
  from,
  to,
  route,
  status,
  sameStationNotice,
  onSwap,
  onReset,
  onClose,
}: MapRoutePanelProps) {
  const { lang, dict } = useI18n();
  const map = dict.route.map;

  const fromStation = stationName(data, from);
  const toStation = stationName(data, to);
  const fromName = fromStation
    ? pickName(fromStation.properties.name, lang)
    : null;
  const toName = toStation ? pickName(toStation.properties.name, lang) : null;

  const showRoute = status === "done" && route !== null && route.found;
  const showNotFound = status === "done" && route !== null && !route.found;

  /**
   * Одна фраза о том, что происходит и что нажать дальше. Именно она попадает
   * в aria-live — состояние «выбрана только первая станция» обязано звучать.
   */
  // Совпадение точек ловится и по факту (from === to), а не только по признаку
  // «нажал ту же станцию»: панель не должна молчать ни при каком раскладе.
  const sameStation = sameStationNotice || (from !== null && from === to);
  const hint = sameStation
    ? map.hintSame
    : !from
      ? map.hintIdle
      : !to
        ? `${dict.route.fromLabel}: ${fromName ?? from}. ${map.hintFrom}`
        : showRoute
          ? map.hintReplace
          : null;

  const plannerHref =
    from && to
      ? `/route?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`
      : "/route";

  return (
    <section
      aria-label={map.title}
      className="pointer-events-auto max-h-[46dvh] overflow-y-auto rounded-panel border border-[var(--border-subtle)] bg-[var(--surface-glass)] p-3 text-[var(--text-primary)] backdrop-blur-xl sm:max-h-[calc(100dvh-330px)] sm:p-4"
    >
      <div className="flex items-center gap-2">
        <h2 className="min-w-0 flex-1 truncate text-small font-bold">
          {map.title}
        </h2>
        <button
          type="button"
          onClick={onClose}
          aria-label={map.toggleOff}
          title={map.toggleOff}
          className="flex h-7 w-7 shrink-0 items-center justify-center rounded-control text-text-secondary transition-colors duration-150 ease-out hover:bg-[var(--surface-hover)] hover:text-[var(--text-primary)]"
        >
          <svg
            aria-hidden="true"
            focusable="false"
            width={14}
            height={14}
            viewBox="0 0 16 16"
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            strokeLinecap="round"
          >
            <path d="M4 4l8 8M12 4l-8 8" />
          </svg>
        </button>
      </div>

      <div className="mt-2.5 flex flex-col gap-2">
        <EndpointRow
          letter={map.fromShort}
          label={dict.route.fromLabel}
          name={fromName ?? map.notPicked}
        />
        <EndpointRow
          letter={map.toShort}
          label={dict.route.toLabel}
          name={toName ?? map.notPicked}
        />
      </div>

      <div className="mt-2.5 flex flex-wrap gap-1.5">
        <button
          type="button"
          onClick={onSwap}
          disabled={!from || !to}
          className="rounded-control border border-[var(--border-strong)] px-2.5 py-1.5 text-caption font-semibold transition-colors duration-150 ease-out hover:bg-[var(--surface-hover)] disabled:cursor-not-allowed disabled:opacity-50"
        >
          {dict.route.swap}
        </button>
        <button
          type="button"
          onClick={onReset}
          disabled={!from && !to}
          className="rounded-control border border-[var(--border-strong)] px-2.5 py-1.5 text-caption font-semibold transition-colors duration-150 ease-out hover:bg-[var(--surface-hover)] disabled:cursor-not-allowed disabled:opacity-50"
        >
          {map.reset}
        </button>
        <Link
          href={plannerHref}
          className="rounded-control border border-[var(--border-strong)] px-2.5 py-1.5 text-caption font-semibold transition-colors duration-150 ease-out hover:bg-[var(--surface-hover)]"
        >
          {map.openPlanner}
        </Link>
      </div>

      {/* Живой регион: состояние выбора и исход построения объявляются вслух */}
      <div aria-live="polite" className="mt-2.5 flex flex-col gap-2">
        {hint && (
          <p
            className={`text-caption ${
              sameStation
                ? "font-semibold text-brand-red"
                : "text-text-secondary"
            }`}
          >
            {hint}
          </p>
        )}

        {status === "loading" && (
          <p className="text-caption text-text-secondary">{dict.route.building}</p>
        )}

        {/* Недоступный backend и «пути нет» — разные исходы, разные тона */}
        {status === "error" && (
          <Alert tone="critical" label={dict.route.nav} live={false}>
            {dict.route.error}
          </Alert>
        )}
        {showNotFound && (
          <Alert tone="info" label={dict.route.nav} live={false}>
            {dict.route.notFound}
          </Alert>
        )}

        {showRoute && route && (
          <>
            <dl className="grid grid-cols-3 gap-1.5">
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
            </dl>

            {/* Оценочность времени остаётся явной и на карте, не только на /route */}
            <p className="text-caption text-text-secondary">
              {dict.route.estimateNote}
            </p>

            <div>
              <h3 className="text-caption font-bold uppercase tracking-[0.08em] text-text-secondary">
                {dict.route.legsHeading}
              </h3>
              <ol className="mt-1.5 flex flex-col gap-1">
                {route.legs.map((leg, index) => (
                  <li
                    key={`${leg.lineCode}-${index}`}
                    className="flex items-center gap-2 rounded-control border border-[var(--border-subtle)] bg-[var(--surface-raised)] px-2 py-1.5"
                  >
                    <span
                      className="line-badge shrink-0"
                      style={{ background: leg.colorHex }}
                    >
                      {lineBadgeLabel(leg.lineCode, lang)}
                    </span>
                    <span className="min-w-0 flex-1 truncate text-caption font-semibold">
                      {pickName(leg.lineName, lang)}
                    </span>
                    <span className="shrink-0 text-caption text-text-secondary">
                      {leg.estimatedMinutes} {dict.route.minutesSuffix}
                    </span>
                  </li>
                ))}
              </ol>
            </div>
          </>
        )}
      </div>

      {/* A11Y: карта мышью — не единственный путь, и об этом сказано словами */}
      <p className="mt-2.5 border-t border-[var(--border-subtle)] pt-2 text-caption text-text-secondary">
        {map.keyboardNote}
      </p>
    </section>
  );
}

/** Плитка сводки маршрута (значение + подпись). */
function SummaryTile({ value, label }: { value: string; label: string }) {
  return (
    <div className="rounded-control border border-[var(--border-subtle)] bg-[var(--surface-sunken)] px-1.5 py-1.5 text-center">
      <dd className="truncate text-small font-bold leading-tight">{value}</dd>
      <dt className="mt-0.5 truncate text-caption font-semibold uppercase tracking-[0.06em] text-text-secondary">
        {label}
      </dt>
    </div>
  );
}
