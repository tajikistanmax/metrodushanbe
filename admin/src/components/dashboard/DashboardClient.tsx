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
import { Badge, Card } from "@/shared/ui";

/** Ссылка «смотреть все» в шапке карточки — повторялась в 4 секциях. */
function ViewAllLink({ href, label }: { href: string; label: string }) {
  return (
    <Link
      href={href}
      className="flex items-center gap-1 text-small font-bold text-info hover:underline"
    >
      {label}
      <IconArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
    </Link>
  );
}

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

/**
 * Тинты плашки-иконки KPI. Токены — новые (--tint-*), старые --kpi-tint-*
 * оставались алиасами.
 *
 * Текст/иконка внутри плашки — --text-primary, а не цвет тона. Прежняя пара
 * «цветной текст на цветном тинте» (text-brand-green на --tint-success)
 * проваливала AA в тёмной теме: тинт там — rgba(19,138,61,.24) поверх #0f1d2e,
 * и #138a3d на нём даёт ≈1.6:1. Тон теперь несёт подложка и подпись рядом,
 * а не цвет глифа (SC 1.4.1 + 1.4.3).
 */
const TINTS = {
  navy: "bg-[var(--tint-neutral)]",
  red: "bg-[var(--tint-critical)]",
  green: "bg-[var(--tint-success)]",
  info: "bg-[var(--tint-info)]",
  warning: "bg-[var(--tint-warning)]",
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
    // Карточка-ссылка не «подпрыгивает» на ховере: hover:-translate-y-0.5 —
    // язык маркетинговых плиток. Отклик даёт подложка (SC 1.4.1 не затронут:
    // ссылка и так распознаётся по подписи).
    <Link
      href={href}
      className="group flex min-h-36 flex-col gap-3 rounded-panel border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-4 transition-colors hover:bg-[var(--surface-hover-subtle)]"
    >
      <div className="flex items-center gap-3">
        <span
          aria-hidden="true"
          className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-control text-[var(--text-primary)] ${TINTS[tint]}`}
        >
          <Icon className="h-[22px] w-[22px]" />
        </span>
        <span className="text-caption font-bold uppercase leading-tight tracking-[0.08em] text-text-secondary">
          {label}
        </span>
      </div>
      <p className="data-text text-title-l font-bold leading-none">{value}</p>
      <div className="mt-auto text-caption leading-relaxed text-text-secondary">{sub}</div>
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

/** Тинт + знак операции. Знак — не единственный носитель: рядом текст action. */
function auditVisual(action: string): { cls: string; label: string } {
  const normalized = action.toLowerCase();
  if (normalized.includes("delete")) {
    return { cls: "bg-[var(--tint-critical)]", label: "−" };
  }
  if (normalized.includes("create")) {
    return { cls: "bg-[var(--tint-success)]", label: "+" };
  }
  if (normalized.includes("publish")) {
    return { cls: "bg-[var(--tint-info)]", label: "↗" };
  }
  return { cls: "bg-[var(--tint-neutral)]", label: "✎" };
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
      {/*
        Шапка дашборда. Было: .showcase-hero (два радиальных градиента +
        трёхстоповый линейный) + тень 0 18px 46px + радиус 22px + rounded-full
        на кнопках. Стало: плоская navy-плоскость, лента флага сверху как
        государственная сигнатура, радиусы по шкале. Никакого свечения.
      */}
      <section className="overflow-hidden rounded-panel bg-brand-navy text-surface-light">
        <div className="ribbon-flag" aria-hidden="true" />
        <div className="max-w-4xl p-5 sm:p-7">
          <span className="inline-flex items-center gap-2 rounded-chip border border-surface-light/25 px-3 py-1.5 text-caption font-bold uppercase tracking-[0.08em]">
            {/* --status-ok (#4ade80) вместо выдуманного #5de18b: ≥3:1 к navy */}
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-status-ok" />
            {t.phaseBadge}
          </span>
          <p className="mt-5 text-small font-semibold text-surface-light/75">{greeting}</p>
          {/* font-black(900) не загружен вовсе; 800 — предел шкалы */}
          <h1 className="mt-1 max-w-3xl text-title-m font-extrabold sm:text-title-l">
            {t.heroTitle}
          </h1>
          <p className="mt-3 max-w-3xl text-small leading-relaxed text-surface-light/80 sm:text-body">
            {t.heroLead}
          </p>
          <div className="mt-5 flex flex-wrap gap-3">
            <a
              href={publicPortalUrl}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-2 rounded-control bg-surface-light px-4 py-2.5 text-small font-bold text-brand-navy transition-colors hover:bg-surface-light/90 focus-visible:outline-[var(--focus-ring-on-dark)]"
            >
              {t.openPublicPortal}
              <IconArrowRight className="h-4 w-4" />
            </a>
            <Link
              href="/map"
              className="inline-flex items-center gap-2 rounded-control border border-surface-light/40 px-4 py-2.5 text-small font-bold text-surface-light transition-colors hover:bg-surface-light/10 focus-visible:outline-[var(--focus-ring-on-dark)]"
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
        <Card
          heading={t.countryTitle}
          description={t.countrySubtitle}
          padding="lg"
          className="overflow-hidden"
          aria-label={t.countryTitle}
        >
          <TajikistanOverviewMap />
          <p className="mt-3 text-caption text-text-secondary">{t.countrySource}</p>
        </Card>

        <Card
          heading={t.cityTitle}
          description={t.citySubtitle}
          actions={
            <Badge tone={networkSource === "api" ? "success" : "warning"} dot>
              {networkSource === "api" ? t.sourceApi : t.sourceDemo}
            </Badge>
          }
          padding="lg"
          className="flex min-w-0 flex-col overflow-hidden"
          aria-label={t.cityTitle}
        >
          {network ? (
            <NetworkSchematic data={network} />
          ) : (
            <p className="py-20 text-center text-small text-text-secondary">{dict.loadError}</p>
          )}

          {lines && lines.length > 0 && (
            <ul className="mt-3 flex flex-wrap gap-x-5 gap-y-2 border-t border-[var(--border-subtle)] pt-3">
              {lines.map((line) => (
                <li key={line.code} className="flex items-center gap-2 text-small font-semibold">
                  <LineBadge code={line.code} colorHex={line.colorHex} />
                  <span>{pickName(line.name, lang)}</span>
                  <span className="text-text-secondary">
                    · {stations?.filter((station) => station.lines.includes(line.code)).length ?? 0} {t.legendStations}
                  </span>
                </li>
              ))}
              <li className="flex items-center gap-2 text-small font-semibold text-text-secondary">
                <IconTransfer className="h-4 w-4" />
                {t.legendTransfer}: {transferCount}
              </li>
            </ul>
          )}
        </Card>
      </div>

      <div className="grid grid-cols-[minmax(0,1fr)] gap-5 xl:grid-cols-[minmax(0,1.5fr)_minmax(300px,0.8fr)]">
        <Card
          heading={t.readinessTitle}
          description={t.readinessLead}
          padding="lg"
          aria-label={t.readinessTitle}
        >
          <div className="grid gap-3 sm:grid-cols-2">
            {readiness.map((item) => (
              <div key={item.label} className="flex items-start justify-between gap-3 rounded-panel border border-[var(--border-subtle)] p-4">
                <div>
                  <p className="text-small font-bold">{item.label}</p>
                  <p className="mt-1 text-caption leading-relaxed text-text-secondary">{item.detail}</p>
                </div>
                <Badge tone={item.ok ? "success" : "critical"} dot className="shrink-0">
                  {item.ok ? t.ready : dict.loadError}
                </Badge>
              </div>
            ))}
          </div>
        </Card>

        <Card
          heading={t.externalTitle}
          description={t.externalLead}
          padding="lg"
          aria-label={t.externalTitle}
        >
          <ul className="flex flex-col gap-2.5">
            {[t.externalAfc, t.externalRealtime, t.externalPayments].map((label) => (
              <li key={label} className="flex items-center justify-between gap-3 rounded-control border border-[var(--border-subtle)] px-3.5 py-3">
                <span className="text-small font-bold">{label}</span>
                <Badge tone="warning" className="shrink-0">
                  {t.notConnected}
                </Badge>
              </li>
            ))}
          </ul>
        </Card>
      </div>

      <div className="grid grid-cols-[minmax(0,1fr)] gap-5 lg:grid-cols-3">
        <Card
          heading={dict.alertsTitle}
          actions={<ViewAllLink href="/alerts" label={t.viewAll} />}
          padding="lg"
          aria-label={dict.alertsTitle}
        >
          {alerts && alerts.length > 0 ? (
            <ul className="flex flex-col gap-3">
              {alerts.slice(0, 4).map((alert) => (
                <li key={alert.code} className="flex gap-3">
                  <span className="mt-0.5 shrink-0">{severityIcon(alert.severity)}</span>
                  <div className="min-w-0">
                    <p className="truncate text-small font-semibold">{pickName(alert.title, lang)}</p>
                    <p className="text-caption text-text-secondary">
                      {dict.severity[alert.severity]} · <time dateTime={alert.startsAt} className="data-text">{formatDateTime(alert.startsAt, lang)}</time>
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          ) : (
            // Текст --text-primary поверх тинта (≈13:1 в обеих темах) вместо
            // text-brand-green на --tint-success (≈1.6:1 в тёмной).
            <p className="flex items-center gap-2 rounded-control bg-[var(--tint-success)] px-3 py-3 text-small font-semibold text-[var(--text-primary)]">
              <IconCheckCircle className="h-5 w-5 shrink-0 text-brand-green" />
              {t.alertsEmpty}
            </p>
          )}
        </Card>

        <Card
          heading={t.requestQueueTitle}
          actions={<ViewAllLink href="/requests" label={t.viewAll} />}
          padding="lg"
          aria-label={t.requestQueueTitle}
        >
          {openRequests.length > 0 ? (
            <ul className="flex flex-col gap-3">
              {openRequests.slice(0, 4).map((request) => (
                <li key={request.code} className="rounded-control border border-[var(--border-subtle)] px-3 py-2.5">
                  <div className="flex items-center justify-between gap-2">
                    <span className="data-text text-caption font-bold">{request.code}</span>
                    <span className="text-caption font-semibold text-text-secondary">{dict.operations.requestStatuses[request.status]}</span>
                  </div>
                  <p className="mt-1 truncate text-small font-semibold">{request.subject}</p>
                </li>
              ))}
            </ul>
          ) : (
            <p className="py-6 text-center text-small text-text-secondary">{t.requestQueueEmpty}</p>
          )}
        </Card>

        <Card
          heading={
            <span className="flex items-center gap-2">
              <IconBolt className="h-[18px] w-[18px] text-warning" aria-hidden="true" />
              {t.quickTitle}
            </span>
          }
          padding="lg"
          aria-label={t.quickTitle}
        >
          <div className="grid grid-cols-2 gap-2.5">
            {[
              { href: "/lines", label: t.quickLine },
              { href: "/stations", label: t.quickStation },
              { href: "/alerts", label: t.quickAlert },
              { href: "/imports", label: dict.operations.importsTitle },
            ].map((action) => (
              <Link key={action.href} href={action.href} className="flex items-center gap-2 rounded-control border border-[var(--border-subtle)] px-3 py-3 text-small font-bold transition-colors hover:bg-[var(--surface-hover)]">
                <IconPlus className="h-4 w-4 shrink-0" aria-hidden="true" />
                {action.label}
              </Link>
            ))}
          </div>
          {latestNews && (
            <div className="mt-3 rounded-control bg-[var(--surface-chip)] p-3">
              <p className="flex items-center gap-2 text-caption font-bold uppercase tracking-[0.08em] text-text-secondary">
                <IconNews className="h-3.5 w-3.5" aria-hidden="true" />{dict.countNews}
              </p>
              <p className="mt-1 truncate text-small font-bold">{pickName(latestNews.title, lang)}</p>
            </div>
          )}
        </Card>
      </div>

      <div className="grid grid-cols-[minmax(0,1fr)] gap-5 xl:grid-cols-[minmax(0,2fr)_minmax(320px,1fr)]">
        <Card
          heading={t.auditTitle}
          actions={<ViewAllLink href="/audit" label={t.viewAll} />}
          padding="lg"
          aria-label={t.auditTitle}
        >
          {auditTail.length > 0 ? (
            <ol className="grid gap-x-5 sm:grid-cols-2">
              {auditTail.map((event) => {
                const visual = auditVisual(event.action);
                return (
                  <li key={event.id} className="flex gap-3 border-b border-[var(--border-subtle)] py-3 first:pt-0 last:border-0">
                    {/* Окружность — законный rounded-full; знак дублирует
                        текст action рядом, а не заменяет его. */}
                    <span aria-hidden="true" className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-small font-bold text-[var(--text-primary)] ${visual.cls}`}>{visual.label}</span>
                    <div className="min-w-0 pt-1">
                      <p className="truncate text-small font-semibold"><span className="data-text text-caption">{event.action}</span> · {event.entityType}</p>
                      <p className="truncate text-caption text-text-secondary">{event.actor} · <time dateTime={event.at} className="data-text">{formatDateTime(event.at, lang)}</time></p>
                    </div>
                  </li>
                );
              })}
            </ol>
          ) : (
            <p className="py-6 text-center text-small text-text-secondary">{t.auditEmpty}</p>
          )}
        </Card>

        <Card
          heading={
            <span className="flex items-center gap-2">
              <IconPulse className="h-[18px] w-[18px] text-info" aria-hidden="true" />
              {t.systemTitle}
            </span>
          }
          padding="lg"
          aria-label={t.systemTitle}
        >
          <ul className="flex flex-col gap-2.5">
            <li className="flex items-center justify-between rounded-control border border-[var(--border-subtle)] px-3.5 py-3">
              <span className="text-small font-semibold">{t.healthBackend}</span>
              <Badge tone={healthOk ? "success" : "critical"} dot>
                {healthOk ? t.healthUp : t.healthDown}
              </Badge>
            </li>
            <li className="flex items-center justify-between rounded-control border border-[var(--border-subtle)] px-3.5 py-3">
              <span className="text-small font-semibold">{t.healthData}</span>
              <Badge tone={networkSource === "api" ? "success" : "warning"} dot>
                {networkSource === "api" ? t.sourceApi : t.sourceDemo}
              </Badge>
            </li>
          </ul>
          {briefing && (
            <div className="mt-3 rounded-control bg-[var(--surface-chip)] p-3">
              <p className="flex items-center gap-2 text-small font-bold"><IconSpark className="h-4 w-4 text-brand-green" aria-hidden="true" />{t.aiTitle}</p>
              <p className="mt-1 text-caption leading-relaxed text-text-secondary">{briefing.recommendations[0] ? pickName(briefing.recommendations[0], lang) : briefing.posture}</p>
            </div>
          )}
          <p className="mt-3 text-caption leading-relaxed text-text-secondary">{t.systemFootnote}</p>
        </Card>
      </div>
    </div>
  );
}
