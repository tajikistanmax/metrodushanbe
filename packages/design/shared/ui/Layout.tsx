/**
 * Container и Stack — примитивы раскладки. Нужны, чтобы ширина колонки и
 * вертикальный ритм задавались шкалой, а не заново на каждом экране.
 *
 * Оба — «прозрачные»: не рисуют ни фона, ни границы, ни отступов, кроме
 * заявленных. Всё оформление — на Card.
 */

import type { ReactNode } from "react";

// --- Container -------------------------------------------------------------

/**
 * Ширины колонки. `text` (65ch) — предел читаемости строки: длиннее ~75
 * символов глаз теряет начало следующей строки (WCAG 2.2 SC 1.4.8 требует
 * ≤80 символов для механизма чтения).
 */
export type ContainerWidth = "text" | "narrow" | "default" | "wide" | "full";

const WIDTH: Record<ContainerWidth, string> = {
  text: "max-w-[65ch]",
  narrow: "max-w-3xl",   /* 768px — формы, статьи */
  default: "max-w-5xl",  /* 1024px — типовая страница */
  wide: "max-w-7xl",     /* 1280px — таблицы консоли */
  full: "max-w-none",
};

export function Container({
  width = "default",
  className,
  children,
}: {
  width?: ContainerWidth;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div className={`mx-auto w-full ${WIDTH[width]} px-4 ${className ?? ""}`}>
      {children}
    </div>
  );
}

// --- Stack -----------------------------------------------------------------

/** Зазоры по шкале отступов: 4/8/12/16/24/32/48px. */
export type StackGap = 1 | 2 | 3 | 4 | 5 | 6 | 7;

const GAP: Record<StackGap, string> = {
  1: "gap-1",
  2: "gap-2",
  3: "gap-3",
  4: "gap-4",
  5: "gap-6",
  6: "gap-8",
  7: "gap-12",
};

/**
 * Классы выравнивания заданы ПОЛНЫМИ строками, а не собраны шаблоном
 * (`items-${align}`): Tailwind сканирует исходники статически и не видит
 * классы, склеенные в рантайме, — такие утилиты просто не попали бы в бандл.
 */
const ALIGN: Record<StackAlign, string> = {
  start: "items-start",
  center: "items-center",
  end: "items-end",
  baseline: "items-baseline",
  stretch: "items-stretch",
};

const JUSTIFY: Record<StackJustify, string> = {
  start: "justify-start",
  center: "justify-center",
  end: "justify-end",
  between: "justify-between",
};

export type StackAlign = "start" | "center" | "end" | "baseline" | "stretch";
export type StackJustify = "start" | "center" | "end" | "between";

/**
 * Вертикальная (по умолчанию) или горизонтальная стопка с зазором из шкалы.
 * as="ul"/"ol" — когда стопка семантически список.
 */
export function Stack({
  as: Tag = "div",
  direction = "vertical",
  gap = 4,
  align,
  justify,
  wrap = false,
  className,
  children,
}: {
  as?: "div" | "ul" | "ol" | "li" | "section" | "form";
  direction?: "vertical" | "horizontal";
  gap?: StackGap;
  align?: StackAlign;
  justify?: StackJustify;
  wrap?: boolean;
  className?: string;
  children: ReactNode;
}) {
  const alignCls = align ? ALIGN[align] : "";
  const justifyCls = justify ? JUSTIFY[justify] : "";
  return (
    <Tag
      className={`flex ${
        direction === "vertical" ? "flex-col" : "flex-row"
      } ${GAP[gap]} ${wrap ? "flex-wrap" : ""} ${alignCls} ${justifyCls} ${
        className ?? ""
      }`}
    >
      {children}
    </Tag>
  );
}
