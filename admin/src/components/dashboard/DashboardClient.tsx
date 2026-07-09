"use client";

/**
 * Операционный дашборд (ТЗ §8.4): KPI-карты → рабочий контур (схема сети +
 * активные уведомления + быстрые действия) → лента изменений и статус систем.
 * Все данные приходят с сервера готовыми props (page.tsx); фейковых метрик
 * нет — вторичные строки KPI считаются из реальных данных.
 */

import Link from "next/link";
import type { ComponentType, ReactNode, SVGProps } from "react";
import { formatDateTime, pickName } from "@/lib/i18n";
import {
  IconArrowRight,
  IconBell,
  IconBolt,
  IconCheckCircle,
  IconInfo,
  IconLines,
  IconNews,
  IconPlus,
  IconPulse,
  IconSpark,
  IconStation,
  IconTransfer,
  IconWarning,
} from "@/lib/icons";
import type {
  AiBriefing,
  Alert,
  AuditEvent,
  Line,
  NetworkGeoJson,
  News,
  Station,
} from "@/lib/types";
import LineBadge from "../LineBadge";
import { useI18n } from "../I18nProvider";
import NetworkSchematic from "./NetworkSchematic";

type Props = {
  lines: Line[] | null;
  stations: Station[] | null;
  alerts: Alert[] | null;
  news: News[] | null;
  briefing: AiBriefing | null;
  audit: AuditEvent[] | null;
  network: NetworkGeoJson | null;
  networkSource: "api" | "demo";
  health: string;
};

/** Оттенки KPI-карт (иконная плашка). */
const TINTS = {
  navy: "bg-[var(--kpi-tint-navy)] text-ink",
  red: "bg-[var(--kpi-tint-red)] text-brand-red",
  green: "bg-[var(--kpi-tint-green)] text-brand-green",
  info: "bg-[var(--kpi-tint-info)] text-info",
  warning: "bg-[var(--kpi-tint-warning)] text-warning",
} as const;

