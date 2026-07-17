"use client";

/**
 * Segmented-переключатель языков (tg/ru/en). Активный — белая пилюля.
 *
 * Доступность:
 *  - обёртка <nav aria-label> + role="group" — блок находится навигацией
 *    скринридера и озвучивается как единая группа;
 *  - aria-pressed на каждой кнопке — выбранный язык слышен, а не только виден
 *    (цвет не единственный носитель смысла, WCAG 2.2 SC 1.4.1);
 *  - lang={code} на кнопке — синтезатор произносит «Тоҷикӣ» на таджикском,
 *    а не читает его по правилам языка страницы;
 *  - aria-label/title дают полное название языка, тогда как видимая подпись
 *    сокращена до двух букв.
 *
 * Рассчитан на ТЁМНУЮ подложку (navy-шапка портала, navy-сайдбар консоли):
 * белый на navy ≈15:1. На светлом фоне использовать нельзя.
 */

import { LANG_LABELS, LANG_SHORT_LABELS, LANGS } from "@/lib/i18n";
import { useI18n } from "./I18nProvider";

export default function LangSwitcher({ className }: { className?: string }) {
  const { lang, setLang, dict } = useI18n();

  return (
    <nav
      aria-label={dict.languageSwitcher}
      className={`shrink-0 ${className ?? ""}`}
    >
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
  );
}
