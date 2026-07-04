"use client";

/**
 * Шапка портала (60px, фон --brand-navy): марка + словесный знак слева;
 * справа — пилюля источника данных, переключатель темы и segmented-
 * переключатель языков. Контраст белого на navy ≈15:1 (WCAG AA).
 */

import { LANG_LABELS, LANG_SHORT_LABELS, LANGS } from "@/lib/i18n";
import type { DataSource } from "@/lib/types";
import BrandMark from "./BrandMark";
import ThemeToggle from "./ThemeToggle";
import { useI18n } from "./I18nProvider";

type HeaderProps = {
  /** Фактический источник данных; null — ещё загружается. */
  source: DataSource | null;
};

/** Цвет точки-индикатора источника (контраст к navy ≥3:1). */
const SOURCE_DOT: Record<"api" | "demo" | "loading", string> = {
  api: "#4ade80",
  demo: "var(--warning)",
  loading: "#9fb3c8",
};

export default function Header({ source }: HeaderProps) {
  const { lang, setLang, dict } = useI18n();

  const sourceKey = source ?? "loading";
  const sourceShort =
    source === "api"
      ? dict.dataSourceApi
      : source === "demo"
        ? dict.dataSourceDemoShort
        : dict.loading;
  const sourceLong =
    source === "api"
      ? dict.dataSourceApi
      : source === "demo"
        ? dict.dataSourceDemo
        : dict.loading;

  return (
    <header className="z-20 flex h-[60px] shrink-0 items-center gap-2 bg-brand-navy px-3 text-surface-light sm:gap-3 sm:px-4">
      <div className="flex min-w-0 items-center gap-2.5">
        <BrandMark
          className="h-9 w-[39px] shrink-0"
          holeColor="var(--brand-navy)"
        />
        <h1 className="truncate text-[15px] font-bold tracking-wide sm:text-base">
          {dict.appTitle}
        </h1>
      </div>

      <div className="ml-auto flex shrink-0 items-center gap-1.5 sm:gap-2.5">
        {/* Пилюля источника данных: API / демо, с точкой-индикатором */}
        <span
          role="status"
          title={`${dict.dataSourceLabel}: ${sourceLong}`}
          className="flex h-8 items-center gap-1.5 rounded-full bg-surface-light/10 px-2.5 text-xs font-semibold"
        >
          <span
            aria-hidden="true"
            className="h-2 w-2 shrink-0 rounded-full"
            style={{ background: SOURCE_DOT[sourceKey] }}
          />
          <span className="hidden sm:inline">{sourceShort}</span>
          <span className="sr-only">
            {dict.dataSourceLabel}: {sourceLong}
          </span>
        </span>

        <ThemeToggle />

        {/* Segmented-переключатель языков: активный — белая пилюля */}
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
                    : "rounded-full px-2.5 py-1 text-xs font-semibold text-surface-light transition-colors duration-150 ease-out hover:bg-surface-light/15"
                }
              >
                {LANG_SHORT_LABELS[code]}
              </button>
            ))}
          </div>
        </nav>
      </div>
    </header>
  );
}
