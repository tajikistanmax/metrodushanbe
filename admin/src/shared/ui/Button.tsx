"use client";

// СГЕНЕРИРОВАНО: packages/design/sync.mjs — НЕ РЕДАКТИРОВАТЬ.
// Источник: packages/design/shared/ui/Button.tsx
// Изменения вносите в источник и запускайте: node packages/design/sync.mjs
/**
 * Кнопка платформы. Строгая, без декора: радиус --corner-control (4px),
 * без теней, без градиентов — плоскости разделяет граница (см. tokens.mjs).
 *
 * КОНТРАСТЫ ПОСЧИТАНЫ (WCAG 2.2 AA, 4.5:1 для текста) — не менять вслепую:
 *   primary   белый на #082742 (navy)  ≈ 15.2:1  ✓
 *   danger    белый на #e21b2d (red)   ≈  4.7:1  ✓ (порог 4.5 проходит впритык:
 *             любое осветление красного уронит вариант ниже нормы)
 *   secondary --text-primary на --surface-raised ≈ 15.2:1 ✓ (обе темы)
 *   ghost     --text-primary на --surface-page   ≈ 13.6:1 ✓
 * Варианта с --brand-green НЕТ намеренно: белый на #138a3d ≈ 4.4:1 — ниже 4.5.
 * Зелёный остаётся цветом линии, а не действия.
 *
 * Опасное действие не кодируется одним лишь красным (SC 1.4.1): вариант danger
 * обязан иметь глагол в подписи («Удалить»), а не только цвет.
 */

import type { ButtonHTMLAttributes, ReactNode } from "react";

export type ButtonVariant = "primary" | "secondary" | "danger" | "ghost";
export type ButtonSize = "sm" | "md" | "lg";

const BASE =
  "inline-flex shrink-0 items-center justify-center gap-2 rounded-control " +
  "font-bold whitespace-nowrap transition-colors duration-150 ease-out " +
  "disabled:cursor-not-allowed disabled:opacity-50";

const VARIANT: Record<ButtonVariant, string> = {
  primary:
    "bg-brand-navy text-surface-light hover:bg-brand-navy/90 " +
    // На navy белый индикатор фокуса (≈15:1) вместо --info (<3:1) — SC 1.4.11
    "focus-visible:outline-[var(--focus-ring-on-dark)]",
  secondary:
    "border border-[var(--border-strong)] bg-[var(--surface-raised)] " +
    "text-[var(--text-primary)] hover:bg-[var(--surface-hover)]",
  danger:
    "bg-brand-red text-surface-light hover:bg-brand-red/90 " +
    "focus-visible:outline-[var(--focus-ring-on-dark)]",
  ghost:
    "text-[var(--text-primary)] hover:bg-[var(--surface-hover)]",
};

/** Высоты кратны 4px; ≥44px у lg — комфортная цель нажатия (SC 2.5.8). */
const SIZE: Record<ButtonSize, string> = {
  sm: "h-8 px-3 text-caption",
  md: "h-10 px-4 text-small",
  lg: "h-11 px-5 text-body",
};

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
  /** Растянуть на всю ширину контейнера. */
  block?: boolean;
  /** Иконка перед подписью (декоративная: aria-hidden ставит вызывающий код). */
  iconLeft?: ReactNode;
  /** Иконка после подписи. */
  iconRight?: ReactNode;
  children: ReactNode;
};

export default function Button({
  variant = "secondary",
  size = "md",
  block = false,
  iconLeft,
  iconRight,
  className,
  type = "button",
  children,
  ...rest
}: ButtonProps) {
  return (
    <button
      // Явный type: без него <button> внутри <form> сабмитит форму — источник
      // трудноуловимых багов, поэтому дефолт здесь "button", а не браузерный "submit".
      type={type}
      className={`${BASE} ${VARIANT[variant]} ${SIZE[size]} ${
        block ? "w-full" : ""
      } ${className ?? ""}`}
      {...rest}
    >
      {iconLeft}
      {children}
      {iconRight}
    </button>
  );
}
