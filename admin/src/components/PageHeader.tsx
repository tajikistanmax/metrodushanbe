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
    // Заголовок страницы — единственный h1 экрана: title-l (32px) по шкале.
    // 800 отдан словесному знаку, заголовкам — 700 (см. tokens.mjs).
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-title-l font-bold">{title}</h1>
        {lead ? (
          <p className="mt-1 max-w-[65ch] text-body text-text-secondary">{lead}</p>
        ) : null}
      </div>
      {actions ? <div className="shrink-0">{actions}</div> : null}
    </div>
  );
}
