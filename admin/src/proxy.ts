/**
 * Быстрый guard операционной консоли: все страницы, кроме /login, требуют
 * валидной сессионной cookie. Полная проверка авторизации и ролей дополнительно
 * выполняется в серверном layout и Server Actions (см. lib/server-auth.ts) —
 * этот слой лишь отсекает очевидно неаутентифицированные запросы пораньше.
 *
 * Статика (_next, favicon, /data) не проверяется — см. matcher ниже.
 */

import { NextRequest, NextResponse } from "next/server";
import { SESSION_COOKIE, verifySessionToken } from "@/lib/auth";

export async function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Upload API returns JSON 401/403 itself; redirecting it would turn fetch errors into HTML.
  if (pathname.startsWith("/api/admin/imports/")) {
    return NextResponse.next();
  }

  const cookie = request.cookies.get(SESSION_COOKIE)?.value ?? "";
  const authenticated = cookie !== "" && (await verifySessionToken(cookie)) !== null;

  // Не редиректим с /login только по локальной подписи cookie: роль/активность
  // могла измениться, и такой redirect создавал бесконечный цикл с server guard.
  if (pathname === "/login") {
    return NextResponse.next();
  }

  if (!authenticated) {
    return NextResponse.redirect(new URL("/login", request.url));
  }
  return NextResponse.next();
}

export const config = {
  // Всё, кроме служебных путей Next и статических файлов.
  matcher: [
    "/((?!_next/static|_next/image|favicon\\.ico|data/|.*\\.(?:svg|png|jpg|webp|ico)$).*)",
  ],
};
