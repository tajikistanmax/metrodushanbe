"use client";

/**
 * Поле трассы линии: карта-редактор + числовой ввод.
 *
 * Единственный источник истины — текст трассы («lon, lat» построчно): карта
 * пишет в него через pathToText, ручной ввод правит его напрямую. Так карта и
 * числовые поля не расходятся, а клавиатурный путь (числовой ввод) остаётся
 * полноценным — рисование мышью его дополняет, а не заменяет
 * (docs/dev-conventions.md §5).
 *
 * Многосегментная трасса (ветки) правится только импортом: поле «path» в
 * контракте backend — плоский список точек, то есть ОДНА линия, и сохранение
 * схлопнуло бы сегменты. В этом случае редактор переходит в режим чтения.
 */

import dynamic from "next/dynamic";
import { useState } from "react";
import { useI18n } from "../I18nProvider";
import { useTheme } from "@/shared/ThemeProvider";
import { parsePath, pathToText } from "@/lib/validate";
import type { LngLat, NetworkGeoJson } from "@/lib/types";
import { Alert, Button } from "@/shared/ui";
import { TextareaField } from "./fields";

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
  /** Текст трассы («lon, lat» построчно) — источник истины. */
  value: string;
  onChange: (next: string) => void;
  error?: string;
  context: NetworkGeoJson | null;
  /** Цвет редактируемой линии (жила черновой трассы). */
  color?: string;
  /** Сегменты существующей трассы из /network/geojson. */
  segments: LngLat[][];
};

export default function LinePathField({
  value,
  onChange,
  error,
  context,
  color,
  segments,
}: Props) {
  const { dict } = useI18n();
  const { resolved: resolvedTheme } = useTheme();
  const [selected, setSelected] = useState<number | null>(null);
  const [live, setLive] = useState("");

  // Ветвистая трасса: правка через плоский path потеряла бы сегменты.
  const multiSegment = segments.length > 1;

  // Точки для карты: из текста. Невалидный текст (оператор печатает) — карта
  // просто не показывает черновик, ошибку выводит форма.
  const points = (parsePath(value) ?? []) as LngLat[];

  function commit(next: LngLat[], message: string) {
    onChange(pathToText(next));
    setLive(`${message}. ${dict.geo.pointsCount}: ${next.length}`);
  }

  function handleMapChange(next: LngLat[]) {
    if (multiSegment) return;
    const message =
      next.length > points.length
        ? points.length > 0 && next.length === points.length + 1
          ? dict.geo.livePointAdded
          : dict.geo.livePointInserted
        : next.length < points.length
          ? dict.geo.livePointRemoved
          : dict.geo.livePointMoved;
    commit(next, message);
  }

  function undoPoint() {
    const next = points.slice(0, -1);
    setSelected(null);
    commit(next, dict.geo.livePointRemoved);
  }

  function clearPath() {
    setSelected(null);
    onChange("");
    setLive(dict.geo.livePathCleared);
  }

  function deleteSelected() {
    if (selected === null) return;
    const next = points.filter((_, i) => i !== selected);
    setSelected(null);
    commit(next, dict.geo.livePointRemoved);
  }

  return (
    <fieldset className="rounded-control border border-[var(--border-subtle)] px-3 pb-3 pt-2">
      <legend className="px-1 text-sm font-semibold">{dict.form.fieldPath}</legend>

      {multiSegment ? (
        <Alert
          tone="warning"
          label={dict.severity.warning}
          heading={dict.geo.multiSegmentTitle}
          className="mb-3"
        >
          <p>{dict.geo.multiSegmentText}</p>
          <p className="mt-1 font-semibold">
            {dict.geo.multiSegmentSegments}: {segments.length}
          </p>
        </Alert>
      ) : null}

      {/* Подсказка о рисовании — только когда рисовать действительно можно;
          для ветвистой трассы объяснение уже дано в Alert выше. */}
      {!multiSegment ? (
        <p className="mb-2 text-xs text-text-secondary">{dict.geo.drawPathHint}</p>
      ) : null}

      <div className="h-[320px] w-full overflow-hidden rounded-control border border-[var(--border-strong)]">
        <GeometryEditorMap
          mode="path"
          value={multiSegment ? [] : points}
          onChange={handleMapChange}
          selectedIndex={multiSegment ? null : selected}
          onSelectIndex={setSelected}
          readOnlySegments={multiSegment ? segments : undefined}
          context={context}
          color={color}
          theme={resolvedTheme}
          readOnly={multiSegment}
          regionLabel={dict.geo.regionLineEditor}
          zoomInLabel={dict.geo.zoomIn}
          zoomOutLabel={dict.geo.zoomOut}
        />
      </div>

      {!multiSegment ? (
        <div className="mt-2 flex flex-wrap items-center gap-2">
          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={undoPoint}
            disabled={points.length === 0}
            aria-label={dict.geo.undoPoint}
          >
            {dict.geo.undoPoint}
          </Button>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={deleteSelected}
            disabled={selected === null}
            aria-label={dict.geo.deletePoint}
          >
            {dict.geo.deletePoint}
          </Button>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={clearPath}
            disabled={points.length === 0}
            aria-label={dict.geo.clearPath}
          >
            {dict.geo.clearPath}
          </Button>
          <span className="ml-auto text-xs text-text-secondary">
            {dict.geo.pointsCount}: {points.length}
            {" · "}
            {selected === null
              ? dict.geo.noPointSelected
              : `${dict.geo.selectedPoint}: ${selected + 1}`}
          </span>
        </div>
      ) : null}

      {/* Результат правки объявляется вспомогательным технологиям. */}
      <p aria-live="polite" className="sr-only">
        {live}
      </p>

      <div className="mt-3">
        <TextareaField
          label={dict.geo.manualTitle}
          value={value}
          onChange={(next) => {
            setSelected(null);
            onChange(next);
          }}
          error={error}
          hint={dict.geo.manualHint}
          disabled={multiSegment}
          placeholder={"68.78, 38.57\n68.79, 38.58"}
        />
      </div>

      <p className="mt-1 text-xs text-text-secondary">{dict.form.hintPath}</p>
    </fieldset>
  );
}
