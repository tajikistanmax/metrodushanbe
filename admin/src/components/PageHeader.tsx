"use client";

/**
 * Заголовок страницы-раздела: <h1> + необязательный ведущий текст.
 * Держит единый ритм всех дашбордов консоли.
 */

import type { ReactNode } from "react";

type PageHeaderProps = {
  title: string;
  lead?: string;
  /** Правый слот (например, счётчик или будущая кнопка «Создать»). */
  actions?: ReactNode;
};

export default function PageHeader({ title, lead, actions }: PageHeaderProps) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-2xl font-extrabold tracking-tight">{title}</h1>
        {lead ? (
          <p className="mt-1 text-sm text-text-secondary">{lead}</p>
        ) : null}
      </div>
      {actions ? <div className="shrink-0">{actions}</div> : null}
    </div>
  );
}
