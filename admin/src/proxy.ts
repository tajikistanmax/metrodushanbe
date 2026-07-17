/**
 * Быстрый guard операционной консоли: все страницы, кроме /login, требуют
 * валидной сессионной cookie. Полная проверка авторизации дополнительно
 * выполняется в серверном layout и Server Actions (см. lib/server-auth.ts).
 *
 * Статика (_next, favicon, /data) не проверяется — см. matcher ниже.
 */

import { NextRequest, NextResponse } from "next/server";
import { SESSION_COOKIE, computeSessionToken, tokensEqual } from "@/lib/auth";

export async function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const cookie = request.cookies.get(SESSION_COOKIE)?.value ?? "";
  const expected = await computeSessionToken();
  const authenticated = cookie !== "" && tokensEqual(cookie, expected);

  // Авторизованного пользователя со страницы входа уводим в консоль.
  if (pathname === "/login") {
    if (authenticated) {
      return NextResponse.redirect(new URL("/", request.url));
    }
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
