"use client";

/**
 * Тулбар раздела над таблицей: справа — основная кнопка «Добавить».
 * Держит единый ритм с PageHeader (отступ снизу), не мешает read-контенту.
 */

import { useI18n } from "../I18nProvider";

export default function Toolbar({
  onCreate,
  createLabel,
}: {
  onCreate: () => void;
  createLabel?: string;
}) {
  const { dict } = useI18n();
  return (
    <div className="mb-3 flex justify-end">
      <button
        type="button"
        onClick={onCreate}
        className="inline-flex items-center gap-1.5 rounded-lg bg-brand-navy px-4 py-2 text-sm font-bold text-surface-light transition-opacity hover:opacity-90"
      >
        <span aria-hidden="true" className="text-base leading-none">
          +
        </span>
        {createLabel ?? dict.actions.create}
      </button>
    </div>
  );
}
