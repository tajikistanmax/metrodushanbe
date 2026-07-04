"use client";

/**
 * Баннер-предупреждение: схема демонстрационная, данные не утверждены
 * (см. metadata.disclaimer в data/demo-network.geojson).
 */

import { useI18n } from "./I18nProvider";

export default function DemoBanner() {
  const { dict } = useI18n();

  return (
    <div
      role="status"
      className="border-b border-warning/40 bg-[#FFF6E8] text-brand-navy"
      style={{ borderLeft: "6px solid var(--warning)" }}
    >
      <p className="mx-auto max-w-7xl px-4 py-2.5 text-sm font-medium sm:px-6">
        <span aria-hidden="true" className="mr-2">
          ⚠
        </span>
        {dict.demoBanner}
      </p>
    </div>
  );
}
