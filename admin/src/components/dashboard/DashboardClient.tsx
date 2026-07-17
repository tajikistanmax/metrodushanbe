"use client";

/**
 * Витринный операционный дашборд: показывает только вычисляемые показатели
 * текущего контура. Пассажиропоток, поезда и платежи не симулируются — до
 * подключения промышленных источников они явно отмечены как интеграции.
 */

import Link from "next/link";
import { useSyncExternalStore, type ComponentType, type ReactNode, type SVGProps } from "react";
import { formatDateTime, pickName } from "@/lib/i18n";
import {
  IconArrowRight,
  IconBell,
  IconBolt,
  IconCheckCircle,
  IconImport,
  IconInfo,
  IconLines,
  IconMap,
  IconNews,
  IconPlus,
  IconPulse,
  IconRequests,
  IconSpark,
  IconStation,
  IconTransfer,
  IconWarning,
} from "@/lib/icons";
import type {
  AiBriefing,
  Alert,
  AuditEvent,
  CitizenRequestAdmin,
  ImportPage,
  Line,
  NetworkGeoJson,
  News,
  Station,
} from "@/lib/types";
import LineBadge from "../LineBadge";
import { useI18n } from "../I18nProvider";
import NetworkSchematic from "./NetworkSchematic";
import TajikistanOverviewMap from "./TajikistanOverviewMap";

type Props = {
  lines: Line[] | null;
  stations: Station[] | null;
  alerts: Alert[] | null;
  news: News[] | null;
  briefing: AiBriefing | null;
  audit: AuditEvent[] | null;
  requests: CitizenRequestAdmin[] | null;
  imports: ImportPage | null;
  network: NetworkGeoJson | null;
  networkSource: "api" | "demo";
  health: string;
};

const TINTS = {
  navy: "bg-[var(--kpi-tint-navy)] text-ink",
  red: "bg-[var(--kpi-tint-red)] text-brand-red",
  green: "bg-[var(--kpi-tint-green)] text-brand-green",
  info: "bg-[var(--kpi-tint-info)] text-info",
  warning: "bg-[var(--kpi-tint-warning)] text-warning",
} as const;

