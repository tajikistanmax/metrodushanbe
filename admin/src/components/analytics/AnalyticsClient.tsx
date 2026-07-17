"use client";

/**
 * Аналитика сети: все показатели считаются из живых данных API (линии,
 * станции, уведомления, новости, геометрия сети) — без выдуманных чисел.
 * Графики — собственные SVG-примитивы (донат, полосы, колонки): внешние
 * chart-библиотеки не используются (офлайн-принцип, dev-conventions.md §8).
 */

import { useMemo } from "react";
import { lineBadgeLabel, pickName } from "@/lib/i18n";
import type {
  AccessibilityFeature,
  Alert,
  Line,
  NetworkGeoJson,
  News,
  Station,
} from "@/lib/types";
import LineBadge from "../LineBadge";
import { useI18n } from "../I18nProvider";
import { Card } from "@/shared/ui";

type Props = {
  lines: Line[] | null;
  stations: Station[] | null;
  alerts: Alert[] | null;
  news: News[] | null;
  network: NetworkGeoJson | null;
};

/** Длина ломаной по гаверсинусу, км. */
function lineLengthKm(coords: [number, number][]): number {
  const R = 6371;
  let total = 0;
  for (let i = 1; i < coords.length; i += 1) {
    const [lon1, lat1] = coords[i - 1];
    const [lon2, lat2] = coords[i];
    const dLat = ((lat2 - lat1) * Math.PI) / 180;
    const dLon = ((lon2 - lon1) * Math.PI) / 180;
    const a =
      Math.sin(dLat / 2) ** 2 +
      Math.cos((lat1 * Math.PI) / 180) *
        Math.cos((lat2 * Math.PI) / 180) *
        Math.sin(dLon / 2) ** 2;
    total += 2 * R * Math.asin(Math.sqrt(a));
  }
  return total;
}

/** Сегментированный донат: доли по цветным дугам, в центре — итог. */
function Donut({
  segments,
  centerValue,
  centerLabel,
}: {
  segments: { value: number; color: string; label: string }[];
  centerValue: string;
  centerLabel: string;
}) {
  const total = segments.reduce((s, x) => s + x.value, 0);
  const R = 52;
  const C = 2 * Math.PI * R;
  let offset = 0;

  return (
    <svg viewBox="0 0 140 140" className="h-36 w-36 shrink-0" role="img" aria-label={centerLabel}>
      <circle cx="70" cy="70" r={R} fill="none" stroke="var(--surface-chip)" strokeWidth="16" />
      {total > 0 &&
        segments.map((seg, i) => {
          const frac = seg.value / total;
          const dash = frac * C;
          const el = (
            <circle
              key={i}
              cx="70"
              cy="70"
              r={R}
              fill="none"
              stroke={seg.color}
              strokeWidth="16"
              strokeDasharray={`${dash} ${C - dash}`}
              strokeDashoffset={-offset}
              transform="rotate(-90 70 70)"
            >
              <title>{`${seg.label}: ${seg.value}`}</title>
            </circle>
          );
          offset += dash;
          return el;
        })}
      <text
        x="70"
        y="66"
        textAnchor="middle"
        fontSize="26"
        fontWeight="800"
        fill="var(--text-primary)"
      >
        {centerValue}
      </text>
      <text
        x="70"
        y="84"
        textAnchor="middle"
        fontSize="10"
        fontWeight="600"
        fill="var(--text-secondary)"
      >
        {centerLabel}
      </text>
    </svg>
  );
}

/** Горизонтальная полоса с подписью и значением. */
function HBar({
  label,
  value,
  max,
  color,
  valueText,
}: {
  label: React.ReactNode;
  value: number;
  max: number;
  color: string;
  valueText?: string;
}) {
  const pct = max > 0 ? Math.max((value / max) * 100, value > 0 ? 4 : 0) : 0;
  return (
    <li className="flex items-center gap-3">
      <span className="w-40 shrink-0 truncate text-xs font-semibold">{label}</span>
      <span className="relative h-2.5 min-w-0 flex-1 overflow-hidden rounded-full bg-[var(--surface-chip)]">
        <span
          className="absolute inset-y-0 left-0 rounded-full"
          style={{ width: `${pct}%`, background: color }}
        />
      </span>
      <span className="data-text w-14 shrink-0 text-right text-xs font-bold">
        {valueText ?? value}
      </span>
    </li>
  );
}

const ACCESSIBILITY_KEYS: AccessibilityFeature[] = [
  "elevator",
  "escalator",
  "ramp",
  "tactile",
  "audio_assist",
];

