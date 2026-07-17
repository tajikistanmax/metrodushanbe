"use client";

/**
 * Маршрутный поиск «откуда/куда» (GET /routes). Станции для выбора берём из
 * данных сети (loadNetworkData, API → офлайн-демо), маршрут строим через
 * loadRoute с офлайн-деградацией. Различаем три исхода построения:
 *  - недоступный backend (loadRoute → null) — локальный расчёт по данным сети;
 *  - валидный ответ found:false — «пути нет»;
 *  - found:true — участки по линиям, пересадки и ОЦЕНОЧНОЕ время.
 * Время явно помечено как оценочное (до реального расписания).
 *
 * Страница принимает `?from=&to=` — тем же адресом маршрут, выбранный двумя
 * нажатиями по карте на главной, открывается списками, и этой же ссылкой можно
 * поделиться. Параметры работают и при прямом заходе: коды сверяются с данными
 * сети, маршрут строится сам, неизвестный код просто отбрасывается.
 */

import { useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { MAIN_CONTENT_ID } from "@/lib/dom-ids";
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
import { Alert, Button, Card, Container, Select } from "@/shared/ui";
import { useReportDataSource } from "./DataSourceProvider";
import { useI18n } from "./I18nProvider";

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
  label,
  placeholder,
  value,
  groups,
  onChange,
}: {
  label: string;
  placeholder: string;
  value: string;
  groups: LineGroup[];
  onChange: (code: string) => void;
}) {
  const { lang } = useI18n();
  // Плоским списком станции не показать: в сети из нескольких десятков станций
  // без разбивки по линиям нужную не найти. Select принимает группы — они
  // становятся нативными optgroup.
  const options = useMemo(
    () => [
      { value: "", label: placeholder },
      ...groups.map((group) => ({
        label: `${lineBadgeLabel(group.line.properties.code, lang)} · ${pickName(
          group.line.properties.name,
          lang,
        )}`,
        options: group.stations.map((station) => ({
          value: station.properties.code,
          label: pickName(station.properties.name, lang),
        })),
      })),
    ],
    [groups, lang, placeholder],
  );

  return (
    <div className="flex-1">
      <Select
        label={label}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        options={options}
      />
    </div>
  );
}

/** Плитка сводки маршрута (значение + подпись). */
function SummaryTile({ value, label }: { value: string; label: string }) {
  return (
    <div className="rounded-control border border-[var(--border-subtle)] bg-[var(--surface-sunken)] px-3 py-2.5 text-center">
      <div className="text-title-s font-bold leading-tight text-[var(--text-primary)]">
        {value}
      </div>
      <div className="mt-0.5 text-caption font-semibold uppercase tracking-[0.08em] text-text-secondary">
        {label}
      </div>
    </div>
  );
}

