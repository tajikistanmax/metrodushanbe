"use client";

/**
 * Лёгкие тосты об исходе операций. Успех — polite live-region, ошибка —
 * assertive (WCAG SC 4.1.3). Автоскрытие через 5 c; можно закрыть вручную.
 * Провайдер подключается один раз в layout; компоненты вызывают useToast().
 */

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useI18n } from "../I18nProvider";

type ToastKind = "success" | "error";
type Toast = { id: number; kind: ToastKind; message: string };

type ToastContextValue = {
  success: (message: string) => void;
  error: (message: string) => void;
};

const ToastContext = createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const { dict } = useI18n();
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);

  const remove = useCallback((id: number) => {
    setToasts((list) => list.filter((t) => t.id !== id));
  }, []);

  const push = useCallback(
    (kind: ToastKind, message: string) => {
      const id = nextId.current++;
      setToasts((list) => [...list, { id, kind, message }]);
      if (typeof window !== "undefined") {
        window.setTimeout(() => remove(id), 5000);
      }
    },
    [remove],
  );

  const value = useMemo<ToastContextValue>(
    () => ({
      success: (m) => push("success", m),
      error: (m) => push("error", m),
    }),
    [push],
  );

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="pointer-events-none fixed inset-x-0 bottom-4 z-[200] flex flex-col items-center gap-2 px-4">
        {toasts.map((t) => (
          <div
            key={t.id}
            role={t.kind === "error" ? "alert" : "status"}
            aria-live={t.kind === "error" ? "assertive" : "polite"}
            className={`pointer-events-auto flex w-full max-w-md items-start justify-between gap-3 rounded-xl border px-4 py-3 text-sm shadow-[var(--shadow-card)] ${
              t.kind === "error"
                ? "border-brand-red/40 bg-brand-red/10"
                : "border-brand-green/40 bg-brand-green/10"
            }`}
          >
            <span className="min-w-0 break-words">{t.message}</span>
            <button
              type="button"
              onClick={() => remove(t.id)}
              aria-label={dict.actions.close}
              className="shrink-0 text-lg leading-none text-text-secondary hover:opacity-70"
            >
              <span aria-hidden="true">×</span>
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error("useToast должен вызываться внутри <ToastProvider>");
  }
  return ctx;
}
