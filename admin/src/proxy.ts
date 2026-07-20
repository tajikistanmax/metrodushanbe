/**
 * Слой запроса операционной консоли: session-guard + заголовки безопасности.
 *
 * Guard: все страницы, кроме /login, требуют валидной сессионной cookie. Полная
 * проверка авторизации и ролей дополнительно выполняется в серверном layout и
 * Server Actions (см. lib/server-auth.ts) — этот слой лишь отсекает очевидно
 * неаутентифицированные запросы пораньше.
 *
 * Заголовки безопасности уровня запроса (то, что нельзя выставить статически в
 * next.config.ts):
 *  - CSP с одноразовым nonce (см. lib/csp.ts);
 *  - HSTS — только поверх настоящего TLS.
 * Статические заголовки (nosniff, X-Frame-Options, Referrer-Policy,
 * Permissions-Policy) остаются в next.config.ts.
 *
 * В Next 16 файловая конвенция middleware переименована в proxy
 * (node_modules/next/dist/docs → 01-app/03-api-reference/03-file-conventions/proxy.md).
 *
 * Статика (_next, favicon, изображения) не проходит через этот слой — см.
 * matcher ниже, поэтому CSP навешивается ровно на документы и API-роуты.
 */

import { NextRequest, NextResponse } from "next/server";
import { SESSION_COOKIE, verifySessionToken } from "@/lib/auth";
import { buildCsp } from "@/lib/csp";

/**
 * Режим наблюдения: при CSP_REPORT_ONLY=1 политика уходит в заголовке
 * Content-Security-Policy-Report-Only — браузер не блокирует нарушения, лишь
 * пишет их в консоль DevTools. Внешний report-uri сознательно не подключён.
 * Порядок использования: поднять консоль с CSP_REPORT_ONLY=1, пройти карту/
 * тему, собрать «Refused to ...» из консоли, расширить lib/csp.ts, убрать
 * переменную.
 */
const REPORT_ONLY = process.env.CSP_REPORT_ONLY === "1";

/** Год в секундах — стандартный max-age для HSTS. */
const HSTS_MAX_AGE = 31_536_000;

/**
 * TLS ли это на самом деле. Учитываем X-Forwarded-Proto: в контейнерном
 * деплое TLS терминируется на реверс-прокси, и до Next доходит http. HSTS на
 * http://localhost заблокировал бы разработчику домен в браузере намертво.
 */
function isSecureRequest(request: NextRequest): boolean {
  const forwarded = request.headers.get("x-forwarded-proto");
  if (forwarded) {
    return forwarded.split(",")[0]!.trim().toLowerCase() === "https";
  }
  return request.nextUrl.protocol === "https:";
}

/**
 * Навешивает CSP (и HSTS на TLS) на ответ. Nonce одновременно кладётся в
 * заголовки запроса — по ним Next проставит nonce своим inline-скриптам, а
 * layout прочитает x-nonce для скрипта темы. Для редиректов requestHeaders не
 * важны (документ не рендерится), но CSP на ответе безвреден и единообразен.
 */
function withSecurityHeaders(request: NextRequest, requestHeaders?: Headers): NextResponse {
  const isDev = process.env.NODE_ENV === "development";
  const isSecure = isSecureRequest(request);
  const nonce = crypto.randomUUID().replaceAll("-", "");
  const csp = buildCsp(nonce, { isDev, isSecure });

  let response: NextResponse;
  if (requestHeaders) {
    requestHeaders.set("x-nonce", nonce);
    requestHeaders.set("Content-Security-Policy", csp);
    response = NextResponse.next({ request: { headers: requestHeaders } });
  } else {
    response = NextResponse.next();
  }

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

export async function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const requestHeaders = new Headers(request.headers);

  // Upload API returns JSON 401/403 itself; redirecting it would turn fetch errors into HTML.
  if (pathname.startsWith("/api/admin/imports/")) {
    return withSecurityHeaders(request, requestHeaders);
  }

  const cookie = request.cookies.get(SESSION_COOKIE)?.value ?? "";
  const authenticated = cookie !== "" && (await verifySessionToken(cookie)) !== null;

  // Не редиректим с /login только по локальной подписи cookie: роль/активность
  // могла измениться, и такой redirect создавал бесконечный цикл с server guard.
  if (pathname === "/login") {
    return withSecurityHeaders(request, requestHeaders);
  }

  if (!authenticated) {
    const redirect = NextResponse.redirect(new URL("/login", request.url));
    // CSP/HSTS на редиректе тоже полезны (защита промежуточного ответа).
    for (const [key, value] of withSecurityHeaders(request).headers) {
      if (key === "content-security-policy" || key === "content-security-policy-report-only" || key === "strict-transport-security") {
        redirect.headers.set(key, value);
      }
    }
    return redirect;
  }
  return withSecurityHeaders(request, requestHeaders);
}

export const config = {
  // Всё, кроме служебных путей Next и статических файлов.
  matcher: [
    "/((?!_next/static|_next/image|favicon\\.ico|login-bg\\.jpg|data/|.*\\.(?:svg|png|jpg|jpeg|webp|ico)$).*)",
  ],
};
