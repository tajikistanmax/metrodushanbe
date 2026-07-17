/**
 * Компактный набор inline-SVG иконок операционной консоли.
 * Без внешних зависимостей и шрифтов иконок (офлайн-принцип,
 * dev-conventions.md §8): каждая иконка — чистый SVG-путь в стиле
 * stroke 2 / round joins (визуально совместим с Lucide).
 *
 * Все иконки декоративны по умолчанию (aria-hidden); смысл передаёт
 * текст рядом. Размер задаётся через className (h-* w-*).
 */

import type { SVGProps } from "react";

type IconProps = SVGProps<SVGSVGElement>;

function Base({ children, ...props }: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      {children}
    </svg>
  );
}

/** Дом / обзор. */
export function IconHome(props: IconProps) {
  return (
    <Base {...props}>
      <path d="M3 10.5 12 3l9 7.5" />
      <path d="M5 9.5V21h14V9.5" />
      <path d="M10 21v-6h4v6" />
    </Base>
  );
}

/** Географическая карта / схема сети. */
export function IconMap(props: IconProps) {
  return (
    <Base {...props}>
      <path d="m3 6 5-3 8 3 5-3v15l-5 3-8-3-5 3V6Z" />
      <path d="M8 3v15" />
      <path d="M16 6v15" />
      <circle cx="12" cy="11" r="2" />
    </Base>
  );
}

/** Линии метро (разветвление маршрутов). */
export function IconLines(props: IconProps) {
  return (
    <Base {...props}>
      <circle cx="6" cy="6" r="2.4" />
      <circle cx="18" cy="18" r="2.4" />
      <circle cx="18" cy="6" r="2.4" />
      <path d="M8.4 6h7.2" />
      <path d="M6 8.4V13a4 4 0 0 0 4 4h5.6" />
    </Base>
  );
}

/** Станция (колонна платформы / пин). */
export function IconStation(props: IconProps) {
  return (
    <Base {...props}>
      <path d="M12 21s-6.5-5.4-6.5-10.2A6.5 6.5 0 0 1 12 4.3a6.5 6.5 0 0 1 6.5 6.5C18.5 15.6 12 21 12 21Z" />
      <circle cx="12" cy="10.8" r="2.3" />
    </Base>
  );
}

/** Сервисные уведомления (колокол). */
export function IconBell(props: IconProps) {
  return (
    <Base {...props}>
      <path d="M6 9.5a6 6 0 0 1 12 0c0 4.2 1.6 5.6 2 6.5H4c.4-.9 2-2.3 2-6.5Z" />
      <path d="M10 19a2.2 2.2 0 0 0 4 0" />
    </Base>
  );
}

/** Новости (газета). */
export function IconNews(props: IconProps) {
  return (
    <Base {...props}>
      <path d="M4 5h13v14H6a2 2 0 0 1-2-2V5Z" />
      <path d="M17 8h3v9a2 2 0 0 1-2 2h-1" />
      <path d="M7.5 9h6M7.5 12.5h6M7.5 16h4" />
    </Base>
  );
}

/** Обращения граждан (диалог). */
export function IconRequests(props: IconProps) {
  return (
    <Base {...props}>
      <path d="M4 5.5h16v11H9l-5 4v-15Z" />
      <path d="M8 9h8M8 12.5h5" />
    </Base>
  );
}

/** Тарифы (билет). */
export function IconTicket(props: IconProps) {
  return (
    <Base {...props}>
      <path d="M4 7.5h16v3a2.5 2.5 0 0 0 0 5v3H4v-3a2.5 2.5 0 0 0 0-5v-3Z" />
      <path d="M14 7.5v11" strokeDasharray="2 2" />
    </Base>
  );
}

/** AI-агенты (искра). */
export function IconSpark(props: IconProps) {
  return (
    <Base {...props}>
      <path d="M12 3.5c.7 3.9 2.6 5.8 6.5 6.5-3.9.7-5.8 2.6-6.5 6.5-.7-3.9-2.6-5.8-6.5-6.5 3.9-.7 5.8-2.6 6.5-6.5Z" />
      <path d="M18.5 15.5c.35 1.95 1.3 2.9 3.25 3.25-1.95.35-2.9 1.3-3.25 3.25-.35-1.95-1.3-2.9-3.25-3.25 1.95-.35 2.9-1.3 3.25-3.25Z" strokeWidth="1.6" />
    </Base>
  );
}

/** Аудит (журнал с отметками). */
export function IconAudit(props: IconProps) {
  return (
    <Base {...props}>
      <path d="M8 3.5h8a2 2 0 0 1 2 2V19a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V5.5a2 2 0 0 1 2-2Z" />
      <path d="M9.5 8.5l1.4 1.4 2.6-2.6" />
      <path d="M9.5 14.5l1.4 1.4 2.6-2.6" />
    </Base>
  );
}

/** Аналитика (столбики). */
export function IconChart(props: IconProps) {
  return (
    <Base {...props}>
      <path d="M4 20h16" />
      <path d="M7 20v-6" />
      <path d="M12 20V9" />
      <path d="M17 20V4.5" />
    </Base>
  );
}

/** Поиск. */
export function IconSearch(props: IconProps) {
  return (
    <Base {...props}>
      <circle cx="11" cy="11" r="6.5" />
      <path d="m20 20-3.8-3.8" />
    </Base>
  );
}

