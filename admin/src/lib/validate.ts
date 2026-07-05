/**
 * Чистые клиентские валидаторы, зеркалящие серверные гейты
 * (AdminSupport: полнота языков, формат цвета, координаты).
 * Возвращают примитивы/флаги; локализованные тексты подставляют формы.
 */

import type { I18nInput } from "./admin-forms";

export function isBlank(s: string | undefined | null): boolean {
  return !s || s.trim().length === 0;
}

/** Все три языка (tg/ru/en) непусты — BR-NET-4/BR-CMS-1. */
export function i18nComplete(v: I18nInput): boolean {
  return !isBlank(v.tg) && !isBlank(v.ru) && !isBlank(v.en);
}

/** Цвет строго #RRGGBB (зеркало @Pattern backend). */
export function isHexColor(s: string): boolean {
  return /^#[0-9A-Fa-f]{6}$/.test(s.trim());
}

/** Разбор числа из поля координаты; null если невалидно. */
export function parseNumber(s: string): number | null {
  if (isBlank(s)) return null;
  const n = Number(s.trim().replace(",", "."));
  return Number.isFinite(n) ? n : null;
}

/** slug: латиница/цифры/дефис (мягкая клиентская проверка). */
export function isSlug(s: string): boolean {
  return /^[a-z0-9]+(?:-[a-z0-9]+)*$/i.test(s.trim());
}

/** Простая проверка http(s) URL. */
export function isHttpUrl(s: string): boolean {
  try {
    const u = new URL(s.trim());
    return u.protocol === "http:" || u.protocol === "https:";
  } catch {
    return false;
  }
}

/**
 * Разбор трассы линии из текста: строки «lon, lat». Пустой текст → [] (нет пути).
 * Возвращает null, если хотя бы одна строка невалидна.
 */
export function parsePath(text: string): number[][] | null {
  const lines = text
    .split("\n")
    .map((l) => l.trim())
    .filter((l) => l.length > 0);
  if (lines.length === 0) return [];
  const out: number[][] = [];
  for (const line of lines) {
    const parts = line.split(/[,;\s]+/).filter(Boolean);
    if (parts.length !== 2) return null;
    const lon = parseNumber(parts[0]);
    const lat = parseNumber(parts[1]);
    if (lon === null || lat === null) return null;
    out.push([lon, lat]);
  }
  return out;
}

/** Обратно: массив точек → текст «lon, lat» по строкам (для префилла). */
export function pathToText(path: number[][] | undefined | null): string {
  if (!path || path.length === 0) return "";
  return path.map((p) => `${p[0]}, ${p[1]}`).join("\n");
}

/**
 * ISO-строка (Instant) из значения <input type="datetime-local"> (локальное
 * время без зоны). Пусто → null.
 */
export function localToIso(local: string): string | null {
  if (isBlank(local)) return null;
  const d = new Date(local);
  if (Number.isNaN(d.getTime())) return null;
  return d.toISOString();
}

/** Обратно: ISO → значение datetime-local (локальная зона), для префилла. */
export function isoToLocal(iso: string | null | undefined): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(
    d.getHours(),
  )}:${pad(d.getMinutes())}`;
}
