"use client";

/**
 * Легенда карты: линии (цвет + название из данных), станция, пересадка.
 * Цвет не является единственным носителем смысла — есть подписи.
 */

import { pickName } from "@/lib/i18n";
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

  return (
    <section
      aria-label={dict.legendHeading}
      className="rounded-xl border border-brand-navy/15 bg-surface-light p-4"
    >
      <h2 className="mb-3 text-base font-bold">{dict.legendHeading}</h2>
      <ul className="space-y-2 text-sm">
        {lines.map((line) => (
          <li key={line.properties.code} className="flex items-center gap-2">
            <span
              aria-hidden="true"
              className="inline-block h-1 w-8 rounded-full"
              style={{ backgroundColor: line.properties.color_hex }}
            />
            <span>
              {pickName(line.properties.name, lang)}{" "}
              <span className="text-text-secondary">
                ({line.properties.code})
              </span>
            </span>
          </li>
        ))}
        <li className="flex items-center gap-2">
          <span
            aria-hidden="true"
            className="ml-2 inline-block h-3 w-3 shrink-0 rounded-full bg-surface-light"
            style={{ border: "2.5px solid var(--brand-navy)" }}
          />
          <span>{dict.legendStation}</span>
        </li>
        <li className="flex items-center gap-2">
          <span
            aria-hidden="true"
            className="ml-1.5 inline-block h-4.5 w-4.5 shrink-0 rounded-full bg-surface-light"
            style={{ border: "2.5px solid var(--brand-navy)" }}
          />
          <span>{dict.legendTransfer}</span>
        </li>
      </ul>
    </section>
  );
}
