"use client";

/**
 * Боковая навигация консоли (фон --brand-navy): марка + словесный знак,
 * разделы, сгруппированные по доменам (сеть / контент / система), внизу —
 * переключатели темы и языка и версия контура. Активный раздел — по
 * usePathname. На узких экранах сайдбар становится верхней панелью.
 */

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ComponentType, SVGProps } from "react";
import { roleAtLeast, type AdminRole } from "@/lib/auth";
import { LANG_LABELS, LANG_SHORT_LABELS, LANGS } from "@/lib/i18n";
import {
  IconAudit,
  IconBell,
  IconBolt,
  IconCalendar,
  IconChart,
  IconHome,
  IconImport,
  IconLines,
  IconMap,
  IconNews,
  IconRequests,
  IconSpark,
  IconStation,
  IconTicket,
  IconUsers,
  IconWarning,
} from "@/lib/icons";
import BrandMark from "./BrandMark";
import ThemeToggle from "./ThemeToggle";
import { useI18n } from "./I18nProvider";

type NavKey =
  | "overview"
  | "map"
  | "analytics"
  | "lines"
  | "stations"
  | "alerts"
  | "news"
  | "requests"
  | "incidents"
  | "fares"
  | "imports"
  | "calendar"
  | "features"
  | "agents"
  | "users"
  | "audit";

type NavItem = {
  key: NavKey;
  href: string;
  icon: ComponentType<SVGProps<SVGSVGElement>>;
  /** Минимальная роль для показа пункта; не задана — виден всем операторам. */
  minRole?: AdminRole;
};

type NavGroup = {
  /** null — группа без заголовка (верхний блок). */
  labelKey: "network" | "content" | "system" | null;
  items: NavItem[];
};

const GROUPS: NavGroup[] = [
  {
    labelKey: null,
    items: [
      { key: "overview", href: "/", icon: IconHome },
      { key: "map", href: "/map", icon: IconMap },
      { key: "analytics", href: "/analytics", icon: IconChart },
    ],
  },
  {
    labelKey: "network",
    items: [
      { key: "lines", href: "/lines", icon: IconLines },
      { key: "stations", href: "/stations", icon: IconStation },
      { key: "imports", href: "/imports", icon: IconImport },
      { key: "calendar", href: "/calendar", icon: IconCalendar },
    ],
  },
  {
    labelKey: "content",
    items: [
      { key: "alerts", href: "/alerts", icon: IconBell },
      { key: "news", href: "/news", icon: IconNews },
      { key: "requests", href: "/requests", icon: IconRequests },
      { key: "incidents", href: "/incidents", icon: IconWarning },
      { key: "fares", href: "/fares", icon: IconTicket },
    ],
  },
  {
    labelKey: "system",
    items: [
      { key: "agents", href: "/agents", icon: IconSpark },
      { key: "features", href: "/features", icon: IconBolt },
      { key: "users", href: "/users", icon: IconUsers, minRole: "superadmin" },
      { key: "audit", href: "/audit", icon: IconAudit },
    ],
  },
];

function isActive(pathname: string, href: string): boolean {
  if (href === "/") {
    return pathname === "/";
  }
  return pathname === href || pathname.startsWith(`${href}/`);
}

export default function Sidebar({ role }: { role: AdminRole }) {
  const { lang, setLang, dict } = useI18n();
  const pathname = usePathname();

  // Скрытие пункта — только удобство: недоступный раздел всё равно закрыт
  // серверным guard'ом (requireAdminRole), прямой переход по URL не поможет.
  const groups = GROUPS.map((group) => ({
    ...group,
    items: group.items.filter(
      (item) => !item.minRole || roleAtLeast(role, item.minRole),
    ),
  })).filter((group) => group.items.length > 0);

  return (
    <aside className="sidebar flex shrink-0 flex-col gap-5 bg-brand-navy p-4 text-surface-light lg:sticky lg:top-0 lg:h-dvh lg:w-[264px] lg:overflow-y-auto">
      {/* Бренд */}
      <Link href="/" className="flex items-center gap-2.5 rounded-lg">
        <BrandMark className="h-10 w-[43px] shrink-0" holeColor="var(--brand-navy)" />
        <span className="min-w-0">
          <span className="block truncate text-[15px] font-extrabold uppercase leading-tight tracking-wide">
            {dict.appSubtitle}
          </span>
          <span className="block truncate text-[11px] font-medium text-surface-light/65">
            {dict.appTitle}
          </span>
        </span>
      </Link>

      {/* Навигация */}
      <nav aria-label={dict.appTitle} className="flex-1">
        <div className="flex gap-4 overflow-x-auto lg:flex-col lg:gap-5 lg:overflow-visible">
          {groups.map((group, gi) => (
            <div key={gi} className="shrink-0">
              {group.labelKey && (
                <p className="mb-1.5 hidden px-3 text-[10px] font-bold uppercase tracking-[0.18em] text-surface-light/45 lg:block">
                  {dict.navGroups[group.labelKey]}
                </p>
              )}
              <ul className="flex gap-1 lg:flex-col">
                {group.items.map(({ key, href, icon: Icon }) => {
                  const active = isActive(pathname, href);
                  return (
                    <li key={key} className="shrink-0">
                      <Link
                        href={href}
                        aria-current={active ? "page" : undefined}
                        className={
                          active
                            ? "flex items-center gap-2.5 whitespace-nowrap rounded-xl bg-[var(--sidebar-active-bg)] px-3 py-2.5 text-sm font-bold text-[var(--sidebar-active-text)] shadow-[0_4px_14px_rgba(0,0,0,0.25)]"
                            : "flex items-center gap-2.5 whitespace-nowrap rounded-xl px-3 py-2.5 text-sm font-semibold text-surface-light/80 transition-colors hover:bg-surface-light/10 hover:text-surface-light"
                        }
                      >
                        <Icon className="h-[18px] w-[18px] shrink-0" />
                        {dict.nav[key]}
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </div>
      </nav>

      {/* Нижний блок: тема + языки + версия */}
      <div className="flex flex-col gap-3">
        <div className="flex items-center justify-between gap-2">
          <ThemeToggle />
          <nav aria-label={dict.languageSwitcher} className="shrink-0">
            <div
              role="group"
              aria-label={dict.languageSwitcher}
              className="flex gap-0.5 rounded-full bg-surface-dark/60 p-1"
            >
              {LANGS.map((code) => (
                <button
                  key={code}
                  type="button"
                  lang={code}
                  aria-pressed={lang === code}
                  aria-label={LANG_LABELS[code]}
                  title={LANG_LABELS[code]}
                  onClick={() => setLang(code)}
                  className={
                    lang === code
                      ? "rounded-full bg-surface-light px-2.5 py-1 text-xs font-bold text-brand-navy"
                      : "rounded-full px-2.5 py-1 text-xs font-semibold text-surface-light transition-colors hover:bg-surface-light/15"
                  }
                >
                  {LANG_SHORT_LABELS[code]}
                </button>
              ))}
            </div>
          </nav>
        </div>
        <p className="hidden px-1 text-[10px] font-medium text-surface-light/40 lg:block">
          {dict.sidebarFootnote}
        </p>
      </div>
    </aside>
  );
}