/** Выход из системы. */
export function IconLogout(props: IconProps) {
  return (
    <Base {...props}>
      <path d="M14 4H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h7" />
      <path d="m17 8 4 4-4 4" />
      <path d="M21 12H10" />
    </Base>
  );
}

/** Пользователь. */
export function IconUser(props: IconProps) {
  return (
    <Base {...props}>
      <circle cx="12" cy="8" r="3.6" />
      <path d="M5 20a7 7 0 0 1 14 0" />
    </Base>
  );
}

/** Группа пользователей (раздел управления операторами). */
export function IconUsers(props: IconProps) {
  return (
    <Base {...props}>
      <circle cx="9" cy="8" r="3.2" />
      <path d="M3 19a6 6 0 0 1 12 0" />
      <path d="M16 5.4a3.2 3.2 0 0 1 0 5.2" />
      <path d="M17.5 13.6A6 6 0 0 1 21 19" />
    </Base>
  );
}

/** Замок (пароль). */
export function IconLock(props: IconProps) {
  return (
    <Base {...props}>
      <rect x="5" y="10.5" width="14" height="9.5" rx="2" />
      <path d="M8 10.5V8a4 4 0 0 1 8 0v2.5" />
    </Base>
  );
}

/** Щит (безопасность). */
export function IconShield(props: IconProps) {
  return (
    <Base {...props}>
      <path d="M12 3 5 5.8v5.4c0 4.5 2.9 7.6 7 9.3 4.1-1.7 7-4.8 7-9.3V5.8L12 3Z" />
      <path d="m9.2 11.8 2 2 3.6-3.9" />
    </Base>
  );
}

/** Молния (быстрые действия). */
export function IconBolt(props: IconProps) {
  return (
    <Base {...props}>
      <path d="M13 3 5 13.5h5.5L11 21l8-10.5h-5.5L13 3Z" />
    </Base>
  );
}

/** Часы. */
export function IconClock(props: IconProps) {
  return (
    <Base {...props}>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3 2" />
    </Base>
  );
}

/** Пульс (здоровье систем). */
export function IconPulse(props: IconProps) {
  return (
    <Base {...props}>
      <path d="M3 12h4l2.2-5.5 4 11L15.5 12H21" />
    </Base>
  );
}

/** Плюс. */
export function IconPlus(props: IconProps) {
  return (
    <Base {...props}>
      <path d="M12 5v14M5 12h14" />
    </Base>
  );
}

/** Стрелка вправо. */
export function IconArrowRight(props: IconProps) {
  return (
    <Base {...props}>
      <path d="M4 12h16" />
      <path d="m14 6 6 6-6 6" />
    </Base>
  );
}

/** Глаз (показать пароль). */
export function IconEye(props: IconProps) {
  return (
    <Base {...props}>
      <path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z" />
      <circle cx="12" cy="12" r="2.8" />
    </Base>
  );
}

/** Глаз перечёркнутый (скрыть пароль). */
export function IconEyeOff(props: IconProps) {
  return (
    <Base {...props}>
      <path d="M4 4l16 16" />
      <path d="M9.9 5.9A9.6 9.6 0 0 1 12 5.5c6 0 9.5 6.5 9.5 6.5a17.6 17.6 0 0 1-3.2 3.9M6.1 8A17 17 0 0 0 2.5 12S6 18.5 12 18.5a9 9 0 0 0 3.4-.7" />
      <path d="M9.2 9.4a2.9 2.9 0 0 0 4 4" />
    </Base>
  );
}

/** Импорт данных (стрелка в лоток). */
export function IconImport(props: IconProps) {
  return (
    <Base {...props}>
      <path d="M12 3v10" />
      <path d="m8 9.5 4 4 4-4" />
      <path d="M4 15v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3" />
    </Base>
  );
}

/** Предупреждение (треугольник). */
export function IconWarning(props: IconProps) {
  return (
    <Base {...props}>
      <path d="M12 4 2.8 19.5h18.4L12 4Z" />
      <path d="M12 10v4.2" />
      <path d="M12 17.3h.01" />
    </Base>
  );
}

/** Информация (круг). */
export function IconInfo(props: IconProps) {
  return (
    <Base {...props}>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 11v5" />
      <path d="M12 7.8h.01" />
    </Base>
  );
}

/** Галочка в круге (успех). */
export function IconCheckCircle(props: IconProps) {
  return (
    <Base {...props}>
      <circle cx="12" cy="12" r="8.5" />
      <path d="m8.5 12.2 2.4 2.4 4.6-5" />
    </Base>
  );
}

/** Календарь. */
export function IconCalendar(props: IconProps) {
  return (
    <Base {...props}>
      <rect x="4" y="5.5" width="16" height="15" rx="2" />
      <path d="M8 3.5v4M16 3.5v4M4 10.5h16" />
    </Base>
  );
}

/** Пересадка (две стрелки). */
export function IconTransfer(props: IconProps) {
  return (
    <Base {...props}>
      <path d="M7 8h11" />
      <path d="m15 4.5 3.5 3.5L15 11.5" />
      <path d="M17 16H6" />
      <path d="m9 12.5L5.5 16 9 19.5" />
    </Base>
  );
}
