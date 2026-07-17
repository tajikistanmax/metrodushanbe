"use client";

/**
 * Постраничная навигация лент консоли.
 *
 * Компонент общий, а не свой в каждом разделе: контракт пагинации у backend
 * один (см. ImportPageDto), и разные кнопки в разных разделах были бы
 * различием без причины.
 *
 * Номер страницы наружу идёт с НУЛЯ — ровно так его принимает backend;
 * оператору же показывается человеческий отсчёт с единицы. Держать обе
 * нумерации в одном месте дешевле, чем ловить сдвиг на единицу в каждом
 * разделе по отдельности.
 */

import { Button } from "@/shared/ui";
import { useI18n } from "../I18nProvider";

type PagerProps = {
  /** Текущая страница, с нуля. */
  page: number;
  totalPages: number;
  totalElements: number;
  /** Размер страницы, как его вернул backend (он же зажимает потолок). */
  size: number;
  busy?: boolean;
  onPage: (page: number) => void;
};

export default function Pager({
  page,
  totalPages,
  totalElements,
  size,
  busy = false,
  onPage,
}: PagerProps) {
  const { dict } = useI18n();
  const t = dict.pager;
  // Пустая лента — это всё ещё одна страница: «Страница 1 из 0» оператору
  // ничего не сообщает.
  const pages = Math.max(totalPages, 1);
  const first = page <= 0;
  const last = page >= pages - 1;

  return (
    <nav
      aria-label={t.label}
      className="flex flex-wrap items-center justify-between gap-3"
    >
      <p className="text-caption tabular-nums text-text-secondary">
        {t.page} {page + 1} / {pages} · {dict.total}: {totalElements} ·{" "}
        {t.pageSize}: {size}
      </p>
      <div className="flex gap-1.5">
        <Button size="sm" disabled={busy || first} onClick={() => onPage(page - 1)}>
          {t.prev}
        </Button>
        <Button size="sm" disabled={busy || last} onClick={() => onPage(page + 1)}>
          {t.next}
        </Button>
      </div>
    </nav>
  );
}
