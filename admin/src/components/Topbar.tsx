"use client";

/**
 * Топбар консоли: заголовок текущего раздела + дата, справа — быстрый
 * переход по разделам (поиск), бейдж демо-контура и профиль оператора
 * с выходом (Server Action logout). Топбар прилипает к верху при прокрутке и
 * отделяется от контента границей и непрозрачной плоскостью, а не размытием
 * (backdrop-blur снят — см. --topbar-bg в packages/design/tokens.mjs).
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
  | "incidents"
  | "fares"
  | "notifications"
  | "tickets"
  | "webhooks"
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
  { key: "incidents", href: "/incidents" },
  { key: "fares", href: "/fares" },
  { key: "notifications", href: "/notifications" },
  { key: "tickets", href: "/tickets" },
  { key: "webhooks", href: "/webhooks" },
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
    <header className="sticky top-0 z-30 border-b border-[var(--border-subtle)] bg-[var(--topbar-bg)]">
      <div className="flex flex-wrap items-center gap-3 px-4 py-3 sm:px-6 lg:px-8">
        {/* Раздел + дата (не <h1>: заголовок уровня страницы задаёт контент) */}
        <div className="min-w-0 flex-1">
          <p className="truncate text-title-s font-bold leading-tight">
            {dict.nav[currentKey(pathname)]}
          </p>
          <time
            dateTime={today.dateTime}
            suppressHydrationWarning
            className="block min-h-4 truncate text-caption text-text-secondary"
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
            // font-medium (500) не загружен — браузер синтезировал начертание;
            // поле ввода — обычный текст, поэтому 400.
            className="w-full rounded-control border border-[var(--border-strong)] bg-[var(--surface-raised)] py-2 pl-9 pr-4 text-small outline-none transition-colors placeholder:text-text-secondary focus:border-info"
          />
          {open && suggestions.length > 0 && (
            // Не <Card>: listbox/option — правильная семантика именно на ul/li,
            // а Card не пробрасывает role. Поверхность собрана теми же токенами.
            // Поповер реально висит над контентом — законный случай тени
            // (--elevation-overlay), см. tokens.mjs.
            <ul
              id="topbar-search-list"
              role="listbox"
              className="absolute left-0 right-0 top-full z-40 mt-2 overflow-hidden rounded-panel border border-[var(--border-subtle)] bg-[var(--surface-overlay)] py-1.5 shadow-overlay"
            >
              {suggestions.map((r) => (
                <li key={r.key} role="option" aria-selected={false}>
                  <button
                    type="button"
                    onClick={() => go(r.href)}
                    className="w-full px-4 py-2 text-left text-small font-semibold transition-colors hover:bg-[var(--surface-hover-subtle)]"
                  >
                    {dict.nav[r.key]}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* Бейдж демо-контура: тон продублирован словом, не только цветом */}
        <span
          className="hidden rounded-chip bg-[var(--tint-warning)] px-3 py-1 text-caption font-bold uppercase tracking-[0.08em] text-[var(--text-primary)] md:inline-block"
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
            className="flex items-center gap-2.5 rounded-control border border-[var(--border-strong)] bg-[var(--surface-raised)] py-1.5 pl-1.5 pr-3 transition-colors hover:bg-[var(--surface-hover)]"
          >
            {/* rounded-full здесь законен: это настоящая окружность-аватар */}
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-brand-navy text-surface-light">
              <IconUser className="h-[18px] w-[18px]" />
            </span>
            <span className="hidden text-left sm:block">
              <span className="block text-small font-bold leading-tight">
                {displayName}
              </span>
              <span className="block text-caption leading-tight text-text-secondary">
                {dict.roles[role]}
              </span>
            </span>
          </button>
          {menuOpen && (
            <div
              role="menu"
              className="absolute right-0 top-full z-40 mt-2 w-52 overflow-hidden rounded-panel border border-[var(--border-subtle)] bg-[var(--surface-overlay)] py-1.5 shadow-overlay"
            >
              <form action={logout} role="none">
                {/* Выход — разрушительное действие: смысл несёт глагол в
                    подписи, красный лишь дублирует его (SC 1.4.1). */}
                <button
                  type="submit"
                  role="menuitem"
                  className="flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-small font-semibold text-brand-red transition-colors hover:bg-[var(--surface-hover-subtle)]"
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
