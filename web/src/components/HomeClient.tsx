"use client";

/**
 * Клиентская часть главной страницы: карта — весь вьюпорт под шапкой
 * (холст продукта), поверх неё — плавающая панель станций и легенда.
 * Загрузка данных сети — API → офлайн-демо (dev-conventions.md, §8).
 */

import dynamic from "next/dynamic";
import { useCallback, useEffect, useState } from "react";
import { loadActiveAlerts } from "@/lib/alerts-data";
import { loadNetworkData, type NetworkDataResult } from "@/lib/network-data";
import type { ServiceAlert } from "@/lib/types";
import type { MapSelection } from "./NetworkMap";
import AlertsBanner, { MAIN_CONTENT_ID } from "./AlertsBanner";
import DemoBanner from "./DemoBanner";
import Header from "./Header";
import Legend from "./Legend";
import MobilityDock from "./MobilityDock";
import StationPanel from "./StationPanel";
import { useI18n } from "./I18nProvider";
import { useTheme } from "./ThemeProvider";

// MapLibre работает только в браузере — отключаем SSR
const NetworkMap = dynamic(() => import("./NetworkMap"), {
  ssr: false,
  loading: () => <MapPlaceholder />,
});

function MapPlaceholder() {
  const { dict } = useI18n();
  return (
    <div
      className="absolute inset-0 flex items-center justify-center bg-[var(--map-bg)] text-sm text-text-secondary"
      role="status"
    >
      {dict.mapLoading}
    </div>
  );
}

export default function HomeClient() {
  const { lang, dict } = useI18n();
  const { resolved } = useTheme();
  const [result, setResult] = useState<NetworkDataResult | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [alerts, setAlerts] = useState<ServiceAlert[]>([]);
  const [selection, setSelection] = useState<MapSelection | null>(null);

  useEffect(() => {
    let cancelled = false;
    loadNetworkData()
      .then((res) => {
        if (!cancelled) {
          setResult(res);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setLoadFailed(true);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // Сервисные уведомления грузятся параллельно и независимо от данных сети;
  // loadActiveAlerts никогда не отклоняется — при ошибке отдаёт []
  useEffect(() => {
    let cancelled = false;
    loadActiveAlerts().then((res) => {
      if (!cancelled) {
        setAlerts(res);
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const handleSelect = useCallback((code: string) => {
    setSelection((prev) => ({ code, seq: (prev?.seq ?? 0) + 1 }));
  }, []);

  return (
    <div className="flex h-dvh w-full flex-col overflow-hidden">
      {/* Skip-link — первый фокусируемый элемент страницы (A11Y) */}
      <a href="#station-list" className="skip-link">
        {dict.skipToList}
      </a>

      <Header source={result?.source ?? null} />
      <DemoBanner />
      <AlertsBanner alerts={alerts} data={result?.data ?? null} />

      {/* Карта — холст продукта: занимает всё остальное пространство.
          tabIndex={-1} — программная цель фокуса после закрытия последнего
          баннера уведомлений (см. AlertsBanner) */}
      <main id={MAIN_CONTENT_ID} tabIndex={-1} className="relative min-h-0 flex-1">
        <NetworkMap
          data={result?.data ?? null}
          lang={lang}
          theme={resolved}
          selection={selection}
          onSelect={handleSelect}
        />

        {loadFailed && (
          <p
            role="alert"
            className="absolute left-1/2 top-4 z-20 -translate-x-1/2 rounded-xl bg-brand-red px-4 py-2 text-sm font-semibold text-surface-light shadow-[var(--shadow-card)]"
          >
            {dict.loadError}
          </p>
        )}

        <StationPanel
          data={result?.data ?? null}
          onSelect={handleSelect}
          selectedCode={selection?.code ?? null}
        />
        <Legend data={result?.data ?? null} />
        <MobilityDock data={result?.data ?? null} alertsCount={alerts.length} />
      </main>
    </div>
  );
}
