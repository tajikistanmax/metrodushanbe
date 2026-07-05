"use client";

/** Локализованный skip-link к основному содержимому (WCAG 2.2 SC 2.4.1). */

import { useI18n } from "./I18nProvider";

export default function SkipLink() {
  const { dict } = useI18n();
  return (
    <a href="#main" className="skip-link">
      {dict.skipToContent}
    </a>
  );
}
