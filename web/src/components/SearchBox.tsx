"use client";

/**
 * Поле поиска станций в плавающей панели: фильтр по названию
 * на любом из трёх языков. Иконка-лупа — inline SVG.
 */

import { useI18n } from "./I18nProvider";

type SearchBoxProps = {
  value: string;
  onChange: (value: string) => void;
};

export default function SearchBox({ value, onChange }: SearchBoxProps) {
  const { dict } = useI18n();

  return (
    <div className="relative">
      <svg
        aria-hidden="true"
        focusable="false"
        width={16}
        height={16}
        viewBox="0 0 16 16"
        fill="none"
        stroke="currentColor"
        strokeWidth={1.8}
        strokeLinecap="round"
        className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-text-secondary"
      >
        <circle cx="7" cy="7" r="4.6" />
        <path d="M10.6 10.6 14 14" />
      </svg>
      <input
        type="search"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-label={dict.searchLabel}
        placeholder={dict.searchPlaceholder}
        autoComplete="off"
        // placeholder:font-medium снят: вес 500 не загружен через
        // @fontsource/montserrat — браузер его синтезировал. Плейсхолдер
        // отличается от введённого текста цветом, а не мнимым весом.
        className="h-10 w-full rounded-control border border-[var(--border-subtle)] bg-[var(--surface-raised)] pl-9 pr-3 text-small text-[var(--text-primary)] placeholder:text-text-secondary"
      />
    </div>
  );
}
