"use client";

/**
 * Плавающая панель станций (list-mode, требование A11Y-06): карта никогда
 * не является единственным способом получить информацию о сети.
 * Desktop: карточка слева поверх карты; mobile (<768px): bottom-sheet 45vh.
 * Линии — вертикальные метро-диаграммы: цветная полоса, станции — кружки
 * на ней (пересадка — двойное кольцо navy), клик = flyTo + popup на карте.
 */

import { useEffect, useMemo, useState } from "react";
import { lineBadgeLabel, pickName, type Dict, type Lang } from "@/lib/i18n";
import { loadStationArrivals } from "@/lib/schedule-data";
import { loadStationDetail } from "@/lib/station-detail-data";
import {
  isLineFeature,
  isStationFeature,
  type LineFeature,
  type NetworkGeoJson,
  type StationArrivalsResult,
  type StationDetail,
  type StationFeature,
} from "@/lib/types";
import { STATION_LIST_ID } from "@/lib/dom-ids";
import SearchBox from "./SearchBox";
import StationArrivals from "./StationArrivals";
import { useI18n } from "./I18nProvider";

type StationPanelProps = {
  data: NetworkGeoJson | null;
  onSelect: (code: string) => void;
  /** Код выбранной станции — для aria-current и подсветки ряда. */
  selectedCode: string | null;
  /**
   * Точки маршрута, выбранные на карте. Панель — list-mode карты (A11Y-06):
   * если карта что-то выбрала, список обязан это показывать.
   */
  routeFrom?: string | null;
  routeTo?: string | null;
  /**
   * Клавиатурный путь к режиму маршрута: клик по карте мышью с клавиатуры
   * недоступен, поэтому «откуда»/«куда» назначаются кнопками в карточке
   * станции. Не переданы — кнопок нет (панель используется вне главной).
   */
  onSetFrom?: (code: string) => void;
  onSetTo?: (code: string) => void;
};

type LineGroup = {
  line: LineFeature;
  stations: StationFeature[];
};

/** Квадрат расстояния между двумя точками [lng, lat] (для сортировки). */
function distanceSq(a: [number, number], b: [number, number]): number {
  const dx = a[0] - b[0];
  const dy = a[1] - b[1];
  return dx * dx + dy * dy;
}

