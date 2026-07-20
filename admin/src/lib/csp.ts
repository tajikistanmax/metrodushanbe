/**
 * Content-Security-Policy операционной консоли.
 *
 * Собирается на каждый запрос в src/proxy.ts, потому что содержит одноразовый
 * nonce. Обоснование nonce (bootstrap-скрипты Next + анти-FOUC скрипт темы),
 * компромисс по 'unsafe-inline' для style-атрибутов и переход на динамический
 * рендер — те же, что в портале; подробно см. web/src/lib/csp.ts. Консоль и
 * так целиком динамическая (сессия в cookie, серверные экшены), поэтому потеря
 * статического пререндера здесь ничего не стоит.
 *
 * Консоль встраивать в iframe нельзя ни при каких условиях (защита от
 * clickjacking на форме входа и в операционных экранах) — frame-ancestors 'none'.
 */

/** Базовый URL стиля карты; должен совпадать с lib/map-config.ts. */
const MAP_STYLE_URL =
  process.env.NEXT_PUBLIC_MAP_STYLE_URL ?? "https://tiles.openfreemap.org/styles/liberty";

function originOf(rawUrl: string): string | null {
  try {
    return new URL(rawUrl).origin;
  } catch {
    return null;
  }
}

function sources(...values: (string | null | undefined)[]): string {
  return [...new Set(values.filter((value): value is string => Boolean(value)))].join(" ");
}

export function buildCsp(
  nonce: string,
  { isDev, isSecure }: { isDev: boolean; isSecure: boolean },
): string {
  const mapOrigin = originOf(MAP_STYLE_URL);

  const directives = [
    "default-src 'self'",

    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${isDev ? " 'unsafe-eval'" : ""}`,

    // 'unsafe-inline' только для style-атрибутов (style={{...}} в React) —
    // сами таблицы стилей грузятся со своего origin (см. web/src/lib/csp.ts).
    "style-src 'self' 'unsafe-inline'",
    "style-src-elem 'self'",
    "style-src-attr 'unsafe-inline'",

    // Карта: тайлы/спрайты с хоста стиля; blob:/data: — канвасы MapLibre и
    // inline-иконки. data: покрывает и login-bg при возможной инлайн-подаче;
    // сейчас /login-bg.jpg отдаётся со своего origin ('self').
    sources("img-src 'self' data: blob:", mapOrigin),

    // Шрифты Montserrat самохостятся через @fontsource.
    "font-src 'self'",

    // Браузер консоли ходит на свой origin (серверные экшены, API-роут
    // импортов) и на хост стиля карты (style JSON, глифы, векторные тайлы).
    sources("connect-src 'self'", mapOrigin),

    // MapLibre поднимает воркеры из blob:-URL.
    "worker-src 'self' blob:",

    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
    "frame-src 'none'",
  ];

  if (isSecure) {
    directives.push("upgrade-insecure-requests");
  }

  return directives.join("; ");
}
