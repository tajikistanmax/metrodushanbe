"use client";

/**
 * Боковая навигация консоли (фон --brand-navy): марка + словесный знак сверху,
 * список разделов, а внизу — бейдж «только чтение», переключатель темы и
 * segmented-переключатель языков. Активный раздел определяется по usePathname.
 * На узких экранах сайдбар становится верхней панелью (см. layout.tsx).
 */

import Link from "next/link";
import { usePathname } from "next/navigation";
import { LANG_LABELS, LANG_SHORT_LABELS, LANGS } from "@/lib/i18n";
import BrandMark from "./BrandMark";
import ThemeToggle from "./ThemeToggle";
import { useI18n } from "./I18nProvider";

type NavKey = "overview" | "lines" | "stations" | "alerts" | "news";

const NAV: { key: NavKey; href: string }[] = [
  { key: "overview", href: "/" },
  { key: "lines", href: "/lines" },
  { key: "stations", href: "/stations" },
  { key: "alerts", href: "/alerts" },
  { key: "news", href: "/news" },
];

function isActive(pathname: string, href: string): boolean {
  if (href === "/") {
    return pathname === "/";
  }
  return pathname === href || pathname.startsWith(`${href}/`);
}

export default function Sidebar() {
  const { lang, setLang, dict } = useI18n();
  const pathname = usePathname();

  return (
    <aside className="sidebar flex shrink-0 flex-col gap-4 bg-brand-navy p-4 text-surface-light lg:h-dvh lg:w-64 lg:overflow-y-auto">
      {/* Бренд */}
      <div className="flex items-center gap-2.5">
        <BrandMark
          className="h-9 w-[39px] shrink-0"
          holeColor="var(--brand-navy)"
        />
        <div className="min-w-0">
          <p className="truncate text-[15px] font-bold leading-tight tracking-wide">
            {dict.appTitle}
          </p>
          <p className="truncate text-xs text-surface-light/70">
            {dict.appSubtitle}
          </p>
        </div>
      </div>

      {/* Навигация */}
      <nav aria-label={dict.appTitle} className="flex-1">
        <ul className="flex gap-1 overflow-x-auto lg:flex-col lg:overflow-visible">
          {NAV.map(({ key, href }) => {
            const active = isActive(pathname, href);
            return (
              <li key={key} className="shrink-0">
                <Link
                  href={href}
                  aria-current={active ? "page" : undefined}
                  className={
                    active
                      ? "block whitespace-nowrap rounded-lg bg-surface-light px-3 py-2 text-sm font-bold text-brand-navy"
                      : "block whitespace-nowrap rounded-lg px-3 py-2 text-sm font-semibold text-surface-light/85 transition-colors hover:bg-surface-light/15"
                  }
                >
                  {dict.nav[key]}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>

      {/* Нижний блок: read-only + тема + языки */}
      <div className="flex flex-col gap-3">
        <span
          title={dict.readOnlyHint}
          className="inline-flex w-fit items-center gap-1.5 rounded-full bg-surface-light/10 px-2.5 py-1 text-xs font-semibold"
        >
          <span
            aria-hidden="true"
            className="h-2 w-2 shrink-0 rounded-full bg-[var(--warning)]"
          />
          {dict.readOnlyBadge}
        </span>

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
      </div>
    </aside>
  );
}