/** Индекс ближайшей вершины линии — станции демо-данных лежат на вершинах. */
function nearestVertexIndex(
  station: StationFeature,
  line: LineFeature,
): number {
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

/** Группировка станций по линиям с сортировкой вдоль трассы. */
function groupByLine(data: NetworkGeoJson): LineGroup[] {
  const lines = data.features
    .filter(isLineFeature)
    .sort((a, b) => a.properties.sort_order - b.properties.sort_order);
  const stations = data.features.filter(isStationFeature);

  return lines.map((line) => ({
    line,
    stations: stations
      .filter((s) => s.properties.lines.includes(line.properties.code))
      .sort((a, b) => nearestVertexIndex(a, line) - nearestVertexIndex(b, line)),
  }));
}

/** Совпадение по названию на любом из трёх языков (без учёта регистра). */
function matchesQuery(station: StationFeature, query: string): boolean {
  const name = station.properties.name;
  return [name.tg, name.ru, name.en].some((value) =>
    value?.toLowerCase().includes(query),
  );
}

/** Кружок станции на вертикальной полосе линии. */
function StationDot({
  transfer,
  lineColor,
}: {
  transfer: boolean;
  lineColor: string;
}) {
  if (transfer) {
    // Двойное кольцо navy (в тёмной теме — светлое, --diagram-ring)
    return (
      <span
        aria-hidden="true"
        className="absolute left-2.5 top-1/2 flex h-5 w-5 -translate-y-1/2 items-center justify-center rounded-full border-[2.5px] border-[var(--diagram-ring)] bg-[var(--station-fill)]"
      >
        <span className="h-2 w-2 rounded-full border-2 border-[var(--diagram-ring)]" />
      </span>
    );
  }
  return (
    <span
      aria-hidden="true"
      className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 rounded-full border-[3px] bg-[var(--station-fill)]"
      style={{ borderColor: lineColor }}
    />
  );
}

/**
 * Метка точки маршрута в списке («А»/«Б»). Буква + скрытая подпись: выбор
 * на карте читается в списке и без цвета (SC 1.4.1).
 */
function RouteMark({ letter, label }: { letter: string; label: string }) {
  return (
    <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-2 border-[var(--text-primary)] text-caption font-extrabold leading-none">
      <span aria-hidden="true">{letter}</span>
      <span className="sr-only">{label}</span>
    </span>
  );
}

function LineDiagram({
  group,
  lang,
  selectedCode,
  transferLabel,
  routeFrom,
  routeTo,
  routeMarks,
  onSelect,
}: {
  group: LineGroup;
  lang: Lang;
  selectedCode: string | null;
  transferLabel: string;
  routeFrom: string | null;
  routeTo: string | null;
  routeMarks: { fromShort: string; toShort: string; fromLabel: string; toLabel: string };
  onSelect: (code: string) => void;
}) {
  const { line, stations } = group;
  const color = line.properties.color_hex;

  return (
    <div className="mb-4 last:mb-1">
      <h3 className="mb-1 flex items-center gap-2 px-2 text-small font-bold">
        <span className="line-badge" style={{ background: color }}>
          {lineBadgeLabel(line.properties.code, lang)}
        </span>
        <span className="min-w-0 truncate">
          {pickName(line.properties.name, lang)}
        </span>
      </h3>

      {/* Вертикальная метро-диаграмма: полоса линии + кружки станций */}
      <div className="relative">
        <span
          aria-hidden="true"
          className="absolute bottom-4 top-4 left-[17px] w-1.5 rounded-full"
          style={{ background: color }}
        />
        <ul>
          {stations.map((station) => {
            const code = station.properties.code;
            const selected = code === selectedCode;
            return (
              <li key={code}>
                <button
                  type="button"
                  aria-current={selected ? "true" : undefined}
                  onClick={() => onSelect(code)}
                  className={`relative flex w-full items-center gap-2 rounded-control py-2 pl-10 pr-2 text-left text-small font-semibold transition-colors duration-150 ease-out hover:bg-[var(--surface-hover)] ${
                    selected ? "bg-[var(--surface-hover)]" : ""
                  }`}
                >
                  <StationDot
                    transfer={station.properties.is_transfer}
                    lineColor={color}
                  />
                  <span className="min-w-0 flex-1 truncate">
                    {pickName(station.properties.name, lang)}
                  </span>
                  {code === routeFrom && (
                    <RouteMark
                      letter={routeMarks.fromShort}
                      label={routeMarks.fromLabel}
                    />
                  )}
                  {code === routeTo && (
                    <RouteMark
                      letter={routeMarks.toShort}
                      label={routeMarks.toLabel}
                    />
                  )}
                  {station.properties.is_transfer && (
                    <span className="shrink-0 rounded-chip border border-[var(--border-subtle)] px-2 py-0.5 text-caption font-semibold text-text-secondary">
                      {transferLabel}
                    </span>
                  )}
                </button>
              </li>
            );
          })}
        </ul>
      </div>
    </div>
  );
}

/**
 * Иконка признака доступности выхода. Смысл передаётся ИКОНКОЙ + текстом
 * рядом (не только цветом) — требование WCAG 2.2 (SC 1.4.1 «Использование
 * цвета»). aria-hidden: значение дублируется видимой подписью.
 */
function AccessIcon({ accessible }: { accessible: boolean }) {
  const color = accessible ? "var(--brand-green)" : "var(--text-secondary)";
  return (
    <span
      aria-hidden="true"
      className="flex h-4 w-4 shrink-0 items-center justify-center"
      style={{ color }}
    >
      <svg
        focusable="false"
        width={16}
        height={16}
        viewBox="0 0 16 16"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        {accessible ? (
          // Галочка — выход доступен
          <path d="M3 8.5 6.5 12 13 4.5" />
        ) : (
          // Перечёркнутый круг — выход недоступен
          <>
            <circle cx="8" cy="8" r="5.5" />
            <path d="M4.5 4.5 11.5 11.5" />
          </>
        )}
      </svg>
    </span>
  );
}

/** Классы текстового бейджа статуса объекта доступности (цвет + текст). */
function featureStatusClass(status: StationDetail["accessibilityFeatures"][number]["status"]): string {
  switch (status) {
    case "available":
      return "border-[var(--brand-green)] text-[var(--brand-green)]";
    case "out_of_service":
      // Недоступность выделяем текстом + цветом (не только цветом)
      return "border-[var(--brand-red)] text-[var(--brand-red)] font-bold";
    case "planned":
    default:
      return "border-[var(--border-subtle)] text-text-secondary";
  }
}

/**
 * Детальная карточка выбранной станции: базовая инфо (имя, статус, линии)
 * всегда из данных сети, а выходы и объекты доступности — из API. При
 * недоступном API показываем базовую инфо + «детали недоступны».
 */
function StationDetailCard({
  station,
  detail,
  loading,
  arrivals,
  arrivalsLoading,
  lang,
  dict,
  code,
  routeFrom,
  routeTo,
  onSetFrom,
  onSetTo,
}: {
  station: StationFeature | null;
  detail: StationDetail | null;
  loading: boolean;
  arrivals: StationArrivalsResult[];
  arrivalsLoading: boolean;
  lang: Lang;
  dict: Dict;
  code: string;
  routeFrom: string | null;
  routeTo: string | null;
  onSetFrom?: (code: string) => void;
  onSetTo?: (code: string) => void;
}) {
  const name = detail
    ? pickName(detail.name, lang)
    : station
      ? pickName(station.properties.name, lang)
      : "";
  const status = detail?.status ?? station?.properties.status ?? null;
  const lines = detail?.lines ?? station?.properties.lines ?? [];

  return (
    <div className="mb-3 rounded-control border border-[var(--border-subtle)] bg-[var(--surface-sunken)] p-3">
      <h3 className="text-small font-bold">{name}</h3>

      <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
        {status && (
          <span className="rounded-chip border border-[var(--border-subtle)] px-2 py-0.5 text-caption font-semibold text-text-secondary">
            {dict.status[status]}
          </span>
        )}
        {lines.map((code) => (
          <span key={code} className="line-badge">
            {lineBadgeLabel(code, lang)}
          </span>
        ))}
      </div>

      {/* Клавиатурный путь к маршруту: то же, что два нажатия по карте, но
          доступное с клавиатуры. aria-pressed — станция уже назначена точкой. */}
      {(onSetFrom || onSetTo) && (
        <div className="mt-2.5 flex gap-1.5">
          {onSetFrom && (
            <button
              type="button"
              onClick={() => onSetFrom(code)}
              aria-pressed={routeFrom === code}
              className={`flex-1 rounded-control border px-2 py-1.5 text-caption font-semibold transition-colors duration-150 ease-out hover:bg-[var(--surface-hover)] ${
                routeFrom === code
                  ? "border-[var(--text-primary)] bg-[var(--surface-hover)]"
                  : "border-[var(--border-strong)]"
              }`}
            >
              {dict.route.map.fromShort} · {dict.route.map.setFrom}
            </button>
          )}
          {onSetTo && (
            <button
              type="button"
              onClick={() => onSetTo(code)}
              aria-pressed={routeTo === code}
              className={`flex-1 rounded-control border px-2 py-1.5 text-caption font-semibold transition-colors duration-150 ease-out hover:bg-[var(--surface-hover)] ${
                routeTo === code
                  ? "border-[var(--text-primary)] bg-[var(--surface-hover)]"
                  : "border-[var(--border-strong)]"
              }`}
            >
              {dict.route.map.toShort} · {dict.route.map.setTo}
            </button>
          )}
        </div>
      )}

      <StationArrivals
        results={arrivals}
        loading={arrivalsLoading}
        lang={lang}
        dict={dict}
      />

      {loading && (
        <p role="status" className="mt-2.5 text-caption text-text-secondary">
          {dict.detailsLoading}
        </p>
      )}

      {!loading && !detail && (
        <p role="status" className="mt-2.5 text-caption text-text-secondary">
          {dict.detailsUnavailable}
        </p>
      )}

      {!loading && detail && (
        <>
          {/* Выходы */}
          <section className="mt-3">
            <h4 className="text-caption font-bold uppercase tracking-[0.08em] text-text-secondary">
              {dict.exitsHeading}
            </h4>
            {detail.exits.length === 0 ? (
              <p className="mt-1 text-caption text-text-secondary">
                {dict.detailsNoExits}
              </p>
            ) : (
              <ul className="mt-1.5 space-y-1.5">
                {detail.exits.map((exit) => (
                  <li
                    key={exit.code}
                    className="flex items-center gap-2 text-small"
                  >
                    <AccessIcon accessible={exit.isAccessible} />
                    {/* font-medium снят: вес 500 не загружен (@fontsource/montserrat
                        даёт 400/600/700/800) — браузер его синтезировал */}
                    <span className="min-w-0 flex-1 truncate">
                      {pickName(exit.name, lang)}
                    </span>
                    <span
                      className={`shrink-0 text-caption font-semibold ${
                        exit.isAccessible
                          ? "text-[var(--brand-green)]"
                          : "text-text-secondary"
                      }`}
                    >
                      {exit.isAccessible
                        ? dict.exitAccessible
                        : dict.exitNotAccessible}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </section>

          {/* Объекты доступности */}
          <section className="mt-3">
            <h4 className="text-caption font-bold uppercase tracking-[0.08em] text-text-secondary">
              {dict.accessibilityFeaturesHeading}
            </h4>
            {detail.accessibilityFeatures.length === 0 ? (
              <p className="mt-1 text-caption text-text-secondary">
                {dict.detailsNoFeatures}
              </p>
            ) : (
              <ul className="mt-1.5 space-y-1.5">
                {detail.accessibilityFeatures.map((feature, index) => {
                  // Подпись типа из словаря; для неизвестного backend-кода
                  // (напр. accessible_toilet) — локализованное описание.
                  const known =
                    dict.accessibility[
                      feature.type as keyof typeof dict.accessibility
                    ];
                  const description = pickName(feature.description, lang);
                  const primary = known ?? description;
                  const secondary = known ? description : null;
                  return (
                  <li
                    key={`${feature.type}-${index}`}
                    className="flex items-start gap-2 text-small"
                  >
                    <span className="min-w-0 flex-1">
                      <span className="font-semibold">{primary}</span>
                      {secondary && (
                        <span className="block text-caption text-text-secondary">
                          {secondary}
                        </span>
                      )}
                    </span>
                    <span
                      className={`mt-0.5 shrink-0 rounded-chip border px-2 py-0.5 text-caption ${featureStatusClass(
                        feature.status,
                      )}`}
                    >
                      {dict.featureStatus[feature.status]}
                    </span>
                  </li>
                  );
                })}
              </ul>
            )}
          </section>
        </>
      )}
    </div>
  );
}

export default function StationPanel({
  data,
  onSelect,
  selectedCode,
  routeFrom = null,
  routeTo = null,
  onSetFrom,
  onSetTo,
}: StationPanelProps) {
  const { lang, dict } = useI18n();
  const [query, setQuery] = useState("");
  const [expanded, setExpanded] = useState(true);
  // Детали выбранной станции подгружаются ОТДЕЛЬНО и не блокируют панель:
  // ошибка/офлайн → detail = null (loadStationDetail никогда не бросает).
  // Храним результат вместе с кодом станции, к которой он относится, чтобы
  // «загрузка» выводилась как производное состояние (без setState в эффекте).
  const [detailState, setDetailState] = useState<{
    code: string;
    detail: StationDetail | null;
  } | null>(null);
  const [arrivalsState, setArrivalsState] = useState<{
    key: string;
    results: StationArrivalsResult[];
  } | null>(null);

  const groups = useMemo(() => (data ? groupByLine(data) : []), [data]);

  // Есть ли уже загруженный результат именно для выбранной станции.
  const detailReady = detailState?.code === selectedCode;
  const detail = detailReady ? (detailState?.detail ?? null) : null;
  const detailLoading = Boolean(selectedCode) && !detailReady;

  /** Выбранная станция из данных сети — источник базовой инфо для карточки. */
  const selectedStation = useMemo(() => {
    if (!selectedCode || !data) {
      return null;
    }
    return (
      data.features.find(
        (f): f is StationFeature =>
          isStationFeature(f) && f.properties.code === selectedCode,
      ) ?? null
    );
  }, [data, selectedCode]);

  /** Линии выбранной станции: API-деталь при наличии, иначе GeoJSON сети. */
  const selectedLineCodes = useMemo(
    () =>
      Array.from(
        new Set(detail?.lines ?? selectedStation?.properties.lines ?? []),
      ).sort(),
    [detail, selectedStation],
  );
  const arrivalsKey = selectedCode
    ? `${selectedCode}:${selectedLineCodes.join(",")}`
    : "";
  const arrivalsReady = arrivalsState?.key === arrivalsKey;
  const arrivals = arrivalsReady ? arrivalsState.results : [];
  const arrivalsLoading =
    Boolean(selectedCode) && selectedLineCodes.length > 0 && !arrivalsReady;

  // Загрузка деталей при смене выбранной станции. Гонки гасим флагом cancelled.
  // setState вызываем только в async-колбэке (не синхронно в теле эффекта).
  useEffect(() => {
    if (!selectedCode) {
      return;
    }
    let cancelled = false;
    loadStationDetail(selectedCode).then((res) => {
      if (!cancelled) {
        setDetailState({ code: selectedCode, detail: res });
      }
    });
    return () => {
      cancelled = true;
    };
  }, [selectedCode]);

  // Расписание линий станции загружаем параллельно. Функция сама выполняет
  // демо-fallback, поэтому UI получает результат даже без запущенного backend.
  useEffect(() => {
    if (!selectedCode || selectedLineCodes.length === 0) {
      return;
    }
    let cancelled = false;
    loadStationArrivals(selectedCode, selectedLineCodes).then((results) => {
      if (!cancelled) {
        setArrivalsState({ key: arrivalsKey, results });
      }
    });
    return () => {
      cancelled = true;
    };
  }, [arrivalsKey, selectedCode, selectedLineCodes]);

  const normalizedQuery = query.trim().toLowerCase();
  const visibleGroups = useMemo(() => {
    if (!normalizedQuery) {
      return groups;
    }
    return groups
      .map((group) => ({
        ...group,
        stations: group.stations.filter((s) =>
          matchesQuery(s, normalizedQuery),
        ),
      }))
      .filter((group) => group.stations.length > 0);
  }, [groups, normalizedQuery]);

  return (
    <section
      id={STATION_LIST_ID}
      aria-label={dict.stationsHeading}
      className={`absolute inset-x-0 bottom-0 z-10 flex flex-col overflow-hidden rounded-t-panel border border-[var(--border-subtle)] bg-[var(--surface-glass)] text-[var(--text-primary)] backdrop-blur-md md:inset-x-auto md:bottom-auto md:left-4 md:top-4 md:w-[360px] md:rounded-panel ${
        expanded
          ? "h-[45dvh] md:h-auto md:max-h-[calc(100dvh-160px)]"
          : "h-auto"
      }`}
    >
      {/* Свайп-ручка (визуальная) — только mobile bottom-sheet */}
      <div aria-hidden="true" className="flex justify-center pt-2 md:hidden">
        <span className="h-1 w-10 rounded-full bg-[var(--text-secondary)] opacity-40" />
      </div>

      <div className="flex shrink-0 items-center gap-2 px-4 pb-2 pt-1.5 md:pt-3">
        <h2 className="text-body font-bold">{dict.stationsHeading}</h2>
        <button
          type="button"
          aria-expanded={expanded}
          aria-controls="station-panel-body"
          aria-label={expanded ? dict.panelCollapse : dict.panelExpand}
          title={expanded ? dict.panelCollapse : dict.panelExpand}
          onClick={() => setExpanded((v) => !v)}
          className="ml-auto flex h-8 w-8 items-center justify-center rounded-control transition-colors duration-150 ease-out hover:bg-[var(--surface-hover)]"
        >
          <svg
            aria-hidden="true"
            focusable="false"
            width={16}
            height={16}
            viewBox="0 0 16 16"
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            strokeLinecap="round"
            strokeLinejoin="round"
            className={`transition-transform duration-200 ease-out ${
              expanded ? "" : "rotate-180"
            }`}
          >
            {/* chevron: вниз (свернуть) / вверх (развернуть) */}
            <path d="M4 6.5 8 10.5 12 6.5" />
          </svg>
        </button>
      </div>

      {expanded && (
        <div
          id="station-panel-body"
          className="flex min-h-0 flex-1 flex-col gap-2.5 px-3 pb-3"
        >
          <SearchBox value={query} onChange={setQuery} />

          <div className="min-h-0 flex-1 overflow-y-auto pr-1">
            {selectedCode && (selectedStation || detail || detailLoading) && (
              <StationDetailCard
                station={selectedStation}
                detail={detail}
                loading={detailLoading}
                arrivals={arrivals}
                arrivalsLoading={arrivalsLoading}
                lang={lang}
                dict={dict}
                code={selectedCode}
                routeFrom={routeFrom}
                routeTo={routeTo}
                onSetFrom={onSetFrom}
                onSetTo={onSetTo}
              />
            )}
            {groups.length === 0 ? (
              <p className="px-2 py-3 text-small text-text-secondary">
                {dict.loading}
              </p>
            ) : visibleGroups.length === 0 ? (
              <p role="status" className="px-2 py-3 text-small text-text-secondary">
                {dict.searchNoResults}
              </p>
            ) : (
              visibleGroups.map((group) => (
                <LineDiagram
                  key={group.line.properties.code}
                  group={group}
                  lang={lang}
                  selectedCode={selectedCode}
                  transferLabel={dict.transferBadge}
                  routeFrom={routeFrom}
                  routeTo={routeTo}
                  routeMarks={{
                    fromShort: dict.route.map.fromShort,
                    toShort: dict.route.map.toShort,
                    fromLabel: dict.route.fromLabel,
                    toLabel: dict.route.toLabel,
                  }}
                  onSelect={onSelect}
                />
              ))
            )}
          </div>
        </div>
      )}
    </section>
  );
}
