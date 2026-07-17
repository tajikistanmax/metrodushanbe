"use client";

/**
 * Топбар консоли: заголовок текущего раздела + дата, справа — быстрый
 * переход по разделам (поиск), бейдж демо-контура и профиль оператора
 * с выходом (Server Action logout). Полупрозрачный фон с blur — топбар
 * прилипает к верху при прокрутке.
 */

import { usePathname, useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import type { AdminRole } from "@/lib/auth";
import { logout } from "@/lib/auth-actions";
import { formatConsoleDate } from "@/lib/i18n";
import { IconLogout, IconSearch, IconUser } from "@/lib/icons";
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
  | "fares"
  | "imports"
  | "calendar"
  | "features"
  | "agents"
  | "users"
  | "audit";

const ROUTES: { key: NavKey; href: string }[] = [
  { key: "overview", href: "/" },
  { key: "map", href: "/map" },
  { key: "analytics", href: "/analytics" },
  { key: "lines", href: "/lines" },
  { key: "stations", href: "/stations" },
  { key: "alerts", href: "/alerts" },
  { key: "news", href: "/news" },
  { key: "requests", href: "/requests" },
  { key: "fares", href: "/fares" },
  { key: "imports", href: "/imports" },
  { key: "calendar", href: "/calendar" },
  { key: "features", href: "/features" },
  { key: "agents", href: "/agents" },
  { key: "users", href: "/users" },
  { key: "audit", href: "/audit" },
];

function currentKey(pathname: string): NavKey {
  const found = ROUTES.filter((r) => r.href !== "/").find(
    (r) => pathname === r.href || pathname.startsWith(`${r.href}/`),
  );
  return found?.key ?? "overview";
}

type TopbarProps = {
  /** Отображаемое имя оператора из claims сессии. */
  displayName: string;
  role: AdminRole;
};

export default function Topbar({ displayName, role }: TopbarProps) {
  const { lang, dict } = useI18n();
  const pathname = usePathname();
  const router = useRouter();
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const searchRef = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  // Сегодняшняя дата в локали интерфейса. При смене языка пересчитываем
  // значение без дополнительного render-pass; возможное отличие часового
  // пояса сервера от браузера подавляется на самом <time> ниже.
  const today = useMemo(() => {
    const now = new Date();
    return {
      dateTime: now.toISOString(),
      label: formatConsoleDate(now, lang),
    };
  }, [lang]);

  // Подсказки: разделы, чьё название содержит запрос
  const suggestions = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) {
      return ROUTES;
    }
    return ROUTES.filter((r) => dict.nav[r.key].toLowerCase().includes(q));
  }, [query, dict]);

  // Закрытие по клику вне
  useEffect(() => {
    function onPointerDown(e: PointerEvent) {
      if (searchRef.current && !searchRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    }
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, []);

  function go(href: string) {
    setOpen(false);
    setQuery("");
    router.push(href);
  }

  return (
    <header className="sticky top-0 z-30 border-b border-card-border bg-[var(--topbar-bg)] backdrop-blur-md">
      <div className="flex flex-wrap items-center gap-3 px-4 py-3 sm:px-6 lg:px-8">
        {/* Раздел + дата (не <h1>: заголовок уровня страницы задаёт контент) */}
        <div className="min-w-0 flex-1">
          <p className="truncate text-lg font-extrabold leading-tight">
            {dict.nav[currentKey(pathname)]}
          </p>
          <time
            dateTime={today.dateTime}
            suppressHydrationWarning
            className="block min-h-4 truncate text-xs text-text-secondary"
          >
            {today.label}
          </time>
        </div>

        {/* Быстрый переход */}
        <div ref={searchRef} className="relative order-3 w-full sm:order-none sm:w-64">
          <IconSearch className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-secondary" />
          <input
            type="search"
            role="combobox"
            aria-expanded={open}
            aria-controls="topbar-search-list"
            aria-label={dict.topbar.searchLabel}
            placeholder={dict.topbar.searchPlaceholder}
            value={query}
            onFocus={() => setOpen(true)}
            onChange={(e) => {
              setQuery(e.target.value);
              setOpen(true);
            }}
            onKeyDown={(e) => {
              if (e.key === "Enter" && suggestions[0]) {
                go(suggestions[0].href);
              }
              if (e.key === "Escape") {
                setOpen(false);
              }
            }}
            className="w-full rounded-full border border-card-border bg-card py-2 pl-9 pr-4 text-sm font-medium outline-none transition-colors placeholder:text-text-secondary/70 focus:border-info"
          />
          {open && suggestions.length > 0 && (
            <ul
              id="topbar-search-list"
              role="listbox"
              className="console-card absolute left-0 right-0 top-full z-40 mt-2 overflow-hidden py-1.5"
            >
              {suggestions.map((r) => (
                <li key={r.key} role="option" aria-selected={false}>
                  <button
                    type="button"
                    onClick={() => go(r.href)}
                    className="w-full px-4 py-2 text-left text-sm font-medium transition-colors hover:bg-[var(--table-row-hover)]"
                  >
                    {dict.nav[r.key]}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* Бейдж демо-контура */}
        <span
          className="hidden rounded-full bg-warning/15 px-3 py-1 text-[11px] font-bold uppercase tracking-wide text-warning md:inline-block"
          title={dict.topbar.demoHint}
        >
          {dict.topbar.demoBadge}
        </span>

        {/* Профиль */}
        <div ref={menuRef} className="relative">
          <button
            type="button"
            onClick={() => setMenuOpen((v) => !v)}
            aria-expanded={menuOpen}
            aria-haspopup="menu"
            className="flex items-center gap-2.5 rounded-full border border-card-border bg-card py-1.5 pl-1.5 pr-3 transition-colors hover:border-info"
          >
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-brand-navy text-surface-light">
              <IconUser className="h-[18px] w-[18px]" />
            </span>
            <span className="hidden text-left sm:block">
              <span className="block text-xs font-bold leading-tight">
                {displayName}
              </span>
              <span className="block text-[10px] leading-tight text-text-secondary">
                {dict.roles[role]}
              </span>
            </span>
          </button>
          {menuOpen && (
            <div
              role="menu"
              className="console-card absolute right-0 top-full z-40 mt-2 w-52 overflow-hidden py-1.5"
            >
              <form action={logout} role="none">
                <button
                  type="submit"
                  role="menuitem"
                  className="flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-sm font-semibold text-brand-red transition-colors hover:bg-[var(--table-row-hover)]"
                >
                  <IconLogout className="h-4 w-4" />
                  {dict.topbar.logout}
                </button>
              </form>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
