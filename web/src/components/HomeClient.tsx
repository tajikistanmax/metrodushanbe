"use client";

/**
 * Клиентская часть главной страницы: загрузка данных сети (API → офлайн-демо),
 * карта (dynamic import без SSR), список станций, легенда,
 * индикатор источника данных.
 */

import dynamic from "next/dynamic";
import { useCallback, useEffect, useState } from "react";
import { loadNetworkData, type NetworkDataResult } from "@/lib/network-data";
import type { MapSelection } from "./NetworkMap";
import DemoBanner from "./DemoBanner";
import Header from "./Header";
import Legend from "./Legend";
import StationList from "./StationList";
import { useI18n } from "./I18nProvider";

// MapLibre работает только в браузере — отключаем SSR
const NetworkMap = dynamic(() => import("./NetworkMap"), {
  ssr: false,
  loading: () => <MapPlaceholder />,
});

function MapPlaceholder() {
  const { dict } = useI18n();
  return (
    <div
      className="flex h-[420px] w-full items-center justify-center rounded-xl border border-brand-navy/15 bg-surface-muted text-sm text-text-secondary sm:h-[520px]"
      role="status"
    >
      {dict.mapLoading}
    </div>
  );
}

export default function HomeClient() {
  const { lang, dict } = useI18n();
  const [result, setResult] = useState<NetworkDataResult | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
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

  const handleSelect = useCallback((code: string) => {
    setSelection((prev) => ({ code, seq: (prev?.seq ?? 0) + 1 }));
  }, []);

  return (
    <>
      {/* Skip-link — первый фокусируемый элемент страницы (A11Y) */}
      <a href="#station-list" className="skip-link">
        {dict.skipToList}
      </a>

      <Header />
      <DemoBanner />

      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-4 sm:px-6">
        {loadFailed && (
          <p role="alert" className="mb-3 text-sm font-semibold text-brand-red">
            {dict.loadError}
          </p>
        )}

        <div className="grid gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(280px,1fr)]">
          <div className="min-w-0">
            <NetworkMap
              data={result?.data ?? null}
              lang={lang}
              selection={selection}
            />
            {/* Индикатор источника данных: API или демо (офлайн) */}
            <p className="mt-2 text-xs text-text-secondary">
              {dict.dataSourceLabel}:{" "}
              <span className="font-semibold text-brand-navy">
                {result
                  ? result.source === "api"
                    ? dict.dataSourceApi
                    : dict.dataSourceDemo
                  : dict.loading}
              </span>
            </p>
          </div>

          <aside
            aria-label={dict.stationsHeading}
            className="flex min-w-0 flex-col gap-4"
          >
            <StationList data={result?.data ?? null} onSelect={handleSelect} />
            <Legend data={result?.data ?? null} />
          </aside>
        </div>
      </main>
    </>
  );
}
