"use client";

/**
 * Шапка портала: название по текущему языку + переключатель языков.
 * Контраст: белый текст на брендовом navy (#082742) — WCAG AA.
 */

import { LANG_LABELS, LANGS } from "@/lib/i18n";
import { useI18n } from "./I18nProvider";

export default function Header() {
  const { lang, setLang, dict } = useI18n();

  return (
    <header className="bg-brand-navy text-surface-light">
      <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-3 px-4 py-3 sm:px-6">
        <h1 className="text-lg font-bold tracking-wide sm:text-xl">
          {dict.appTitle}
        </h1>
        <nav aria-label={dict.languageSwitcher}>
          <div
            role="group"
            aria-label={dict.languageSwitcher}
            className="flex gap-1 rounded-lg bg-surface-dark/60 p-1"
          >
            {LANGS.map((code) => (
              <button
                key={code}
                type="button"
                lang={code}
                aria-pressed={lang === code}
                onClick={() => setLang(code)}
                className={
                  lang === code
                    ? "rounded-md bg-surface-light px-3 py-1.5 text-sm font-semibold text-brand-navy"
                    : "rounded-md px-3 py-1.5 text-sm font-medium text-surface-light hover:bg-surface-light/15"
                }
              >
                {LANG_LABELS[code]}
              </button>
            ))}
          </div>
        </nav>
      </div>
    </header>
  );
}
