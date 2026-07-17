"use client";

/**
 * Док мобильности — панель поверх карты: назначение страницы, счётчики сети и
 * переходы к маршруту, тарифам и обращениям.
 *
 * Здесь живёт <h1> главной страницы. Карта — холст, у неё нет заголовка, а
 * страница без h1 не имеет названия ни для скринридера, ни для поиска. Прежний
 * <h1> портала сидел в шапке (15px, скрыт до xl) и повторялся на всех экранах;
 * теперь заголовок принадлежит странице и назван её словами.
 *
 * СНЯТО В РЕДИЗАЙНЕ (было декором, воюющим с институциональным тоном):
 *  - радиальное зелёное свечение 180px (.mobility-dock::before);
 *  - въезд панели 420ms (@keyframes mobility-dock-rise);
 *  - градиент 115° на главной кнопке (.mobility-primary);
 *  - «подпрыгивание» ссылки при наведении (hover:-translate-y-0.5);
 *  - тени 0 8px 20px и радиус 22px.
 * Панель отделена от карты границей и фоном --surface-glass, а не размытием.
 */

import Link from "next/link";
import type { ReactNode } from "react";
import { isLineFeature, isStationFeature, type NetworkGeoJson } from "@/lib/types";
import { useI18n } from "./I18nProvider";

type MobilityDockProps = {
  data: NetworkGeoJson | null;
  alertsCount: number;
  /** Включён ли режим построения маршрута нажатиями по карте. */
  routeMode: boolean;
  /** Переключение режима маршрута (главное действие дока). */
  onToggleRouteMode: () => void;
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

/** Счётчик сети: число и подпись. tabular-nums — чтобы цифры не «плясали». */
function Stat({ value, label }: { value: number; label: string }) {
  return (
    <div className="rounded-control bg-[var(--surface-hover)] px-2.5 py-2">
      <dd className="text-title-s font-bold leading-none tabular-nums">{value}</dd>
      <dt className="mt-1 truncate text-caption font-semibold text-text-secondary">
        {label}
      </dt>
    </div>
  );
}

/** Вторичное действие дока: квадратная кнопка-ссылка с иконкой. */
function DockAction({
  href,
  label,
  children,
}: {
  href: string;
  label: string;
  children: ReactNode;
}) {
  return (
    <Link
      href={href}
      aria-label={label}
      title={label}
      className="flex h-10 w-10 shrink-0 items-center justify-center rounded-control border border-[var(--border-subtle)] bg-[var(--surface-raised)] text-[var(--text-primary)] transition-colors duration-150 ease-out hover:bg-[var(--surface-hover)]"
    >
      {children}
    </Link>
  );
}

export default function MobilityDock({
  data,
  alertsCount,
  routeMode,
  onToggleRouteMode,
}: MobilityDockProps) {
  const { dict } = useI18n();
  const lineCount = data?.features.filter(isLineFeature).length ?? 0;
  const stationCount = data?.features.filter(isStationFeature).length ?? 0;

  return (
    <section
      aria-label={dict.mobility.title}
      className="pointer-events-auto rounded-panel border border-[var(--border-subtle)] bg-[var(--surface-glass)] p-3 text-[var(--text-primary)] backdrop-blur-xl sm:p-4"
    >
      <div className="hidden items-center justify-between gap-3 sm:flex">
        <p className="flex min-w-0 items-center gap-2 text-caption font-semibold uppercase tracking-[0.08em] text-text-secondary">
          {/* Точка — вспомогательный сигнал; смысл несёт подпись рядом */}
          <span className="h-2 w-2 shrink-0 rounded-full bg-brand-green" aria-hidden="true" />
          <span className="truncate">{dict.mobility.kicker}</span>
        </p>
        <span className="rounded-chip border border-[var(--border-subtle)] bg-[var(--surface-raised)] px-2 py-1 text-caption font-semibold text-text-secondary">
          {dict.mobility.network}
        </span>
      </div>

      <h1 className="text-body font-bold leading-tight sm:mt-2 sm:text-title-s">
        {dict.mobility.title}
      </h1>
      <p className="mt-1 hidden text-small leading-relaxed text-text-secondary sm:block">
        {dict.mobility.hint}
      </p>

      <dl className="mt-3 hidden grid-cols-3 gap-2 sm:grid">
        <Stat value={lineCount} label={dict.mobility.lines} />
        <Stat value={stationCount} label={dict.mobility.stations} />
        <Stat value={alertsCount} label={dict.mobility.alerts} />
      </dl>

      <div className="mt-2 flex gap-2 sm:mt-3">
        {/* Главное действие дока — включение режима маршрута ПРЯМО НА КАРТЕ:
            это действие на текущей странице, а не переход, поэтому кнопка, а не
            ссылка. Планировщик со списками никуда не делся: он в шапке портала
            и в панели режима («Открыть в планировщике» c ?from=&to=).
            Вид совпадает с Button variant="primary" (navy, 4px, без тени). */}
        <button
          type="button"
          onClick={onToggleRouteMode}
          aria-pressed={routeMode}
          className="flex h-10 min-w-0 flex-1 items-center justify-center gap-2 rounded-control bg-brand-navy px-3 text-small font-bold text-surface-light transition-colors duration-150 ease-out hover:bg-brand-navy/90 focus-visible:outline-[var(--focus-ring-on-dark)]"
        >
          <RouteIcon />
          <span className="truncate">
            {routeMode ? dict.route.map.toggleOff : dict.route.map.toggleOn}
          </span>
        </button>
        <DockAction href="/fares" label={dict.fares.nav}>
          <TicketIcon />
        </DockAction>
        <DockAction href="/requests" label={dict.requests.nav}>
          <MessageIcon />
        </DockAction>
      </div>
    </section>
  );
}
