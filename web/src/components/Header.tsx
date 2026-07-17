"use client";

/**
 * Шапка портала — одна на весь портал (рендерится из app/layout.tsx).
 *
 * Институциональный вид: сплошной --brand-navy без градиента, без тени под
 * панелью и без drop-shadow под маркой. Плоскости разделяет граница и цвет, а
 * не размытие (обоснование шкалы теней — packages/design/tokens.mjs).
 *
 * Контрасты на navy (#082742) посчитаны, менять вслепую нельзя:
 *   белый текст и марка                      ≈ 15.2:1  ✓ AA
 *   text-surface-light/80 (неактивный пункт) ≈ 10.2:1  ✓ AA
 *   активный пункт: navy на белом            ≈ 15.2:1  ✓ AA
 *   индикатор фокуса — белый (см. globals.css: header :focus-visible)
 *
 * Словесного знака <h1> здесь НЕТ намеренно. Заголовок первого уровня
 * принадлежит СТРАНИЦЕ и на каждой странице свой; общая шапка, повторяющая
 * <h1> на всех экранах, оставляла бы разделы без собственных заголовков —
 * ровно это и было до редизайна (портальный <h1> = 15px, скрыт до xl).
 */

import Link from "next/link";
import { usePathname } from "next/navigation";
import { isNavActive, PORTAL_NAV } from "@/lib/nav";
import BrandMark from "@/shared/BrandMark";
import ThemeToggle from "@/shared/ThemeToggle";
import LangSwitcher from "@/shared/LangSwitcher";
import { useI18n } from "@/shared/I18nProvider";
import { useDataSource } from "./DataSourceProvider";

/**
 * Цвет точки-индикатора источника. Только токены; контраст каждой к navy —
 * выше порога 3:1 для нетекстовой графики (SC 1.4.11):
 *   --status-ok   #4ade80 ≈ 8.7:1
 *   --warning     #e08600 ≈ 5.5:1
 *   --status-idle #9fb3c8 ≈ 7.1:1
 * Точка — ВСПОМОГАТЕЛЬНЫЙ сигнал: рядом всегда подпись словами (SC 1.4.1).
 */
const SOURCE_DOT: Record<"api" | "demo" | "loading", string> = {
  api: "var(--status-ok)",
  demo: "var(--warning)",
  loading: "var(--status-idle)",
};

/** Активный пункт — белая плашка; радиус из шкалы (--corner-control, 4px). */
const NAV_ACTIVE =
  "block rounded-control bg-surface-light px-3 py-1.5 text-small font-bold text-brand-navy";
const NAV_IDLE =
  "block rounded-control px-3 py-1.5 text-small font-semibold text-surface-light/80 " +
  "transition-colors duration-150 ease-out hover:bg-surface-light/15 hover:text-surface-light";

export default function Header() {
  const { dict } = useI18n();
  const pathname = usePathname();
  const source = useDataSource();

  const sourceKey = source ?? "loading";
  const sourceShort =
    source === "api"
      ? dict.dataSourceApi
      : source === "demo"
        ? dict.dataSourceDemoShort
        : dict.loading;
  const sourceLong =
    source === "api"
      ? dict.dataSourceApi
      : source === "demo"
        ? dict.dataSourceDemo
        : dict.loading;

  return (
    <header className="z-30 shrink-0">
      {/* Лента флага РТ — государственная сигнатура портала */}
      <div className="ribbon-flag" aria-hidden="true" />

      <div className="flex h-16 items-center gap-2 bg-brand-navy px-3 text-surface-light sm:gap-3 sm:px-4">
        {/* Марка ведёт на главную: единственная ссылка «домой», не зависящая
            от того, докуда пассажир пролистал навигацию */}
        <Link href="/" className="flex min-w-0 items-center gap-2.5 rounded-control">
          <BrandMark
            className="h-9 w-[39px] shrink-0"
            holeColor="var(--brand-navy)"
          />
          {/* Словесный знак — вес 800 оправдан: это марка, а не текст интерфейса */}
          <span className="hidden truncate text-body font-extrabold lg:block">
            {dict.appTitle}
          </span>
        </Link>

        <nav
          aria-label={dict.appTitle}
          className="scrollbar-none min-w-0 flex-1 overflow-x-auto md:ml-1"
        >
          <ul className="flex w-max items-center gap-0.5 sm:gap-1">
            {PORTAL_NAV.map((item) => {
              const active = isNavActive(pathname, item.href);
              return (
                <li key={item.key}>
                  <Link
                    href={item.href}
                    aria-current={active ? "page" : undefined}
                    className={active ? NAV_ACTIVE : NAV_IDLE}
                  >
                    {item.label(dict)}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>

        <div className="ml-auto flex shrink-0 items-center gap-1.5 sm:gap-2.5">
          {/* Пилюля источника данных. Скрыта на разделах без данных сети:
              source === undefined (см. DataSourceProvider). */}
          {source !== undefined && (
            <span
              role="status"
              title={`${dict.dataSourceLabel}: ${sourceLong}`}
              className="flex h-8 items-center gap-1.5 rounded-control bg-surface-light/10 px-2.5 text-caption font-semibold"
            >
              <span
                aria-hidden="true"
                className="h-2 w-2 shrink-0 rounded-full"
                style={{ background: SOURCE_DOT[sourceKey] }}
              />
              <span className="hidden md:inline">{sourceShort}</span>
              <span className="sr-only">
                {dict.dataSourceLabel}: {sourceLong}
              </span>
            </span>
          )}

          <ThemeToggle />
          <LangSwitcher />
        </div>
      </div>
    </header>
  );
}
