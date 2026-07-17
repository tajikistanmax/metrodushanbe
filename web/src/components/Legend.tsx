"use client";

/**
 * Легенда карты: компактная карточка-строка чипов снизу-слева —
 * бейджи линий («Л1»/«Л2»), кружок станции, двойное кольцо пересадки.
 * Цвет не является единственным носителем смысла — есть подписи/названия.
 * На мобильных скрыта: ту же информацию даёт bottom-sheet со списком линий.
 */

import { lineBadgeLabel, pickName } from "@/lib/i18n";
import { isLineFeature, type NetworkGeoJson } from "@/lib/types";
import { useI18n } from "./I18nProvider";

type LegendProps = {
  data: NetworkGeoJson | null;
};

export default function Legend({ data }: LegendProps) {
  const { lang, dict } = useI18n();
  const lines = (data?.features ?? [])
    .filter(isLineFeature)
    .sort((a, b) => a.properties.sort_order - b.properties.sort_order);

  if (lines.length === 0) {
    return null;
  }

  return (
    <section
      aria-label={dict.legendHeading}
      className="absolute bottom-4 left-4 z-10 hidden items-center gap-3 rounded-panel border border-[var(--border-subtle)] bg-[var(--surface-glass)] px-3 py-2 text-caption font-semibold text-[var(--text-primary)] backdrop-blur-md md:flex"
    >
      {lines.map((line) => (
        <span
          key={line.properties.code}
          className="line-badge"
          style={{ background: line.properties.color_hex }}
          title={pickName(line.properties.name, lang)}
        >
          {lineBadgeLabel(line.properties.code, lang)}
          <span className="sr-only"> — {pickName(line.properties.name, lang)}</span>
        </span>
      ))}

      <span className="flex items-center gap-1.5">
        <span
          aria-hidden="true"
          className="h-3.5 w-3.5 shrink-0 rounded-full border-[2.5px] border-[var(--diagram-ring)] bg-[var(--station-fill)]"
        />
        {dict.legendStation}
      </span>

      <span className="flex items-center gap-1.5">
        <span
          aria-hidden="true"
          className="flex h-[18px] w-[18px] shrink-0 items-center justify-center rounded-full border-[2.5px] border-[var(--diagram-ring)] bg-[var(--station-fill)]"
        >
          <span className="h-[7px] w-[7px] rounded-full border-2 border-[var(--diagram-ring)]" />
        </span>
        {dict.legendTransfer}
      </span>
    </section>
  );
}
