"use client";

/**
 * Переключатель темы в шапке: одна кнопка, три состояния по кругу
 * auto → light → dark → auto. Иконки — inline SVG (без внешних ресурсов).
 */

import { useTheme, type ThemeMode } from "./ThemeProvider";
import { useI18n } from "./I18nProvider";

const NEXT_MODE: Record<ThemeMode, ThemeMode> = {
  auto: "light",
  light: "dark",
  dark: "auto",
};

function ModeIcon({ mode }: { mode: ThemeMode }) {
  const common = {
    width: 18,
    height: 18,
    viewBox: "0 0 20 20",
    "aria-hidden": true as const,
    focusable: false as const,
  };
  if (mode === "light") {
    // Солнце
    return (
      <svg {...common} fill="none" stroke="currentColor" strokeWidth={1.8}>
        <circle cx="10" cy="10" r="3.6" />
        <path
          strokeLinecap="round"
          d="M10 1.8v2.1M10 16.1v2.1M1.8 10h2.1M16.1 10h2.1M4.2 4.2l1.5 1.5M14.3 14.3l1.5 1.5M15.8 4.2l-1.5 1.5M5.7 14.3l-1.5 1.5"
        />
      </svg>
    );
  }
  if (mode === "dark") {
    // Луна
    return (
      <svg {...common} fill="currentColor">
        <path d="M16.2 12.6A7 7 0 0 1 7.4 3.8a.5.5 0 0 0-.66-.62 7.6 7.6 0 1 0 10.08 10.08.5.5 0 0 0-.62-.66Z" />
      </svg>
    );
  }
  // Auto: полузакрашенный круг (следует системной теме)
  return (
    <svg {...common}>
      <circle
        cx="10"
        cy="10"
        r="7"
        fill="none"
        stroke="currentColor"
        strokeWidth={1.8}
      />
      <path d="M10 3a7 7 0 0 1 0 14Z" fill="currentColor" />
    </svg>
  );
}

export default function ThemeToggle() {
  const { mode, setMode } = useTheme();
  const { dict } = useI18n();

  const modeLabel =
    mode === "auto"
      ? dict.themeAuto
      : mode === "light"
        ? dict.themeLight
        : dict.themeDark;
  const label = `${dict.themeLabel}: ${modeLabel}`;

  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onClick={() => setMode(NEXT_MODE[mode])}
      className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-surface-light transition-colors duration-150 ease-out hover:bg-surface-light/15"
    >
      <ModeIcon mode={mode} />
    </button>
  );
}
