import type { Lang } from "./i18n";

const DUSHANBE_TIME_ZONE = "Asia/Dushanbe";

function dateParts(date: Date, includeTime: boolean): Record<string, string> {
  return Object.fromEntries(
    new Intl.DateTimeFormat("en-GB", {
      timeZone: DUSHANBE_TIME_ZONE,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      ...(includeTime
        ? { hour: "2-digit" as const, minute: "2-digit" as const, hourCycle: "h23" as const }
        : {}),
    })
      .formatToParts(date)
      .filter((part) => part.type !== "literal")
      .map((part) => [part.type, part.value]),
  );
}

export function formatDushanbeDateTime(value: string, lang: Lang): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  const parts = dateParts(date, true);
  const separator = lang === "en" ? "/" : ".";
  return `${parts.day}${separator}${parts.month}${separator}${parts.year}, ${parts.hour}:${parts.minute}`;
}

const MONTHS: Record<Lang, string[]> = {
  tg: [
    "январи", "феврали", "марти", "апрели", "майи", "июни",
    "июли", "августи", "сентябри", "октябри", "ноябри", "декабри",
  ],
  ru: [
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
  ],
  en: [
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
  ],
};

export function formatDushanbeNewsDate(value: string, lang: Lang): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  const parts = dateParts(date, false);
  const month = MONTHS[lang][Number(parts.month) - 1];
  if (lang === "ru") {
    return `${Number(parts.day)} ${month} ${parts.year} г.`;
  }
  return `${Number(parts.day)} ${month} ${parts.year}`;
}
