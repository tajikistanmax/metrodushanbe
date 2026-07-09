"use server";

/**
 * Server Actions входа/выхода операционной консоли (см. lib/auth.ts).
 * Пароль сверяется только здесь, на сервере; в браузер уходит лишь
 * httpOnly-cookie с HMAC-токеном — ни пароль, ни секрет в бандл не попадают.
 */

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import {
  SESSION_COOKIE,
  computeSessionToken,
  expectedPassword,
  expectedUser,
  tokensEqual,
} from "./auth";

export type LoginState = {
  /** Код ошибки для локализации на клиенте; null — без ошибки. */
  error: "invalid" | "required" | null;
};

/** Сутки/месяц в секундах — срок cookie без и с «запомнить меня». */
const DAY_S = 60 * 60 * 24;
const MONTH_S = DAY_S * 30;

export async function login(
  _prev: LoginState,
  formData: FormData,
): Promise<LoginState> {
  const user = String(formData.get("username") ?? "").trim();
  const password = String(formData.get("password") ?? "");
  const remember = formData.get("remember") === "on";

  if (!user || !password) {
    return { error: "required" };
  }
  // tokensEqual: сравнение фиксированного времени, без ранних выходов
  if (user !== expectedUser() || !tokensEqual(password, expectedPassword())) {
    return { error: "invalid" };
  }

  const token = await computeSessionToken();
  const store = await cookies();
  store.set(SESSION_COOKIE, token, {
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    path: "/",
    maxAge: remember ? MONTH_S : DAY_S,
  });
  redirect("/");
}

export async function logout(): Promise<void> {
  const store = await cookies();
  store.delete(SESSION_COOKIE);
  redirect("/login");
}