const subscribeClock = () => () => undefined;
const readBrowserHour = () => new Date().getHours();
const readServerHour = () => null;

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
      className="console-card group flex min-h-36 flex-col gap-3 p-4 transition-transform hover:-translate-y-0.5"
    >
      <div className="flex items-center gap-3">
        <span className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${TINTS[tint]}`}>
          <Icon className="h-[22px] w-[22px]" />
        </span>
        <span className="text-[11px] font-bold uppercase leading-tight tracking-wide text-text-secondary">
          {label}
        </span>
      </div>
      <p className="data-text text-3xl font-extrabold leading-none tracking-tight">{value}</p>
      <div className="mt-auto text-xs font-medium leading-relaxed text-text-secondary">{sub}</div>
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

function auditVisual(action: string): { cls: string; label: string } {
  const normalized = action.toLowerCase();
  if (normalized.includes("delete")) {
    return { cls: "bg-[var(--kpi-tint-red)] text-brand-red", label: "−" };
  }
  if (normalized.includes("create")) {
    return { cls: "bg-[var(--kpi-tint-green)] text-brand-green", label: "+" };
  }
  if (normalized.includes("publish")) {
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
  requests,
  imports,
  network,
  networkSource,
  health,
}: Props) {
  const { lang, dict } = useI18n();
  const t = dict.dash;

  // Time zones can differ between the Next.js server and the operator's browser.
  // Keep the server and first client render deterministic, then personalize after hydration.
  const hour = useSyncExternalStore(subscribeClock, readBrowserHour, readServerHour);
  const greeting =
    hour === null
      ? t.greetingDay
      : hour < 5 || hour >= 22
      ? t.greetingNight
      : hour < 12
        ? t.greetingMorning
        : hour < 18
          ? t.greetingDay
          : t.greetingEvening;

  const transferCount = stations?.filter((station) => station.isTransfer).length ?? 0;
  const activeLines = lines?.filter((line) => line.status === "active").length ?? 0;
  const critical = alerts?.filter((alert) => alert.severity === "critical").length ?? 0;
  const warnings = alerts?.filter((alert) => alert.severity === "warning").length ?? 0;
  const completeStations =
    stations?.filter(
      (station) =>
        station.lines.length > 0 &&
        Number.isFinite(station.coordinates[0]) &&
        Number.isFinite(station.coordinates[1]),
    ).length ?? 0;
  const geodataReadiness =
    stations && stations.length > 0
      ? Math.round((completeStations / stations.length) * 100)
      : 0;
  const openRequests =
    requests?.filter(
      (request) => request.status !== "resolved" && request.status !== "closed",
    ) ?? [];
  const latestImport = imports?.items[0] ?? null;
  const latestNews = news?.[0] ?? null;
  const auditTail = audit?.slice(0, 6) ?? [];
  const healthOk = health === "UP";
  const publicPortalUrl =
    process.env.NEXT_PUBLIC_PUBLIC_WEB_URL ?? "http://localhost:3000";

  const readiness = [
    {
      label: t.readinessCatalog,
      detail: `${lines?.length ?? 0} ${dict.countLines.toLowerCase()} · ${stations?.length ?? 0} ${dict.countStations.toLowerCase()}`,
      ok: lines !== null && stations !== null,
    },
    {
      label: t.readinessGeo,
      detail: `${geodataReadiness}% · ${networkSource === "api" ? t.sourceApi : t.sourceDemo}`,
      ok: network !== null,
    },
    {
      label: t.readinessContent,
      detail: `${news?.length ?? 0} ${dict.countNews.toLowerCase()} · ${alerts?.length ?? 0} ${dict.countAlerts.toLowerCase()}`,
      ok: news !== null && alerts !== null,
    },
    {
      label: t.readinessFeedback,
      detail: `${requests?.length ?? 0} · ${openRequests.length} ${t.kpiRequestsSub}`,
      ok: requests !== null,
    },
  ];

  return (
    <div className="flex flex-col gap-5">
      <section className="showcase-hero relative overflow-hidden rounded-[22px] p-5 text-surface-light shadow-[0_18px_46px_rgba(8,39,66,0.28)] sm:p-7">
        <div className="absolute right-6 top-5 hidden items-center gap-1 opacity-80 sm:flex" aria-hidden="true">
          <span className="h-2.5 w-8 rounded-l-full bg-brand-red" />
          <span className="h-2.5 w-8 bg-white" />
          <span className="h-2.5 w-8 rounded-r-full bg-brand-green" />
        </div>
        <div className="relative max-w-4xl">
          <span className="inline-flex items-center gap-2 rounded-full border border-white/20 bg-white/10 px-3 py-1.5 text-[11px] font-bold uppercase tracking-[0.14em]">
            <span className="h-2 w-2 rounded-full bg-[#5de18b]" />
            {t.phaseBadge}
          </span>
          <p className="mt-5 text-sm font-semibold text-white/70">{greeting}</p>
          <h1 className="mt-1 max-w-3xl text-2xl font-black tracking-tight sm:text-4xl">
            {t.heroTitle}
          </h1>
          <p className="mt-3 max-w-3xl text-sm leading-relaxed text-white/75 sm:text-base">
            {t.heroLead}
          </p>
          <div className="mt-5 flex flex-wrap gap-3">
            <a
              href={publicPortalUrl}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-2 rounded-full bg-white px-4 py-2.5 text-xs font-extrabold text-brand-navy transition-transform hover:-translate-y-0.5"
            >
              {t.openPublicPortal}
              <IconArrowRight className="h-4 w-4" />
            </a>
            <Link
              href="/map"
              className="inline-flex items-center gap-2 rounded-full border border-white/25 bg-white/10 px-4 py-2.5 text-xs font-extrabold text-white transition-colors hover:bg-white/15"
            >
              <IconMap className="h-4 w-4" />
              {t.openCityMap}
            </Link>
          </div>
        </div>
      </section>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-3 2xl:grid-cols-6">
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
          sub={stations ? t.kpiStationsSub.replace("{transfer}", String(transferCount)) : dict.loadError}
          href="/stations"
        />
        <KpiCard
          icon={IconCheckCircle}
          tint={geodataReadiness === 100 ? "green" : "warning"}
          label={t.kpiDataReady}
          value={stations ? `${geodataReadiness}%` : "—"}
          sub={stations ? t.kpiDataReadySub : dict.loadError}
          href="/imports"
        />
        <KpiCard
          icon={IconRequests}
          tint={openRequests.length > 0 ? "warning" : "green"}
          label={t.kpiRequests}
          value={requests ? String(openRequests.length) : "—"}
          sub={requests ? t.kpiRequestsSub : dict.loadError}
          href="/requests"
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
          icon={IconImport}
          tint={latestImport?.status === "failed" ? "red" : "info"}
          label={t.kpiImports}
          value={imports ? String(imports.totalElements) : "—"}
          sub={
            imports
              ? t.kpiImportsSub.replace("{status}", latestImport?.status ?? dict.empty)
              : dict.loadError
          }
          href="/imports"
        />
      </div>

      <div className="grid grid-cols-[minmax(0,1fr)] gap-5 xl:grid-cols-[minmax(300px,0.82fr)_minmax(0,1.7fr)]">
        <section className="console-card overflow-hidden p-5" aria-label={t.countryTitle}>
          <div className="mb-3">
            <h2 className="text-base font-extrabold">{t.countryTitle}</h2>
            <p className="mt-1 text-xs leading-relaxed text-text-secondary">{t.countrySubtitle}</p>
          </div>
          <TajikistanOverviewMap />
          <p className="mt-3 text-[10px] font-semibold text-text-secondary">{t.countrySource}</p>
        </section>

        <section className="console-card flex min-w-0 flex-col overflow-hidden p-5" aria-label={t.cityTitle}>
          <div className="mb-2 flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 className="text-base font-extrabold">{t.cityTitle}</h2>
              <p className="mt-1 text-xs leading-relaxed text-text-secondary">{t.citySubtitle}</p>
            </div>
            <span className={`rounded-full px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide ${
              networkSource === "api"
                ? "bg-[var(--kpi-tint-green)] text-brand-green"
                : "bg-[var(--kpi-tint-warning)] text-warning"
            }`}>
              {networkSource === "api" ? t.sourceApi : t.sourceDemo}
            </span>
          </div>

          {network ? (
            <NetworkSchematic data={network} />
          ) : (
            <p className="py-20 text-center text-sm text-text-secondary">{dict.loadError}</p>
          )}

          {lines && lines.length > 0 && (
            <ul className="mt-3 flex flex-wrap gap-x-5 gap-y-2 border-t border-card-border pt-3">
              {lines.map((line) => (
                <li key={line.code} className="flex items-center gap-2 text-xs font-semibold">
                  <LineBadge code={line.code} colorHex={line.colorHex} />
                  <span>{pickName(line.name, lang)}</span>
                  <span className="text-text-secondary">
                    · {stations?.filter((station) => station.lines.includes(line.code)).length ?? 0} {t.legendStations}
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
      </div>

      <div className="grid grid-cols-[minmax(0,1fr)] gap-5 xl:grid-cols-[minmax(0,1.5fr)_minmax(300px,0.8fr)]">
        <section className="console-card p-5" aria-label={t.readinessTitle}>
          <div className="mb-4">
            <h2 className="text-base font-extrabold">{t.readinessTitle}</h2>
            <p className="mt-1 text-xs leading-relaxed text-text-secondary">{t.readinessLead}</p>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            {readiness.map((item) => (
              <div key={item.label} className="flex items-start justify-between gap-3 rounded-2xl border border-card-border p-4">
                <div>
                  <p className="text-sm font-bold">{item.label}</p>
                  <p className="mt-1 text-xs leading-relaxed text-text-secondary">{item.detail}</p>
                </div>
                <span className={`shrink-0 rounded-full px-2.5 py-1 text-[10px] font-bold uppercase ${
                  item.ok
                    ? "bg-[var(--kpi-tint-green)] text-brand-green"
                    : "bg-[var(--kpi-tint-red)] text-brand-red"
                }`}>
                  {item.ok ? t.ready : dict.loadError}
                </span>
              </div>
            ))}
          </div>
        </section>

        <section className="console-card p-5" aria-label={t.externalTitle}>
          <h2 className="text-base font-extrabold">{t.externalTitle}</h2>
          <p className="mt-1 text-xs leading-relaxed text-text-secondary">{t.externalLead}</p>
          <ul className="mt-4 flex flex-col gap-2.5">
            {[t.externalAfc, t.externalRealtime, t.externalPayments].map((label) => (
              <li key={label} className="flex items-center justify-between gap-3 rounded-xl border border-card-border px-3.5 py-3">
                <span className="text-xs font-bold">{label}</span>
                <span className="shrink-0 rounded-full bg-[var(--kpi-tint-warning)] px-2 py-1 text-[9px] font-extrabold uppercase text-warning">
                  {t.notConnected}
                </span>
              </li>
            ))}
          </ul>
        </section>
      </div>

      <div className="grid grid-cols-[minmax(0,1fr)] gap-5 lg:grid-cols-3">
        <section className="console-card p-5" aria-label={dict.alertsTitle}>
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-base font-extrabold">{dict.alertsTitle}</h2>
            <Link href="/alerts" className="flex items-center gap-1 text-xs font-bold text-info hover:underline">
              {t.viewAll}<IconArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>
          {alerts && alerts.length > 0 ? (
            <ul className="flex flex-col gap-3">
              {alerts.slice(0, 4).map((alert) => (
                <li key={alert.code} className="flex gap-3">
                  <span className="mt-0.5 shrink-0">{severityIcon(alert.severity)}</span>
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold">{pickName(alert.title, lang)}</p>
                    <p className="text-xs text-text-secondary">
                      {dict.severity[alert.severity]} · <time dateTime={alert.startsAt} className="data-text">{formatDateTime(alert.startsAt, lang)}</time>
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

        <section className="console-card p-5" aria-label={t.requestQueueTitle}>
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-base font-extrabold">{t.requestQueueTitle}</h2>
            <Link href="/requests" className="flex items-center gap-1 text-xs font-bold text-info hover:underline">
              {t.viewAll}<IconArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>
          {openRequests.length > 0 ? (
            <ul className="flex flex-col gap-3">
              {openRequests.slice(0, 4).map((request) => (
                <li key={request.code} className="rounded-xl border border-card-border px-3 py-2.5">
                  <div className="flex items-center justify-between gap-2">
                    <span className="data-text text-[10px] font-bold text-info">{request.code}</span>
                    <span className="text-[10px] font-bold text-text-secondary">{dict.operations.requestStatuses[request.status]}</span>
                  </div>
                  <p className="mt-1 truncate text-sm font-semibold">{request.subject}</p>
                </li>
              ))}
            </ul>
          ) : (
            <p className="py-6 text-center text-sm text-text-secondary">{t.requestQueueEmpty}</p>
          )}
        </section>

        <section className="console-card p-5" aria-label={t.quickTitle}>
          <h2 className="mb-3 flex items-center gap-2 text-base font-extrabold">
            <IconBolt className="h-[18px] w-[18px] text-warning" />
            {t.quickTitle}
          </h2>
          <div className="grid grid-cols-2 gap-2.5">
            {[
              { href: "/lines", label: t.quickLine },
              { href: "/stations", label: t.quickStation },
              { href: "/alerts", label: t.quickAlert },
              { href: "/imports", label: dict.operations.importsTitle },
            ].map((action) => (
              <Link key={action.href} href={action.href} className="flex items-center gap-2 rounded-xl border border-card-border px-3 py-3 text-xs font-bold transition-colors hover:border-info hover:text-info">
                <IconPlus className="h-4 w-4 shrink-0" />
                {action.label}
              </Link>
            ))}
          </div>
          {latestNews && (
            <div className="mt-3 rounded-xl bg-[var(--chip-bg)] p-3">
              <p className="flex items-center gap-2 text-[10px] font-extrabold uppercase tracking-wide text-text-secondary">
                <IconNews className="h-3.5 w-3.5" />{dict.countNews}
              </p>
              <p className="mt-1 truncate text-xs font-bold">{pickName(latestNews.title, lang)}</p>
            </div>
          )}
        </section>
      </div>

      <div className="grid grid-cols-[minmax(0,1fr)] gap-5 xl:grid-cols-[minmax(0,2fr)_minmax(320px,1fr)]">
        <section className="console-card p-5" aria-label={t.auditTitle}>
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-base font-extrabold">{t.auditTitle}</h2>
            <Link href="/audit" className="flex items-center gap-1 text-xs font-bold text-info hover:underline">
              {t.viewAll}<IconArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>
          {auditTail.length > 0 ? (
            <ol className="grid gap-x-5 sm:grid-cols-2">
              {auditTail.map((event) => {
                const visual = auditVisual(event.action);
                return (
                  <li key={event.id} className="flex gap-3 border-b border-card-border py-3 first:pt-0 last:border-0">
                    <span aria-hidden="true" className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-sm font-extrabold ${visual.cls}`}>{visual.label}</span>
                    <div className="min-w-0 pt-1">
                      <p className="truncate text-sm font-semibold"><span className="data-text text-xs">{event.action}</span> · {event.entityType}</p>
                      <p className="truncate text-xs text-text-secondary">{event.actor} · <time dateTime={event.at} className="data-text">{formatDateTime(event.at, lang)}</time></p>
                    </div>
                  </li>
                );
              })}
            </ol>
          ) : (
            <p className="py-6 text-center text-sm text-text-secondary">{t.auditEmpty}</p>
          )}
        </section>

        <section className="console-card p-5" aria-label={t.systemTitle}>
          <h2 className="mb-3 flex items-center gap-2 text-base font-extrabold">
            <IconPulse className="h-[18px] w-[18px] text-info" />
            {t.systemTitle}
          </h2>
          <ul className="flex flex-col gap-2.5">
            <li className="flex items-center justify-between rounded-xl border border-card-border px-3.5 py-3">
              <span className="text-sm font-semibold">{t.healthBackend}</span>
              <span className={`flex items-center gap-1.5 text-xs font-bold ${healthOk ? "text-brand-green" : "text-brand-red"}`}>
                <span aria-hidden="true" className={`h-2 w-2 rounded-full ${healthOk ? "bg-brand-green" : "bg-brand-red"}`} />
                {healthOk ? t.healthUp : t.healthDown}
              </span>
            </li>
            <li className="flex items-center justify-between rounded-xl border border-card-border px-3.5 py-3">
              <span className="text-sm font-semibold">{t.healthData}</span>
              <span className={`text-xs font-bold ${networkSource === "api" ? "text-brand-green" : "text-warning"}`}>
                {networkSource === "api" ? t.sourceApi : t.sourceDemo}
              </span>
            </li>
          </ul>
          {briefing && (
            <div className="mt-3 rounded-xl bg-[var(--chip-bg)] p-3">
              <p className="flex items-center gap-2 text-xs font-extrabold"><IconSpark className="h-4 w-4 text-brand-green" />{t.aiTitle}</p>
              <p className="mt-1 text-[11px] leading-relaxed text-text-secondary">{briefing.recommendations[0] ?? briefing.posture}</p>
            </div>
          )}
          <p className="mt-3 text-[11px] leading-relaxed text-text-secondary">{t.systemFootnote}</p>
        </section>
      </div>
    </div>
  );
}