export default function RoutePlannerClient() {
  const { lang, dict } = useI18n();
  const searchParams = useSearchParams();
  const [network, setNetwork] = useState<NetworkDataResult | null>(null);
  // Начальные значения из адреса: ссылка с карты должна открываться готовой
  const [rawFrom, setRawFrom] = useState(() => searchParams.get("from") ?? "");
  const [rawTo, setRawTo] = useState(() => searchParams.get("to") ?? "");
  /**
   * Пара, построение которой запрошено: нажатием кнопки или адресом при заходе.
   * Прямой заход по ссылке строит маршрут САМ — иначе поделиться результатом
   * нельзя, получатель ссылки увидел бы просто заполненную форму.
   */
  const [submitted, setSubmitted] = useState<{ from: string; to: string } | null>(
    () => {
      const f = searchParams.get("from") ?? "";
      const t = searchParams.get("to") ?? "";
      return f && t && f !== t ? { from: f, to: t } : null;
    },
  );
  // Результат хранится вместе с парой, к которой относится: «строим» и «ничего
  // не выбрано» выводятся из него, а не выставляются setState прямо в эффекте.
  const [routeState, setRouteState] = useState<{
    key: string;
    route: Route | null;
    status: "done" | "error";
  } | null>(null);
  // Ключ последнего запрошенного построения — устаревшие ответы отбрасываются
  const requestKeyRef = useRef<string | null>(null);

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

  /** Коды станций сети — ими сверяются параметры адреса. */
  const stationCodes = useMemo(
    () =>
      new Set(
        (network?.data.features ?? [])
          .filter(isStationFeature)
          .map((station) => station.properties.code),
      ),
    [network],
  );

  /**
   * Неизвестный код из адреса (опечатка, устаревшая ссылка) отбрасывается:
   * селект показывает плейсхолдер, а не «станцию-призрак». Пока данные сети не
   * приехали, сверять не с чем — код считаем годным.
   */
  const isKnown = (code: string) => stationCodes.size === 0 || stationCodes.has(code);
  const from = rawFrom && !isKnown(rawFrom) ? "" : rawFrom;
  const to = rawTo && !isKnown(rawTo) ? "" : rawTo;

  const sameStation = from !== "" && from === to;
  const canSubmit = from !== "" && to !== "" && !sameStation;

  /** Пара, которую действительно строим: запрошена и состоит из живых станций. */
  const request = useMemo(
    () =>
      submitted && canSubmit && submitted.from === from && submitted.to === to
        ? submitted
        : null,
    [canSubmit, from, submitted, to],
  );
  const requestKey = request ? `${request.from} ${request.to}` : null;

  // Производные состояния: запроса нет — «ничего не построено»; результат чужой
  // пары — «строим»; результат своей пары — исход построения.
  const status: Status =
    requestKey === null
      ? "idle"
      : routeState?.key === requestKey
        ? routeState.status
        : "loading";
  const route =
    requestKey !== null && routeState?.key === requestKey
      ? routeState.route
      : null;

  useEffect(() => {
    if (!request || !requestKey) {
      requestKeyRef.current = null;
      return;
    }
    requestKeyRef.current = requestKey;
    const { from: origin, to: destination } = request;
    loadRoute(origin, destination).then((res) => {
      if (requestKeyRef.current !== requestKey) {
        return; // устаревший ответ — игнорируем
      }
      if (res === null) {
        const offlineRoute = network
          ? buildOfflineRoute(network.data, origin, destination)
          : null;
        setRouteState(
          offlineRoute === null
            ? { key: requestKey, route: null, status: "error" }
            : { key: requestKey, route: offlineRoute, status: "done" },
        );
        return;
      }
      setRouteState({ key: requestKey, route: res, status: "done" });
    });
  }, [network, request, requestKey]);

  const handleSubmit = useCallback(() => {
    if (!canSubmit) {
      return;
    }
    setSubmitted({ from, to });
  }, [canSubmit, from, to]);

  // Адрес всегда отражает выбор — ссылкой можно поделиться. replaceState, а не
  // router.replace: перерисовывать дерево ради строки адреса незачем.
  useEffect(() => {
    const params = new URLSearchParams();
    if (from) {
      params.set("from", from);
    }
    if (to) {
      params.set("to", to);
    }
    const query = params.toString();
    window.history.replaceState(
      null,
      "",
      query ? `${window.location.pathname}?${query}` : window.location.pathname,
    );
  }, [from, to]);

  // Смена станции снимает запрос: показанный результат относился к другой паре
  const changeFrom = useCallback((value: string) => {
    setRawFrom(value);
    setSubmitted(null);
  }, []);

  const changeTo = useCallback((value: string) => {
    setRawTo(value);
    setSubmitted(null);
  }, []);

  const handleSwap = useCallback(() => {
    setRawFrom(to);
    setRawTo(from);
    setSubmitted(null);
  }, [from, to]);

  useReportDataSource(network?.source ?? null);

  const showRoute = status === "done" && route !== null && route.found;
  const showNotFound = status === "done" && route !== null && !route.found;

  return (
    <main id={MAIN_CONTENT_ID} className="flex-1 py-8 sm:py-12">
      <Container width="narrow">
        <h1 className="text-title-l font-bold sm:text-title-xl">
          {dict.route.heading}
        </h1>
        <p className="mt-3 max-w-[65ch] text-lead text-text-secondary">
          {dict.route.intro}
        </p>

        {/* Форма выбора «откуда»/«куда». onSubmit — чтобы работал Enter. */}
        <Card
          as="div"
          padding="lg"
          className="mt-6"
        >
        <form
          onSubmit={(event) => {
            event.preventDefault();
            handleSubmit();
          }}
        >
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <StationSelect
              label={dict.route.fromLabel}
              placeholder={dict.route.fromPlaceholder}
              value={from}
              groups={groups}
              onChange={changeFrom}
            />

            <button
              type="button"
              onClick={handleSwap}
              aria-label={dict.route.swap}
              title={dict.route.swap}
              className="flex h-11 w-11 shrink-0 items-center justify-center self-center rounded-control border border-[var(--border-strong)] text-text-secondary transition-colors duration-150 ease-out hover:bg-[var(--surface-hover)] hover:text-[var(--text-primary)] sm:mb-0 sm:self-end"
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
              label={dict.route.toLabel}
              placeholder={dict.route.toPlaceholder}
              value={to}
              groups={groups}
              onChange={changeTo}
            />
          </div>

          {/* Подсказки валидации: не только цветом — понятным текстом */}
          {sameStation && (
            <p role="alert" className="mt-3 text-small font-semibold text-brand-red">
              {dict.route.sameStation}
            </p>
          )}

          {/* Основное действие — navy (variant="primary"), а не красное:
              красный в системе означает опасное действие (Button variant
              "danger"), а построение маршрута ничего не разрушает. */}
          <Button
            type="submit"
            variant="primary"
            size="lg"
            block
            disabled={!canSubmit || status === "loading"}
            className="mt-4"
          >
            {status === "loading" ? dict.route.building : dict.route.submit}
          </Button>

          {!canSubmit && !sameStation && (
            <p className="mt-2 text-center text-caption text-text-secondary">
              {dict.route.selectBoth}
            </p>
          )}
        </form>
        </Card>

        {/* Область результата: живой регион для скринридеров */}
        <div aria-live="polite" className="mt-6">
          {status === "loading" && (
            <p role="status" className="text-body text-text-secondary">
              {dict.route.building}
            </p>
          )}

          {/* Недоступный backend и «пути нет» — РАЗНЫЕ исходы, поэтому разные
              тона и разные текстовые метки, а не один серый прямоугольник. */}
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
            <section aria-label={dict.route.heading} className="flex flex-col gap-5">
              {/* Заголовок маршрута: откуда → куда */}
              <h2 className="flex flex-wrap items-center gap-x-2 gap-y-1 text-title-s font-bold sm:text-title-m">
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
              <p className="flex items-start gap-2 text-small text-text-secondary">
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
                <h3 className="text-caption font-bold uppercase tracking-[0.08em] text-text-secondary">
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
                        <div className="flex items-center gap-2.5 rounded-control border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-3">
                          <span
                            className="line-badge shrink-0"
                            style={{ background: leg.colorHex }}
                          >
                            {lineBadgeLabel(leg.lineCode, lang)}
                          </span>
                          <span className="min-w-0 flex-1">
                            <span className="block truncate text-small font-semibold">
                              {pickName(leg.lineName, lang)}
                            </span>
                            <span className="block text-caption text-text-secondary">
                              {leg.stations.length} {dict.route.stopsCountSuffix}{" "}
                              · {leg.estimatedMinutes} {dict.route.minutesSuffix}
                            </span>
                          </span>
                        </div>
                        {showTransfer && transferName && (
                          <p className="flex items-center gap-1.5 py-1.5 pl-3 text-caption font-semibold text-text-secondary">
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
                <h3 className="text-caption font-bold uppercase tracking-[0.08em] text-text-secondary">
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
                      <span className="min-w-0 flex-1 truncate text-small font-semibold">
                        {pickName(stop.name, lang)}
                      </span>
                      {stop.transfer && (
                        <span className="flex shrink-0 items-center gap-1 rounded-chip border border-[var(--border-subtle)] px-2 py-0.5 text-caption font-semibold text-text-secondary">
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
      </Container>
    </main>
  );
}
