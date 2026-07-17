/**
 * Разделы публичного портала — ДАННЫЕ, а не цепочка булевых флагов.
 *
 * До редизайна Header держал по флагу на маршрут (`onNews`, `onRoute`, …), а
 * активность карты вычислялась отрицанием всех остальных (`onMap = !onNews &&
 * !onRoute && …`). С каждым новым разделом такая цепочка растёт квадратично и
 * молча ломается: добавили `/tickets` — и на нём «активной» осталась карта.
 * Здесь список один, и Header с Footer читают его, а не повторяют.
 *
 * Приём тот же, что у `GROUPS` в admin/src/components/Sidebar.tsx.
 *
 * Подпись — функция от словаря, а не готовая строка: словарь зависит от
 * выбранного языка и существует только в рантайме компонента (хардкод строк
 * запрещён, dev-conventions.md §6).
 */

import type { Dict } from "./i18n";

export type PortalNavItem = {
  /** Стабильный ключ для React-списка. */
  key: string;
  href: string;
  label: (dict: Dict) => string;
};

/**
 * Порядок = пассажирский сценарий: посмотреть сеть → построить маршрут →
 * узнать новости → написать обращение → узнать цену → купить → читать рассылки.
 */
export const PORTAL_NAV: readonly PortalNavItem[] = [
  { key: "map", href: "/", label: (d) => d.news.mapNav },
  { key: "route", href: "/route", label: (d) => d.route.nav },
  { key: "news", href: "/news", label: (d) => d.news.nav },
  { key: "requests", href: "/requests", label: (d) => d.requests.nav },
  { key: "fares", href: "/fares", label: (d) => d.fares.nav },
  { key: "tickets", href: "/tickets", label: (d) => d.tickets.nav },
  { key: "notifications", href: "/notifications", label: (d) => d.notifications.nav },
] as const;

/**
 * Активен ли раздел. «/» — только точное совпадение: раздел карты не должен
 * поглощать все остальные маршруты (ровно эту ошибку и делал прежний `onMap`).
 * Прочие разделы захватывают вложенные страницы (`/news/{slug}`).
 */
export function isNavActive(pathname: string, href: string): boolean {
  if (href === "/") {
    return pathname === "/";
  }
  return pathname === href || pathname.startsWith(`${href}/`);
}
