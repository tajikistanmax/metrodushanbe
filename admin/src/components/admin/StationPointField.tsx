"use client";

/**
 * Поле координат станции: карта-редактор + числовые поля lon/lat.
 *
 * Источник истины — строки lon/lat: клик и перетаскивание по карте пишут в них,
 * ручной ввод правит их напрямую. Числовые поля остаются доступными всегда —
 * это и клавиатурный путь (рисование мышью с клавиатуры недоступно), и запасной
 * путь, когда карта недоступна без сети (docs/dev-conventions.md §5).
 *
 * Диапазоны WGS84 проверяет backend (AdminSupport.point) — здесь правило не
 * дублируется, но заведомо нечисловой ввод форма не отправляет.
 */

import dynamic from "next/dynamic";
import { useState } from "react";
import { useI18n } from "../I18nProvider";
import { useTheme } from "@/shared/ThemeProvider";
import { parseNumber } from "@/lib/validate";
import type { LngLat, NetworkGeoJson } from "@/lib/types";
import { Button } from "@/shared/ui";
import { TextField } from "./fields";

// MapLibre работает только в браузере — SSR отключён.
const GeometryEditorMap = dynamic(() => import("../map/GeometryEditorMap"), {
  ssr: false,
  loading: () => <MapPlaceholder />,
});

function MapPlaceholder() {
  const { dict } = useI18n();
  return (
    <div
      className="flex h-full w-full items-center justify-center bg-[var(--surface-chip)] text-small text-text-secondary"
      role="status"
    >
      {dict.geo.mapLoading}
    </div>
  );
}

type Props = {
  lon: string;
  lat: string;
  onChange: (lon: string, lat: string) => void;
  error?: string;
  context: NetworkGeoJson | null;
};

/** Координата в поле: 6 знаков — ~0.1 м, избыточной точности не копим. */
function fmt(n: number): string {
  return n.toFixed(6);
}

export default function StationPointField({
  lon,
  lat,
  onChange,
  error,
  context,
}: Props) {
  const { dict } = useI18n();
  const { resolved: resolvedTheme } = useTheme();
  const [live, setLive] = useState("");

  const lonN = parseNumber(lon);
  const latN = parseNumber(lat);
  const point: LngLat[] = lonN !== null && latN !== null ? [[lonN, latN]] : [];

  function handleMapChange(next: LngLat[]) {
    const p = next[0];
    if (!p) return;
    onChange(fmt(p[0]), fmt(p[1]));
    setLive(`${dict.geo.livePointSet}: ${fmt(p[0])}, ${fmt(p[1])}`);
  }

  function clearPoint() {
    onChange("", "");
    setLive(dict.geo.livePathCleared);
  }

  return (
    <fieldset
      className={`rounded-control border ${
        error ? "border-brand-red" : "border-[var(--border-subtle)]"
      } px-3 pb-3 pt-2`}
    >
      <legend className="px-1 text-sm font-semibold">
        {dict.form.fieldCoordinates}
        <span aria-hidden="true" className="ml-0.5 text-brand-red">
          *
        </span>
      </legend>

      <p className="mb-2 text-xs text-text-secondary">{dict.geo.pickPointHint}</p>

      <div className="h-[280px] w-full overflow-hidden rounded-control border border-[var(--border-strong)]">
        <GeometryEditorMap
          mode="point"
          value={point}
          onChange={handleMapChange}
          selectedIndex={point.length > 0 ? 0 : null}
          onSelectIndex={() => {}}
          context={context}
          theme={resolvedTheme}
          regionLabel={dict.geo.regionStationEditor}
          zoomInLabel={dict.geo.zoomIn}
          zoomOutLabel={dict.geo.zoomOut}
        />
      </div>

      <div className="mt-2 flex flex-wrap items-center gap-2">
        <Button
          type="button"
          variant="secondary"
          size="sm"
          onClick={clearPoint}
          disabled={point.length === 0}
          aria-label={dict.geo.clearPoint}
        >
          {dict.geo.clearPoint}
        </Button>
      </div>

      {/* Результат правки объявляется вспомогательным технологиям. */}
      <p aria-live="polite" className="sr-only">
        {live}
      </p>

      {/* Числовой ввод — клавиатурный и запасной путь, всегда доступен. */}
      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <TextField
          label={dict.form.fieldLon}
          value={lon}
          onChange={(next) => onChange(next, lat)}
          required
        />
        <TextField
          label={dict.form.fieldLat}
          value={lat}
          onChange={(next) => onChange(lon, next)}
          required
        />
      </div>

      {error ? (
        <p className="mt-1 text-xs font-semibold text-brand-red">{error}</p>
      ) : (
        <p className="mt-1 text-xs text-text-secondary">
          {dict.form.hintCoordinates} {dict.geo.manualHint}
        </p>
      )}
    </fieldset>
  );
}