function KpiCard({
  icon: Icon,
  tint,
  label,
  value,
  sub,
  href,
}: {
  icon: ComponentType<SVGProps<SVGSVGElement>>;
  tint: keyof typeof TINTS;
  label: string;
  value: string;
  sub: ReactNode;
  href: string;
}) {
  return (
    <Link
      href={href}
      className="console-card group flex flex-col gap-3 p-4 transition-transform hover:-translate-y-0.5"
    >
      <div className="flex items-center gap-3">
        <span
          className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${TINTS[tint]}`}
        >
          <Icon className="h-[22px] w-[22px]" />
        </span>
        <span className="text-xs font-semibold uppercase tracking-wide text-text-secondary">
          {label}
        </span>
      </div>
      <p className="text-3xl font-extrabold leading-none tracking-tight">{value}</p>
      <div className="text-xs font-medium text-text-secondary">{sub}</div>
    </Link>
  );
}

function severityIcon(severity: Alert["severity"]) {
  if (severity === "critical") {
    return <IconWarning className="h-[18px] w-[18px] text-brand-red" />;
  }
  if (severity === "warning") {
    return <IconWarning className="h-[18px] w-[18px] text-warning" />;
  }
  return <IconInfo className="h-[18px] w-[18px] text-info" />;
}

/** Плашка записи аудита по типу действия (плюс/минус/стрелка/карандаш). */
function auditVisual(action: string): { cls: string; label: string } {
  const a = action.toLowerCase();
  if (a.includes("delete")) {
    return { cls: "bg-[var(--kpi-tint-red)] text-brand-red", label: "−" };
  }
  if (a.includes("create")) {
    return { cls: "bg-[var(--kpi-tint-green)] text-brand-green", label: "+" };
  }
  if (a.includes("publish")) {
    return { cls: "bg-[var(--kpi-tint-info)] text-info", label: "↗" };
  }
  return { cls: "bg-[var(--kpi-tint-navy)] text-ink", label: "✎" };
}

export default function DashboardClient({
  lines,
  stations,
  alerts,
  news,
  briefing,
  audit,
  network,
  networkSource,
  health,
}: Props) {
  const { lang, dict } = useI18n();
  const t = dict.dash;

  const hour = new Date().getHours();
  const greeting =
    hour < 5 || hour >= 22
      ? t.greetingNight
      : hour < 12
        ? t.greetingMorning
        : hour < 18
          ? t.greetingDay
          : t.greetingEvening;

  const transferCount = stations?.filter((s) => s.isTransfer).length ?? 0;
  const activeLines = lines?.filter((l) => l.status === "active").length ?? 0;
  const critical = alerts?.filter((a) => a.severity === "critical").length ?? 0;
  const warnings = alerts?.filter((a) => a.severity === "warning").length ?? 0;

  const healthOk = health === "UP";
  const latestNews = news?.[0] ?? null;
  const auditTail = audit?.slice(0, 6) ?? [];

  return (
    <div className="flex flex-col gap-5">
      {/* Приветствие */}
      <div>
        <h1 className="text-2xl font-extrabold tracking-tight sm:text-[28px]">
          {greeting}
        </h1>
        <p className="mt-1 text-sm text-text-secondary">{t.lead}</p>
      </div>

      {/* KPI */}
      <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-5">
        <KpiCard
          icon={IconLines}
          tint="red"
          label={dict.countLines}
          value={lines ? String(lines.length) : "—"}
          sub={
            lines
              ? t.kpiLinesSub
                  .replace("{active}", String(activeLines))
                  .replace("{planned}", String(lines.length - activeLines))
              : dict.loadError
          }
          href="/lines"
        />
        <KpiCard
          icon={IconStation}
          tint="navy"
          label={dict.countStations}
          value={stations ? String(stations.length) : "—"}
          sub={
            stations
              ? t.kpiStationsSub.replace("{transfer}", String(transferCount))
              : dict.loadError
          }
          href="/stations"
        />
        <KpiCard
          icon={IconBell}
          tint={critical > 0 ? "red" : warnings > 0 ? "warning" : "green"}
          label={dict.countAlerts}
          value={alerts ? String(alerts.length) : "—"}
          sub={
            alerts
              ? t.kpiAlertsSub
                  .replace("{critical}", String(critical))
                  .replace("{warning}", String(warnings))
              : dict.loadError
          }
          href="/alerts"
        />
        <KpiCard
          icon={IconNews}
          tint="info"
          label={dict.countNews}
          value={news ? String(news.length) : "—"}
          sub={
            latestNews
              ? `${t.kpiNewsSub} ${formatDateTime(latestNews.publishedAt, lang)}`
              : news
                ? dict.empty
                : dict.loadError
          }
          href="/news"
        />
        <KpiCard
          icon={IconSpark}
          tint="green"
          label={dict.countAgents}
          value={briefing ? String(briefing.agents.length) : "—"}
          sub={briefing ? `${t.kpiAgentsSub} ${briefing.posture}` : dict.loadError}
          href="/agents"
        />
      </div>

      {/* Рабочий контур: схема + правая колонка */}
      <div className="grid gap-5 xl:grid-cols-[minmax(0,2fr)_minmax(320px,1fr)]">
        {/* Схема сети */}
        <section className="console-card flex flex-col p-5" aria-label={t.networkTitle}>
          <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-base font-extrabold">{t.networkTitle}</h3>
            <span
              className={`rounded-full px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide ${
                networkSource === "api"
                  ? "bg-[var(--kpi-tint-green)] text-brand-green"
                  : "bg-[var(--kpi-tint-warning)] text-warning"
              }`}
            >
              {networkSource === "api" ? t.sourceApi : t.sourceDemo}
            </span>
          </div>

          {network ? (
            <NetworkSchematic data={network} />
          ) : (
            <p className="py-16 text-center text-sm text-text-secondary">
              {dict.loadError}
            </p>
          )}

          {/* Легенда */}
          {lines && lines.length > 0 && (
            <ul className="mt-3 flex flex-wrap gap-x-5 gap-y-2 border-t border-card-border pt-3">
              {lines.map((l) => (
                <li key={l.code} className="flex items-center gap-2 text-xs font-semibold">
                  <LineBadge code={l.code} colorHex={l.colorHex} />
                  <span>{pickName(l.name, lang)}</span>
                  <span className="text-text-secondary">
                    {stations
                      ? `· ${stations.filter((s) => s.lines.includes(l.code)).length} ${t.legendStations}`
                      : null}
                  </span>
                </li>
              ))}
              <li className="flex items-center gap-2 text-xs font-semibold text-text-secondary">
                <IconTransfer className="h-4 w-4" />
                {t.legendTransfer}: {transferCount}
              </li>
            </ul>
          )}
        </section>

        {/* Правая колонка */}
        <div className="flex flex-col gap-5">
          {/* Активные уведомления */}
          <section className="console-card p-5" aria-label={dict.alertsTitle}>
            <div className="mb-3 flex items-center justify-between">
              <h3 className="text-base font-extrabold">{dict.alertsTitle}</h3>
              <Link
                href="/alerts"
                className="flex items-center gap-1 text-xs font-bold text-info hover:underline"
              >
                {t.viewAll}
                <IconArrowRight className="h-3.5 w-3.5" />
              </Link>
            </div>
            {alerts && alerts.length > 0 ? (
              <ul className="flex flex-col gap-3">
                {alerts.slice(0, 4).map((a) => (
                  <li key={a.code} className="flex gap-3">
                    <span className="mt-0.5 shrink-0">{severityIcon(a.severity)}</span>
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold">
                        {pickName(a.title, lang)}
                      </p>
                      <p className="text-xs text-text-secondary">
                        {dict.severity[a.severity]} ·{" "}
                        <time dateTime={a.startsAt} className="data-text">
                          {formatDateTime(a.startsAt, lang)}
                        </time>
                      </p>
                    </div>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="flex items-center gap-2 rounded-xl bg-[var(--kpi-tint-green)] px-3 py-3 text-sm font-semibold text-brand-green">
                <IconCheckCircle className="h-5 w-5 shrink-0" />
                {t.alertsEmpty}
              </p>
            )}
          </section>

          {/* Быстрые действия */}
          <section className="console-card p-5" aria-label={t.quickTitle}>
            <h3 className="mb-3 flex items-center gap-2 text-base font-extrabold">
              <IconBolt className="h-[18px] w-[18px] text-warning" />
              {t.quickTitle}
            </h3>
            <div className="grid grid-cols-2 gap-2.5">
              {[
                { href: "/lines", label: t.quickLine },
                { href: "/stations", label: t.quickStation },
                { href: "/alerts", label: t.quickAlert },
                { href: "/news", label: t.quickNews },
              ].map((qa) => (
                <Link
                  key={qa.href}
                  href={qa.href}
                  className="flex items-center gap-2 rounded-xl border border-card-border px-3 py-3 text-xs font-bold transition-colors hover:border-info hover:text-info"
                >
                  <IconPlus className="h-4 w-4 shrink-0" />
                  {qa.label}
                </Link>
              ))}
            </div>
          </section>

          {/* AI-брифинг */}
          {briefing && (
            <section className="console-card p-5" aria-label={t.aiTitle}>
              <div className="mb-2 flex items-center justify-between">
                <h3 className="flex items-center gap-2 text-base font-extrabold">
                  <IconSpark className="h-[18px] w-[18px] text-brand-green" />
                  {t.aiTitle}
                </h3>
                <Link
                  href="/agents"
                  className="flex items-center gap-1 text-xs font-bold text-info hover:underline"
                >
                  {t.viewAll}
                  <IconArrowRight className="h-3.5 w-3.5" />
                </Link>
              </div>
              <p className="text-xs text-text-secondary">
                {t.aiPosture}: <span className="font-bold text-ink">{briefing.posture}</span>
              </p>
              {briefing.recommendations[0] && (
                <p className="mt-2 rounded-xl bg-[var(--chip-bg)] px-3 py-2.5 text-xs leading-relaxed">
                  {briefing.recommendations[0]}
                </p>
              )}
            </section>
          )}
        </div>
      </div>

      {/* Нижний ряд: аудит + системы */}
      <div className="grid gap-5 xl:grid-cols-[minmax(0,2fr)_minmax(320px,1fr)]">
        {/* Последние изменения */}
        <section className="console-card p-5" aria-label={t.auditTitle}>
          <div className="mb-3 flex items-center justify-between">
            <h3 className="text-base font-extrabold">{t.auditTitle}</h3>
            <Link
              href="/audit"
              className="flex items-center gap-1 text-xs font-bold text-info hover:underline"
            >
              {t.viewAll}
              <IconArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>
          {auditTail.length > 0 ? (
            <ol className="flex flex-col">
              {auditTail.map((e, i) => {
                const v = auditVisual(e.action);
                return (
                  <li key={e.id} className="relative flex gap-3 pb-4 last:pb-0">
                    {/* Вертикальная нить таймлайна */}
                    {i < auditTail.length - 1 && (
                      <span
                        aria-hidden="true"
                        className="absolute left-[15px] top-8 h-[calc(100%-28px)] w-px bg-card-border"
                      />
                    )}
                    <span
                      aria-hidden="true"
                      className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-sm font-extrabold ${v.cls}`}
                    >
                      {v.label}
                    </span>
                    <div className="min-w-0 pt-1">
                      <p className="text-sm font-semibold">
                        <span className="data-text text-xs">{e.action}</span>{" "}
                        <span className="text-text-secondary">·</span> {e.entityType}{" "}
                        <span className="data-text text-xs">{e.entityId}</span>
                      </p>
                      <p className="text-xs text-text-secondary">
                        {e.actor} ·{" "}
                        <time dateTime={e.at} className="data-text">
                          {formatDateTime(e.at, lang)}
                        </time>
                      </p>
                    </div>
                  </li>
                );
              })}
            </ol>
          ) : (
            <p className="py-6 text-center text-sm text-text-secondary">
              {t.auditEmpty}
            </p>
          )}
        </section>

        {/* Статус систем */}
        <section className="console-card p-5" aria-label={t.systemTitle}>
          <h3 className="mb-3 flex items-center gap-2 text-base font-extrabold">
            <IconPulse className="h-[18px] w-[18px] text-info" />
            {t.systemTitle}
          </h3>
          <ul className="flex flex-col gap-2.5">
            <li className="flex items-center justify-between rounded-xl border border-card-border px-3.5 py-3">
              <span className="text-sm font-semibold">{t.healthBackend}</span>
              <span
                className={`flex items-center gap-1.5 text-xs font-bold ${
                  healthOk ? "text-brand-green" : "text-brand-red"
                }`}
              >
                <span
                  aria-hidden="true"
                  className={`h-2 w-2 rounded-full ${healthOk ? "bg-brand-green" : "bg-brand-red"}`}
                />
                {healthOk ? t.healthUp : t.healthDown}
              </span>
            </li>
            <li className="flex items-center justify-between rounded-xl border border-card-border px-3.5 py-3">
              <span className="text-sm font-semibold">{t.healthData}</span>
              <span
                className={`text-xs font-bold ${
                  networkSource === "api" ? "text-brand-green" : "text-warning"
                }`}
              >
                {networkSource === "api" ? t.sourceApi : t.sourceDemo}
              </span>
            </li>
            <li className="flex items-center justify-between rounded-xl border border-card-border px-3.5 py-3">
              <span className="text-sm font-semibold">{t.healthAlerts}</span>
              <span
                className={`text-xs font-bold ${
                  critical > 0
                    ? "text-brand-red"
                    : warnings > 0
                      ? "text-warning"
                      : "text-brand-green"
                }`}
              >
                {critical > 0
                  ? `${critical} ${dict.severity.critical.toLowerCase()}`
                  : warnings > 0
                    ? `${warnings} ${dict.severity.warning.toLowerCase()}`
                    : t.healthCalm}
              </span>
            </li>
          </ul>
          <p className="mt-3 text-[11px] leading-relaxed text-text-secondary">
            {t.systemFootnote}
          </p>
        </section>
      </div>
    </div>
  );
}