const SEVERITY_COLORS = {
  critical: "var(--brand-red)",
  warning: "var(--warning)",
  info: "var(--info)",
} as const;

export default function AnalyticsClient({ lines, stations, alerts, news, network }: Props) {
  const { lang, dict } = useI18n();
  const t = dict.analytics;

  // Протяжённость линий из геометрии сети
  const lengths = useMemo(() => {
    if (!network) {
      return new Map<string, number>();
    }
    const m = new Map<string, number>();
    for (const f of network.features) {
      if (f.properties.feature_type === "line" && f.geometry.type === "LineString") {
        m.set(f.properties.code, lineLengthKm(f.geometry.coordinates));
      }
    }
    return m;
  }, [network]);

  const totalKm = Array.from(lengths.values()).reduce((s, x) => s + x, 0);
  const maxKm = Math.max(...Array.from(lengths.values()), 0);

  // Станции по линиям
  const stationsPerLine = useMemo(() => {
    if (!lines || !stations) {
      return [];
    }
    return lines.map((l) => ({
      line: l,
      count: stations.filter((s) => s.lines.includes(l.code)).length,
    }));
  }, [lines, stations]);

  // Доступность
  const accessibilityCounts = useMemo(() => {
    if (!stations) {
      return [];
    }
    return ACCESSIBILITY_KEYS.map((k) => ({
      key: k,
      count: stations.filter((s) => s.accessibility.includes(k)).length,
    }));
  }, [stations]);

  // Статусы станций
  const statusCounts = useMemo(() => {
    if (!stations) {
      return [];
    }
    const m = new Map<string, number>();
    for (const s of stations) {
      m.set(s.status, (m.get(s.status) ?? 0) + 1);
    }
    return Array.from(m.entries()).sort((a, b) => b[1] - a[1]);
  }, [stations]);

  // Уведомления по уровню
  const severityCounts = useMemo(() => {
    const base = { critical: 0, warning: 0, info: 0 };
    for (const a of alerts ?? []) {
      base[a.severity] += 1;
    }
    return base;
  }, [alerts]);

  const stationCount = stations?.length ?? 0;
  const alertCount = alerts?.length ?? 0;

  if (!lines && !stations) {
    return (
      <p className="rounded-panel border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-8 text-center text-small text-text-secondary">
        {dict.loadError}. {dict.loadErrorHint}
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-5">
      <div>
        <h1 className="text-title-l font-bold">{t.title}</h1>
        <p className="mt-1 text-body text-text-secondary">{t.lead}</p>
      </div>

      <div className="grid gap-5 lg:grid-cols-2">
        {/* Станции по линиям. Card рендерит <h2>: прежние <h3> под <h1>
            пропускали уровень (SC 1.3.1). */}
        <Card heading={t.stationsPerLine} padding="lg" aria-label={t.stationsPerLine}>
          <div className="flex flex-wrap items-center gap-5">
            <Donut
              segments={stationsPerLine.map(({ line, count }) => ({
                value: count,
                color: line.colorHex,
                label: pickName(line.name, lang),
              }))}
              centerValue={String(stationCount)}
              centerLabel={t.totalStations}
            />
            <ul className="flex min-w-0 flex-1 flex-col gap-2.5">
              {stationsPerLine.map(({ line, count }) => (
                <HBar
                  key={line.code}
                  label={
                    <span className="flex items-center gap-2">
                      <LineBadge code={line.code} colorHex={line.colorHex} />
                      <span className="truncate">{pickName(line.name, lang)}</span>
                    </span>
                  }
                  value={count}
                  max={Math.max(...stationsPerLine.map((x) => x.count), 1)}
                  color={line.colorHex}
                />
              ))}
            </ul>
          </div>
        </Card>

        {/* Протяжённость линий */}
        <Card
          heading={t.lineLengths}
          description={
            <>
              {t.totalLength}:{" "}
              <span className="data-text font-bold text-[var(--text-primary)]">
                {totalKm.toFixed(1)} {t.km}
              </span>
            </>
          }
          padding="lg"
          aria-label={t.lineLengths}
        >
          <ul className="flex flex-col gap-3">
            {(lines ?? []).map((l) => {
              const km = lengths.get(l.code) ?? 0;
              return (
                <HBar
                  key={l.code}
                  label={`${lineBadgeLabel(l.code, lang)} · ${pickName(l.name, lang)}`}
                  value={km}
                  max={maxKm}
                  color={l.colorHex}
                  valueText={`${km.toFixed(1)} ${t.km}`}
                />
              );
            })}
          </ul>
          <p className="mt-4 text-caption leading-relaxed text-text-secondary">
            {t.lengthFootnote}
          </p>
        </Card>

        {/* Доступность станций */}
        <Card heading={t.accessibilityTitle} padding="lg" aria-label={t.accessibilityTitle}>
          <ul className="flex flex-col gap-3">
            {accessibilityCounts.map(({ key, count }) => (
              <HBar
                key={key}
                label={dict.accessibility[key]}
                value={count}
                max={stationCount}
                color="var(--brand-green)"
                valueText={`${count}/${stationCount}`}
              />
            ))}
          </ul>
        </Card>

        {/* Уведомления и статусы */}
        <Card heading={t.alertsBySeverity} padding="lg" aria-label={t.alertsBySeverity}>
          <div className="flex flex-wrap items-center gap-5">
            <Donut
              segments={(
                Object.keys(SEVERITY_COLORS) as (keyof typeof SEVERITY_COLORS)[]
              ).map((k) => ({
                value: severityCounts[k],
                color: SEVERITY_COLORS[k],
                label: dict.severity[k],
              }))}
              centerValue={String(alertCount)}
              centerLabel={t.totalAlerts}
            />
            <ul className="flex min-w-0 flex-1 flex-col gap-2.5">
              {(
                Object.keys(SEVERITY_COLORS) as (keyof typeof SEVERITY_COLORS)[]
              ).map((k) => (
                <HBar
                  key={k}
                  label={dict.severity[k]}
                  value={severityCounts[k]}
                  max={Math.max(alertCount, 1)}
                  color={SEVERITY_COLORS[k]}
                />
              ))}
            </ul>
          </div>

          {/* h4 → h3: карточка теперь даёт h2, уровень идёт без пропуска */}
          <h3 className="mb-3 mt-6 text-small font-bold">{t.stationStatuses}</h3>
          <ul className="flex flex-col gap-2.5">
            {statusCounts.map(([status, count]) => (
              <HBar
                key={status}
                label={
                  dict.status[status as keyof typeof dict.status] ?? status
                }
                value={count}
                max={stationCount}
                color="var(--info)"
              />
            ))}
          </ul>
        </Card>
      </div>

      {/* Новости: помесячная активность */}
      <NewsByMonth news={news} />
    </div>
  );
}

/** Колонки публикаций по месяцам (последние 12 месяцев). */
function NewsByMonth({ news }: { news: News[] | null }) {
  const { lang, dict } = useI18n();
  const t = dict.analytics;

  const buckets = useMemo(() => {
    const now = new Date();
    const list: { key: string; label: string; count: number }[] = [];
    const locale = lang === "tg" ? "tg-TJ" : lang === "ru" ? "ru-RU" : "en-GB";
    for (let i = 11; i >= 0; i -= 1) {
      const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
      list.push({
        key: `${d.getFullYear()}-${d.getMonth()}`,
        label: new Intl.DateTimeFormat(locale, {
          month: "short",
          timeZone: "Asia/Dushanbe",
        }).format(d),
        count: 0,
      });
    }
    for (const n of news ?? []) {
      const d = new Date(n.publishedAt);
      const key = `${d.getFullYear()}-${d.getMonth()}`;
      const bucket = list.find((b) => b.key === key);
      if (bucket) {
        bucket.count += 1;
      }
    }
    return list;
  }, [news, lang]);

  const max = Math.max(...buckets.map((b) => b.count), 1);

  return (
    <Card heading={t.newsByMonth} padding="lg" aria-label={t.newsByMonth}>
      <div className="flex h-40 items-end gap-2">
        {buckets.map((b) => (
          <div key={b.key} className="flex min-w-0 flex-1 flex-col items-center gap-1.5">
            <span className="data-text text-caption font-bold text-text-secondary">
              {b.count > 0 ? b.count : ""}
            </span>
            <div
              className="w-full max-w-8 rounded-t-[2px] bg-info transition-colors"
              style={{
                height: `${(b.count / max) * 100}%`,
                minHeight: b.count > 0 ? 6 : 2,
                opacity: b.count > 0 ? 1 : 0.2,
              }}
              title={`${b.label}: ${b.count}`}
            />
            <span className="truncate text-caption font-semibold text-text-secondary">
              {b.label}
            </span>
          </div>
        ))}
      </div>
    </Card>
  );
}
