"use client";

import { useEffect, useSyncExternalStore } from "react";
import { useI18n } from "./I18nProvider";

function subscribe(listener: () => void): () => void {
  window.addEventListener("online", listener);
  window.addEventListener("offline", listener);
  return () => {
    window.removeEventListener("online", listener);
    window.removeEventListener("offline", listener);
  };
}

function onlineSnapshot(): boolean {
  return navigator.onLine;
}

/** Регистрирует service worker и показывает глобальное состояние offline. */
export default function PwaRuntime() {
  const { dict } = useI18n();
  const online = useSyncExternalStore(subscribe, onlineSnapshot, () => true);

  useEffect(() => {
    if (
      process.env.NODE_ENV !== "production" ||
      !("serviceWorker" in navigator)
    ) {
      return;
    }
    navigator.serviceWorker
      .register("/sw.js", { scope: "/" })
      .then((registration) => registration.update())
      .catch(() => undefined);
  }, []);

  if (online) return null;

  return (
    <p
      role="status"
      aria-live="polite"
      // Тост физически висит над контентом — единственный законный повод
      // для тени (--elevation-overlay, см. packages/design/tokens.mjs)
      className="fixed bottom-4 left-1/2 z-[300] w-[calc(100%-2rem)] max-w-lg -translate-x-1/2 rounded-panel border border-warning/50 bg-brand-navy px-4 py-3 text-center text-small font-semibold text-surface-light shadow-overlay"
    >
      {dict.offlineStatus}
    </p>
  );
}
