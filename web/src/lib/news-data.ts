/**
 * Загрузка публичных новостей (ТЗ §6.2.7) с офлайн-деградацией
 * (dev-conventions.md, §8): GET {NEXT_PUBLIC_API_BASE}/news[/{slug}] с
 * таймаутом API_TIMEOUT_MS (общий хелпер fetchApiJson из lib/api.ts).
 * При ЛЮБОЙ ошибке (нет сети, не 2xx — в т.ч. 404 news.not_found, таймаут,
 * битый JSON) список деградирует в [], а статья — в null: портал обязан
 * жить без backend.
 */

import { fetchApiJson } from "./api";
import type { I18nName, NewsArticle } from "./types";
import type { Lang } from "./i18n";

/** Мультиязычное поле: все три языка обязательны и являются строками. */
function isI18nName(value: unknown): value is I18nName {
  const candidate = value as I18nName | null;
  return (
    typeof candidate === "object" &&
    candidate !== null &&
    typeof candidate.tg === "string" &&
    typeof candidate.ru === "string" &&
    typeof candidate.en === "string"
  );
}

/**
 * Проверка элемента ответа по контракту NewsArticle: рендер не должен падать
 * на данных, которые контракт не описывает. Невалидные slug / title / body /
 * publishedAt или coverMediaUrl не-строка-и-не-null — статья отбрасывается
 * целиком (null). Отсутствующий coverMediaUrl нормализуется в null.
 */
function parseNewsArticle(value: unknown): NewsArticle | null {
  const candidate = value as NewsArticle | null;
  if (
    !candidate ||
    typeof candidate !== "object" ||
    typeof candidate.slug !== "string" ||
    !isI18nName(candidate.title) ||
    !isI18nName(candidate.body) ||
    typeof candidate.publishedAt !== "string" ||
    (candidate.coverMediaUrl != null &&
      typeof candidate.coverMediaUrl !== "string")
  ) {
    return null;
  }
  return {
    slug: candidate.slug,
    title: candidate.title,
    body: candidate.body,
    coverMediaUrl: candidate.coverMediaUrl ?? null,
    publishedAt: candidate.publishedAt,
  };
}

/**
 * Возвращает опубликованные новости в порядке, который отдаёт API
 * (published_at по убыванию), либо пустой массив при недоступном backend.
 */
export async function loadNews(): Promise<NewsArticle[]> {
  try {
    const payload = await fetchApiJson("/news");
    if (!Array.isArray(payload)) {
      throw new Error("Ответ /news не является массивом");
    }
    return payload
      .map(parseNewsArticle)
      .filter((article): article is NewsArticle => article !== null);
  } catch {
    // Офлайн-принцип: любая ошибка — просто нет новостей
    return [];
  }
}

/**
 * Возвращает опубликованную статью по слагу либо null (нет статьи, не
 * опубликована — 404 news.not_found, недоступный backend, битый ответ).
 */
export async function loadNewsArticle(
  slug: string,
): Promise<NewsArticle | null> {
  try {
    const payload = await fetchApiJson(`/news/${encodeURIComponent(slug)}`);
    return parseNewsArticle(payload);
  } catch {
    return null;
  }
}

/** Соответствие языка UI и локали Intl для форматирования дат. */
const DATE_LOCALES: Record<Lang, string> = {
  tg: "tg-TJ",
  ru: "ru-RU",
  en: "en-GB",
};

/**
 * Дата публикации в формате текущего языка (день, месяц прописью, год).
 * Intl сам деградирует на неподдерживаемой локали (tg); при невалидной дате
 * возвращает исходную строку, чтобы ничего не потерять на экране.
 */
export function formatNewsDate(iso: string, lang: Lang): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso;
  }
  return new Intl.DateTimeFormat(DATE_LOCALES[lang], {
    year: "numeric",
    month: "long",
    day: "numeric",
  }).format(date);
}
