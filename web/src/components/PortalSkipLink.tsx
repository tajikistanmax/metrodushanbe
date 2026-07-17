"use client";

/**
 * Skip-link портала: первый фокусируемый элемент документа, скрыт до фокуса
 * (стили — .skip-link в сгенерированном блоке globals.css).
 *
 * Отдельный компонент нужен потому, что подпись берётся из словаря, а словарь
 * живёт в клиентском контексте: серверный app/layout.tsx прочитать его не
 * может, а хардкодить строку нельзя (dev-conventions.md §6).
 */

import { MAIN_CONTENT_ID } from "@/lib/dom-ids";
import { useI18n } from "@/shared/I18nProvider";

export default function PortalSkipLink() {
  const { dict } = useI18n();
  return (
    <a href={`#${MAIN_CONTENT_ID}`} className="skip-link">
      {dict.skipToContent}
    </a>
  );
}
