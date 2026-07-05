"use client";

/**
 * Amber-полоса (36px) под шапкой: схема демонстрационная, данные
 * не утверждены. Скрывается крестиком, выбор сохраняется в localStorage
 * (читается через useSyncExternalStore — без setState в эффектах и
 * hydration mismatch). Контраст navy-текста на #E08600 ≈ 5.5:1 (WCAG AA).
 */

import { useSyncExternalStore } from "react";
import { useI18n } from "./I18nProvider";

/** Ключ в localStorage: баннер скрыт пользователем. */
const DISMISS_STORAGE_KEY = "metro-dushanbe.demo-banner-dismissed";

// --- Внешнее хранилище флага скрытия (localStorage + in-memory fallback) ---

let memoryDismissed: boolean | null = null;

const listeners = new Set<() => void>();

function emitChange(): void {
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  // Синхронизация между вкладками
  const onStorage = (event: StorageEvent) => {
    if (event.key === DISMISS_STORAGE_KEY) {
      memoryDismissed = null;
      listener();
    }
  };
  window.addEventListener("storage", onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", onStorage);
  };
}

function readDismissed(): boolean {
  if (memoryDismissed !== null) {
    return memoryDismissed;
  }
  try {
    return window.localStorage.getItem(DISMISS_STORAGE_KEY) === "1";
  } catch {
    // localStorage недоступен — баннер остаётся видимым
    return false;
  }
}

function writeDismissed(): void {
  memoryDismissed = true;
  try {
    window.localStorage.setItem(DISMISS_STORAGE_KEY, "1");
  } catch {
    // не критично: скрытие не переживёт перезагрузку
  }
  emitChange();
}

export default function DemoBanner() {
  const { dict } = useI18n();
  // SSR-снапшот: баннер виден
  const dismissed = useSyncExternalStore(subscribe, readDismissed, () => false);

  if (dismissed) {
    return null;
  }

  return (
    <div
      role="status"
      className="demo-banner flex h-9 shrink-0 items-center gap-2 bg-warning px-3 text-brand-navy sm:px-4"
    >
      <svg
        aria-hidden="true"
        focusable="false"
        width={16}
        height={16}
        viewBox="0 0 16 16"
        className="shrink-0"
        fill="currentColor"
      >
        <path d="M8 1.5c.36 0 .69.19.87.5l6.4 11.1a1 1 0 0 1-.87 1.5H1.6a1 1 0 0 1-.87-1.5L7.13 2c.18-.31.51-.5.87-.5Zm0 4a.8.8 0 0 0-.8.84l.17 3.2a.63.63 0 0 0 1.26 0l.17-3.2A.8.8 0 0 0 8 5.5Zm0 5.6a.9.9 0 1 0 0 1.8.9.9 0 0 0 0-1.8Z" />
      </svg>
      <p className="min-w-0 flex-1 truncate text-[13px] font-semibold">
        {dict.demoBanner}
      </p>
      <button
        type="button"
        aria-label={dict.demoDismiss}
        title={dict.demoDismiss}
        onClick={writeDismissed}
        className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md transition-colors duration-150 ease-out hover:bg-brand-navy/15"
      >
        <svg
          aria-hidden="true"
          focusable="false"
          width={14}
          height={14}
          viewBox="0 0 14 14"
          fill="none"
          stroke="currentColor"
          strokeWidth={2}
          strokeLinecap="round"
        >
          <path d="M3 3l8 8M11 3l-8 8" />
        </svg>
      </button>
    </div>
  );
}
