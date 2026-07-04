"use client";

/**
 * React-контекст языка интерфейса.
 * - default: tg; выбор сохраняется в localStorage;
 * - <html lang> обновляется при смене языка;
 * - состояние читается через useSyncExternalStore: при SSR/гидратации
 *   используется tg, затем без hydration mismatch подхватывается
 *   сохранённый выбор (в т.ч. синхронизация между вкладками).
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
import {
  DEFAULT_LANG,
  getDict,
  isLang,
  LANG_STORAGE_KEY,
  type Dict,
  type Lang,
} from "@/lib/i18n";

// --- Внешнее хранилище языка (localStorage + in-memory fallback) ---

/** In-memory значение на случай недоступного localStorage (privacy mode). */
let memoryLang: Lang | null = null;

const listeners = new Set<() => void>();

function emitChange(): void {
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  // Синхронизация выбора языка между вкладками
  const onStorage = (event: StorageEvent) => {
    if (event.key === LANG_STORAGE_KEY) {
      memoryLang = null;
      listener();
    }
  };
  window.addEventListener("storage", onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", onStorage);
  };
}

function readLang(): Lang {
  if (memoryLang) {
    return memoryLang;
  }
  try {
    const stored = window.localStorage.getItem(LANG_STORAGE_KEY);
    if (isLang(stored)) {
      return stored;
    }
  } catch {
    // localStorage недоступен — используем значение по умолчанию
  }
  return DEFAULT_LANG;
}

function writeLang(next: Lang): void {
  memoryLang = next;
  try {
    window.localStorage.setItem(LANG_STORAGE_KEY, next);
  } catch {
    // не критично: выбор просто не сохранится между сессиями
  }
  emitChange();
}

// --- Контекст ---

type I18nContextValue = {
  lang: Lang;
  setLang: (lang: Lang) => void;
  dict: Dict;
};

const I18nContext = createContext<I18nContextValue | null>(null);

export function I18nProvider({ children }: { children: ReactNode }) {
  // На сервере и при гидратации — tg (совпадает с <html lang="tg">),
  // после — сохранённое значение из localStorage.
  const lang = useSyncExternalStore(subscribe, readLang, () => DEFAULT_LANG);

  useEffect(() => {
    document.documentElement.lang = lang;
  }, [lang]);

  const setLang = useCallback((next: Lang) => {
    writeLang(next);
  }, []);

  const value = useMemo<I18nContextValue>(
    () => ({ lang, setLang, dict: getDict(lang) }),
    [lang, setLang],
  );

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

/** Хук доступа к языку и словарю. Использовать только под I18nProvider. */
export function useI18n(): I18nContextValue {
  const ctx = useContext(I18nContext);
  if (!ctx) {
    throw new Error("useI18n должен вызываться внутри <I18nProvider>");
  }
  return ctx;
}
