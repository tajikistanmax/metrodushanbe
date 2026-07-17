"use client";

import Link from "next/link";
import { isLineFeature, isStationFeature, type NetworkGeoJson } from "@/lib/types";
import { useI18n } from "./I18nProvider";

type MobilityDockProps = {
  data: NetworkGeoJson | null;
  alertsCount: number;
};

function RouteIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="6" cy="6" r="2.5" />
      <circle cx="18" cy="18" r="2.5" />
      <path d="M8.5 6h3a3 3 0 0 1 3 3v0a3 3 0 0 1-3 3h-1a3 3 0 0 0-3 3v0a3 3 0 0 0 3 3h5" />
    </svg>
  );
}

function TicketIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M4 7.5A1.5 1.5 0 0 1 5.5 6h13A1.5 1.5 0 0 1 20 7.5v2a2.5 2.5 0 0 0 0 5v2A1.5 1.5 0 0 1 18.5 18h-13A1.5 1.5 0 0 1 4 16.5v-2a2.5 2.5 0 0 0 0-5Z" />
      <path d="M12 8.5v7" strokeDasharray="2.5 2.5" />
    </svg>
  );
}

function MessageIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M5 5h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H9l-5 3v-3a2 2 0 0 1-1-1.73V7a2 2 0 0 1 2-2Z" />
      <path d="M8 10h8M8 13h5" />
    </svg>
  );
}

export default function MobilityDock({ data, alertsCount }: MobilityDockProps) {
  const { dict } = useI18n();
  const lineCount = data?.features.filter(isLineFeature).length ?? 0;
  const stationCount = data?.features.filter(isStationFeature).length ?? 0;

  return (
    <section
      aria-label={dict.mobility.title}
      className="mobility-dock absolute left-3 right-14 top-3 z-10 rounded-[22px] border border-[var(--panel-border)] p-3 text-[var(--text-primary)] shadow-[var(--shadow-card)] backdrop-blur-xl sm:left-auto sm:right-14 sm:top-4 sm:w-[330px] sm:p-4"
    >
      <div className="hidden items-center justify-between gap-3 sm:flex">
        <p className="flex min-w-0 items-center gap-2 text-[10px] font-extrabold uppercase tracking-[0.14em] text-text-secondary">
          <span className="h-2 w-2 shrink-0 rounded-full bg-brand-green shadow-[0_0_0_5px_rgba(19,138,61,0.12)]" aria-hidden="true" />
          <span className="truncate">{dict.mobility.kicker}</span>
        </p>
        <span className="rounded-full border border-[var(--panel-border)] bg-[var(--field-bg)] px-2 py-1 text-[10px] font-bold text-text-secondary">
          {dict.mobility.network}
        </span>
      </div>

      <h2 className="text-sm font-extrabold leading-tight sm:mt-2 sm:text-lg">
        {dict.mobility.title}
      </h2>
      <p className="mt-1 hidden text-xs leading-relaxed text-text-secondary sm:block">
        {dict.mobility.hint}
      </p>

      <dl className="mt-3 hidden grid-cols-3 gap-2 sm:grid">
        <div className="rounded-xl bg-[var(--control-hover)] px-2.5 py-2">
          <dd className="text-lg font-extrabold leading-none tabular-nums">{lineCount}</dd>
          <dt className="mt-1 truncate text-[10px] font-semibold text-text-secondary">{dict.mobility.lines}</dt>
        </div>
        <div className="rounded-xl bg-[var(--control-hover)] px-2.5 py-2">
          <dd className="text-lg font-extrabold leading-none tabular-nums">{stationCount}</dd>
          <dt className="mt-1 truncate text-[10px] font-semibold text-text-secondary">{dict.mobility.stations}</dt>
        </div>
        <div className="rounded-xl bg-[var(--control-hover)] px-2.5 py-2">
          <dd className="text-lg font-extrabold leading-none tabular-nums">{alertsCount}</dd>
          <dt className="mt-1 truncate text-[10px] font-semibold text-text-secondary">{dict.mobility.alerts}</dt>
        </div>
      </dl>

      <div className="mt-2 flex gap-2 sm:mt-3">
        <Link
          href="/route"
          className="mobility-primary flex min-w-0 flex-1 items-center justify-center gap-2 rounded-xl bg-brand-navy px-3 py-2.5 text-xs font-extrabold text-surface-light shadow-[0_8px_20px_rgba(8,39,66,0.22)] transition-transform hover:-translate-y-0.5"
        >
          <RouteIcon />
          <span className="truncate">{dict.route.submit}</span>
        </Link>
        <Link
          href="/fares"
          aria-label={dict.fares.nav}
          title={dict.fares.nav}
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-[var(--panel-border)] bg-[var(--field-bg)] text-[var(--text-primary)] transition-colors hover:bg-[var(--control-hover)]"
        >
          <TicketIcon />
        </Link>
        <Link
          href="/requests"
          aria-label={dict.requests.nav}
          title={dict.requests.nav}
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-[var(--panel-border)] bg-[var(--field-bg)] text-[var(--text-primary)] transition-colors hover:bg-[var(--control-hover)]"
        >
          <MessageIcon />
        </Link>
      </div>
    </section>
  );
}
