"use client";

/**
 * Тулбар раздела над таблицей: справа — основная кнопка «Добавить».
 * Держит единый ритм с PageHeader (отступ снизу), не мешает read-контенту.
 */

import { Button } from "@/shared/ui";
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
      {/* Было: сырой <button> с rounded-lg(8) и bg-brand-navy — копия primary
          из примитива, но со своим радиусом и hover:opacity вместо цвета. */}
      <Button
        variant="primary"
        onClick={onCreate}
        iconLeft={
          <span aria-hidden="true" className="text-body leading-none">
            +
          </span>
        }
      >
        {createLabel ?? dict.actions.create}
      </Button>
    </div>
  );
}
