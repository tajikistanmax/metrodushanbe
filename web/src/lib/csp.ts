/**
 * Content-Security-Policy портала.
 *
 * Политика собирается на КАЖДЫЙ запрос в src/proxy.ts, потому что в неё входит
 * одноразовый nonce. Nonce нужен по двум причинам:
 *
 *  1. Next инлайнит в HTML свои bootstrap-скрипты (`self.__next_f.push([...])`)
 *     с данными RSC-потока. Их содержимое зависит от страницы и меняется на
 *     каждой сборке, поэтому hash-подход (`'sha256-...'`) для них неприменим —
 *     остаётся nonce. Next сам проставляет nonce на свои теги, разбирая
 *     заголовок CSP из запроса (см. node_modules/next/dist/docs →
 *     01-app/02-guides/content-security-policy.md, раздел «How nonces work»).
 *  2. Анти-FOUC скрипт темы (THEME_INIT_SCRIPT) вставляется через
 *     dangerouslySetInnerHTML и обязан выполниться ДО первой отрисовки —
 *     ему nonce передаётся вручную в app/layout.tsx.
 *
 * Цена решения: страницы с nonce обязаны рендериться динамически (на статике
 * nonce взяться неоткуда). Для портала это дёшево — все страницы и так лишь
 * тонкие оболочки над клиентскими компонентами, данные тянутся с API в
 * браузере, поэтому build-time пререндер не экономил ничего, кроме рендера
 * пустого каркаса.
 */

/** Базовый URL стиля карты; должен совпадать с NetworkMap.tsx. */
const MAP_STYLE_URL =
  process.env.NEXT_PUBLIC_MAP_STYLE_URL ?? "https://tiles.openfreemap.org/styles/liberty";

/** База API бэкенда; должна совпадать с lib/api.ts. */
const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080/api/v1";

/**
 * Origin из URL. CSP оперирует источниками, а не путями: у стиля карты путь
 * /styles/liberty, но спрайты и глифы лежат по другим путям того же хоста.
 */
function originOf(rawUrl: string): string | null {
  try {
    return new URL(rawUrl).origin;
  } catch {
    return null;
  }
}

/** Уникальные непустые источники в порядке добавления. */
function sources(...values: (string | null | undefined)[]): string {
  return [...new Set(values.filter((value): value is string => Boolean(value)))].join(" ");
}

export function buildCsp(
  nonce: string,
  { isDev, isSecure }: { isDev: boolean; isSecure: boolean },
): string {
  const mapOrigin = originOf(MAP_STYLE_URL);
  const apiOrigin = originOf(API_BASE);

  const directives = [
    "default-src 'self'",

    // 'strict-dynamic': скрипты, загруженные доверенным (nonce) кодом, наследуют
    // доверие — так работает подгрузка чанков рантаймом Next. В dev React
    // использует eval для восстановления серверных стеков ошибок, в проде — нет.
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${isDev ? " 'unsafe-eval'" : ""}`,

    // Стили — единственное место, где пришлось оставить 'unsafe-inline'.
    // Причина: React-пропс style={{...}} превращается в HTML-атрибут style="...",
    // а атрибуты стилей nonce'ом не покрываются в принципе (nonce применим
    // только к тегам <style>/<link>). В портале ~11 таких мест: цвета линий,
    // плашки алертов, легенда — значения вычисляются из данных сети.
    // Гранулярные директивы ниже сужают послабление до одних лишь атрибутов:
    // сами таблицы стилей грузятся только со своего origin. Браузеры, не
    // знающие style-src-elem/attr, откатываются на style-src.
    "style-src 'self' 'unsafe-inline'",
    "style-src-elem 'self'",
    "style-src-attr 'unsafe-inline'",

    // Карта: спрайты/растровые тайлы приходят с хоста стиля; blob: и data: —
    // это отрисованные MapLibre канвасы и inline-иконки маркеров.
    sources("img-src 'self' data: blob:", mapOrigin),

    // Шрифты Montserrat самохостятся через @fontsource — только свой origin.
    "font-src 'self'",

    // fetch/XHR: свой origin (в т.ч. /data/demo-network.geojson), API бэкенда
    // и хост стиля карты (style JSON, глифы, векторные тайлы).
    sources("connect-src 'self'", apiOrigin, mapOrigin),

    // MapLibre поднимает воркеры из blob:-URL; сюда же попадает /sw.js.
    "worker-src 'self' blob:",

    // manifest.webmanifest отдаётся своим же роутом.
    "manifest-src 'self'",

    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",

    // Портал не должен встраиваться в чужие страницы — это и есть защита от
    // clickjacking, современная замена X-Frame-Options.
    "frame-ancestors 'none'",
    "frame-src 'none'",
  ];

  // upgrade-insecure-requests — только на настоящем TLS. Ключимся по реальной
  // схеме запроса, а не по NODE_ENV: иначе директива либо сломала бы dev
  // (браузер молча переписал бы http://localhost:8080 в https), либо, что
  // хуже, prod-сборку, случайно поднятую по http для отладки.
  if (isSecure) {
    directives.push("upgrade-insecure-requests");
  }

  return directives.join("; ");
}
