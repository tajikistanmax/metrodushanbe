"use client";

/**
 * Тема оформления: auto (по умолчанию, следует prefers-color-scheme),
 * light, dark. Выбор хранится в localStorage; применяется через
 * атрибут data-theme="light|dark" на <html> (токены — в globals.css).
 * До гидратации атрибут выставляет инлайн-скрипт THEME_INIT_SCRIPT
 * (см. ./theme-init.ts), подключаемый в layout.tsx каждого приложения.
 *
 * Ключ localStorage общий для портала и консоли — выбор темы переносится
 * между ними в пределах одного origin.
 */

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useSyncExternalStore,
  type ReactNode,
} from "react";

export type ThemeMode = "auto" | "light" | "dark";
export type ResolvedTheme = "light" | "dark";

/** Ключ в localStorage (дублируется в THEME_INIT_SCRIPT — менять синхронно). */
export const THEME_STORAGE_KEY = "metro-dushanbe.theme";

function isThemeMode(value: unknown): value is ThemeMode {
  return value === "auto" || value === "light" || value === "dark";
}

// --- Внешнее хранилище режима (localStorage + in-memory fallback) ---

let memoryMode: ThemeMode | null = null;

const listeners = new Set<() => void>();

function emitChange(): void {
  listeners.forEach((listener) => listener());
}

function subscribeMode(listener: () => void): () => void {
  listeners.add(listener);
  const onStorage = (event: StorageEvent) => {
    if (event.key === THEME_STORAGE_KEY) {
      memoryMode = null;
      listener();
    }
  };
  window.addEventListener("storage", onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", onStorage);
  };
}

function readMode(): ThemeMode {
  if (memoryMode) {
    return memoryMode;
  }
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    if (isThemeMode(stored)) {
      return stored;
    }
  } catch {
    // localStorage недоступен — режим по умолчанию
  }
  return "auto";
}

function writeMode(next: ThemeMode): void {
  memoryMode = next;
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, next);
  } catch {
    // не критично: выбор не сохранится между сессиями
  }
  emitChange();
}

// --- Системная тема (prefers-color-scheme) ---

const DARK_QUERY = "(prefers-color-scheme: dark)";

function subscribeSystem(listener: () => void): () => void {
  const mq = window.matchMedia(DARK_QUERY);
  mq.addEventListener("change", listener);
  return () => mq.removeEventListener("change", listener);
}

function readSystemDark(): boolean {
  return window.matchMedia(DARK_QUERY).matches;
}

// --- Контекст ---

type ThemeContextValue = {
  mode: ThemeMode;
  resolved: ResolvedTheme;
  setMode: (mode: ThemeMode) => void;
};

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
  // SSR-снапшоты: auto + светлая (совпадает с дефолтом до инлайн-скрипта)
  const mode = useSyncExternalStore<ThemeMode>(
    subscribeMode,
    readMode,
    () => "auto",
  );
  const systemDark = useSyncExternalStore(
    subscribeSystem,
    readSystemDark,
    () => false,
  );

  const resolved: ResolvedTheme =
    mode === "auto" ? (systemDark ? "dark" : "light") : mode;

  useEffect(() => {
    document.documentElement.dataset.theme = resolved;
  }, [resolved]);

  const setMode = useCallback((next: ThemeMode) => {
    writeMode(next);
  }, []);

  const value = useMemo<ThemeContextValue>(
    () => ({ mode, resolved, setMode }),
    [mode, resolved, setMode],
  );

  return (
    <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
  );
}

/** Хук доступа к теме. Использовать только под ThemeProvider. */
export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error("useTheme должен вызываться внутри <ThemeProvider>");
  }
  return ctx;
}
