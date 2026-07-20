/**
 * Заголовки безопасности уровня запроса для портала.
 *
 * В Next 16 файловая конвенция middleware переименована в proxy
 * (node_modules/next/dist/docs → 01-app/03-api-reference/03-file-conventions/proxy.md),
 * поэтому файл называется proxy.ts и экспортирует функцию `proxy`.
 *
 * Здесь живёт то, что нельзя выставить статически в next.config.ts:
 *  - CSP с одноразовым nonce (см. lib/csp.ts — там же обоснование);
 *  - HSTS, который допустим ТОЛЬКО поверх настоящего TLS.
 *
 * Статические заголовки (nosniff, X-Frame-Options, Referrer-Policy,
 * Permissions-Policy) остаются в next.config.ts и здесь не дублируются.
 */

import { NextResponse, type NextRequest } from "next/server";
import { buildCsp } from "@/lib/csp";

/**
 * Режим наблюдения: при CSP_REPORT_ONLY=1 политика уходит в заголовке
 * Content-Security-Policy-Report-Only — браузер НЕ блокирует нарушения, а лишь
 * пишет их в консоль DevTools. Отчёты никуда не отправляются: внешний
 * report-uri сознательно не подключён, чтобы не отдавать телеметрию третьей
 * стороне. Порядок использования: поднять портал с CSP_REPORT_ONLY=1, пройти
 * карту/тему/PWA, собрать «Refused to ...» из консоли, расширить lib/csp.ts,
 * затем убрать переменную и вернуться к блокирующему режиму.
 */
const REPORT_ONLY = process.env.CSP_REPORT_ONLY === "1";

/** Год в секундах — стандартный max-age для HSTS. */
const HSTS_MAX_AGE = 31_536_000;

/**
 * TLS ли это на самом деле. Проверяем и схему запроса, и X-Forwarded-Proto:
 * в контейнерном деплое TLS терминируется на реверс-прокси, и до Next доходит
 * уже http. Ошибиться в другую сторону нельзя: HSTS, выставленный на
 * http://localhost, заставит браузер намертво запомнить домен как
 * https-only — разработчик заблокирует себе localhost, и откатывается это
 * только вручную через chrome://net-internals/#hsts.
 */
function isSecureRequest(request: NextRequest): boolean {
  const forwarded = request.headers.get("x-forwarded-proto");
  if (forwarded) {
    // Прокси может прислать список: "https,http" — значимо первое значение.
    return forwarded.split(",")[0]!.trim().toLowerCase() === "https";
  }
  return request.nextUrl.protocol === "https:";
}

export function proxy(request: NextRequest): NextResponse {
  const isDev = process.env.NODE_ENV === "development";
  const isSecure = isSecureRequest(request);
  const nonce = crypto.randomUUID().replaceAll("-", "");
  const csp = buildCsp(nonce, { isDev, isSecure });

  // Nonce и CSP кладём в заголовки ЗАПРОСА: по ним Next проставит nonce своим
  // <script>-тегам, а layout прочитает x-nonce через headers() для скрипта темы.
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("x-nonce", nonce);
  requestHeaders.set("Content-Security-Policy", csp);

  const response = NextResponse.next({ request: { headers: requestHeaders } });

  response.headers.set(
    REPORT_ONLY ? "Content-Security-Policy-Report-Only" : "Content-Security-Policy",
    csp,
  );

  if (isSecure) {
    response.headers.set(
      "Strict-Transport-Security",
      `max-age=${HSTS_MAX_AGE}; includeSubDomains`,
    );
  }

  return response;
}

export const config = {
  matcher: [
    {
      /*
       * Исключены: служебные пути Next, иконки и статика из public/.
       * Отдельно исключён /sw.js — заголовок CSP на теле service worker'а стал
       * бы политикой его собственного глобального скоупа и мог бы ограничить
       * офлайн-кеширование; сам файл отдаётся со своего origin и в защите
       * через CSP не нуждается.
       */
      source:
        "/((?!_next/static|_next/image|favicon\\.ico|sw\\.js|brand/|data/|.*\\.(?:svg|png|jpg|jpeg|webp|ico|geojson)$).*)",
      // Префетчи <Link> не рендерят документ — им CSP не нужен.
      missing: [
        { type: "header", key: "next-router-prefetch" },
        { type: "header", key: "purpose", value: "prefetch" },
      ],
    },
  ],
};
