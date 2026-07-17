"use server";

/**
 * Server Actions входа/выхода операционной консоли (см. lib/auth.ts).
 *
 * Пароль здесь не сверяется локально: его проверяет backend
 * (`POST /v1/admin/auth/login`), закрытый заголовком X-Admin-Key. Ключ читается
 * из серверного env и в клиентский бандл не попадает, поэтому эндпоинт входа
 * недоступен из браузера напрямую. В ответ backend отдаёт профиль с ролью, из
 * которого Next выпускает подписанную httpOnly-cookie — ни пароль, ни секрет
 * подписи браузер не видит.
 */

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { API_BASE } from "./api";
import { SESSION_COOKIE, createSessionToken, isAdminRole } from "./auth";

export type LoginState = {
  /** Код ошибки для локализации на клиенте; null — без ошибки. */
  error: "invalid" | "required" | "unavailable" | null;
};

/** Сутки/месяц в секундах — срок cookie без и с «запомнить меня». */
const DAY_S = 60 * 60 * 24;
const MONTH_S = DAY_S * 30;

/** Dev-ключ по умолчанию (совпадает с app.admin.dev-key backend). */
const DEFAULT_ADMIN_KEY = "dev-admin-key-change-me";

type LoginResponse = {
  user?: {
    username?: string;
    displayName?: string;
    role?: string;
    sessionVersion?: number;
  };
};

export async function login(
  _prev: LoginState,
  formData: FormData,
): Promise<LoginState> {
  const username = String(formData.get("username") ?? "").trim();
  const password = String(formData.get("password") ?? "");
  const remember = formData.get("remember") === "on";

  if (!username || !password) {
    return { error: "required" };
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE}/admin/auth/login`, {
      method: "POST",
      cache: "no-store",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        "X-Admin-Key": process.env.ADMIN_API_KEY ?? DEFAULT_ADMIN_KEY,
      },
      body: JSON.stringify({ username, password }),
    });
  } catch {
    // Backend недоступен — это не «неверный пароль», и путать их нельзя:
    // иначе оператор будет искать ошибку в своих учётных данных.
    return { error: "unavailable" };
  }

  if (response.status === 401) {
    return { error: "invalid" };
  }
  if (!response.ok) {
    return { error: "unavailable" };
  }

  const payload = (await response.json()) as LoginResponse;
  const user = payload.user;
  if (
    !user?.username ||
    !user.role ||
    !isAdminRole(user.role) ||
    typeof user.sessionVersion !== "number"
  ) {
    return { error: "unavailable" };
  }

  const token = await createSessionToken(
    {
      username: user.username,
      displayName: user.displayName ?? user.username,
      role: user.role,
      accountVersion: user.sessionVersion,
    },
    remember ? MONTH_S : DAY_S,
  );

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
