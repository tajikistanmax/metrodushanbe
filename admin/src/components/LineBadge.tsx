"use client";

/**
 * Бейдж линии («Л1»/«L1») в фирменном цвете. Цвет — вспомогательный сигнал:
 * подпись с кодом линии присутствует всегда (WCAG 2.2 SC 1.4.1).
 */

import { useI18n } from "./I18nProvider";
import { lineBadgeLabel } from "@/lib/i18n";

type LineBadgeProps = {
  code: string;
  /** HEX-цвет линии; по умолчанию navy, если неизвестен. */
  colorHex?: string;
};

export default function LineBadge({ code, colorHex }: LineBadgeProps) {
  const { lang } = useI18n();
  return (
    <span
      className="line-badge"
      style={{ background: colorHex ?? "var(--brand-navy)" }}
    >
      {lineBadgeLabel(code, lang)}
    </span>
  );
}
