// СГЕНЕРИРОВАНО: packages/design/sync.mjs — НЕ РЕДАКТИРОВАТЬ.
// Источник: packages/design/shared/ui/Card.tsx
// Изменения вносите в источник и запускайте: node packages/design/sync.mjs

/**
 * Карточка — базовая поверхность контента. Заменяет разметку, скопированную
 * в 6 файлов портала, и класс .console-card консоли.
 *
 * Институциональный стиль: карточка держится на 1px --border-subtle, а не на
 * тени. elevation="overlay" — только для того, что физически висит над
 * контентом (модалка, поповер, тост).
 *
 * Семантика: по умолчанию <section>. Если карточка — элемент списка, передайте
 * as="li"; если это просто визуальная группировка без собственного заголовка —
 * as="div" (безымянный <section> только замусоривает дерево доступности).
 * При as="section" передавайте `heading` либо aria-label.
 *
 * Проп называется `heading`, а не `title`: `title` у DOM-элемента — это атрибут
 * всплывающей подсказки (string), и одноимённый проп с типом ReactNode ломал бы
 * типизацию при пробросе ...rest.
 */

import type { ReactNode } from "react";

export type CardPadding = "none" | "sm" | "md" | "lg";
export type CardElevation = "flat" | "raised" | "overlay";

/** Паддинги по шкале отступов: 12 / 16 / 24px. */
const PADDING: Record<CardPadding, string> = {
  none: "",
  sm: "p-3",
  md: "p-4",
  lg: "p-6",
};

const ELEVATION: Record<CardElevation, string> = {
  flat: "",
  raised: "shadow-raised",
  overlay: "shadow-overlay",
};

type CardProps = {
  /** Тег-обёртка: section (по умолчанию), div, li, article. */
  as?: "section" | "div" | "li" | "article";
  padding?: CardPadding;
  elevation?: CardElevation;
  /** Заголовок карточки; рендерится как <h2>. */
  heading?: ReactNode;
  /** Пояснение под заголовком. */
  description?: ReactNode;
  /** Контролы в правом верхнем углу (кнопки, фильтры). */
  actions?: ReactNode;
  className?: string;
  /** Имя региона для скринридера, когда видимого заголовка нет. */
  "aria-label"?: string;
  "aria-labelledby"?: string;
  id?: string;
  children?: ReactNode;
};

export default function Card({
  as: Tag = "section",
  padding = "md",
  elevation = "flat",
  heading,
  description,
  actions,
  className,
  children,
  ...rest
}: CardProps) {
  const hasHeader = Boolean(heading || description || actions);
  return (
    <Tag
      className={`rounded-panel border border-[var(--border-subtle)] bg-[var(--surface-raised)] ${
        PADDING[padding]
      } ${ELEVATION[elevation]} ${className ?? ""}`}
      {...rest}
    >
      {hasHeader ? (
        <div className="mb-4 flex items-start justify-between gap-4">
          <div className="min-w-0">
            {heading ? (
              <h2 className="text-title-s font-bold text-[var(--text-primary)]">
                {heading}
              </h2>
            ) : null}
            {description ? (
              <p className="mt-1 text-small text-text-secondary">
                {description}
              </p>
            ) : null}
          </div>
          {actions ? <div className="shrink-0">{actions}</div> : null}
        </div>
      ) : null}
      {children}
    </Tag>
  );
}
