"use client";

/**
 * Экран входа в операционную консоль: слева — брендовая сцена (ночной
 * Душанбе, арка-тоннель, флаг), справа — форма входа. Пароль проверяется
 * Server Action `login` (lib/auth-actions.ts); сюда возвращается только
 * код ошибки для локализации. Языки — общий I18nProvider.
 */

import { useActionState, useState } from "react";
import { LANG_LABELS, LANG_SHORT_LABELS, LANGS } from "@/lib/i18n";
import { login, type LoginState } from "@/lib/auth-actions";
import { IconEye, IconEyeOff, IconLock, IconShield, IconUser } from "@/lib/icons";
import BrandMark from "../BrandMark";
import { useI18n } from "../I18nProvider";
import LoginScene from "./LoginScene";

const INITIAL: LoginState = { error: null };

export default function LoginClient() {
  const { lang, setLang, dict } = useI18n();
  const [state, formAction, pending] = useActionState(login, INITIAL);
  const [showPassword, setShowPassword] = useState(false);
  const t = dict.login;

  const errorText =
    state.error === "invalid"
      ? t.errInvalid
      : state.error === "required"
        ? t.errRequired
        : null;

  return (
    <div className="relative flex min-h-dvh flex-col lg:flex-row">
      {/* Лента флага — государственная сигнатура */}
      <div className="ribbon-flag absolute inset-x-0 top-0 z-20" aria-hidden="true" />

      {/* Левая панель: бренд + сцена */}
      <section className="relative flex flex-col justify-between overflow-hidden bg-[#0b1622] text-surface-light lg:w-[54%]">
        <div className="relative z-10 px-8 pb-4 pt-10 sm:px-12">
          <div className="flex items-center gap-3">
            <BrandMark className="h-11 w-[48px] shrink-0" holeColor="#0b1622" />
            <div>
              <p className="text-lg font-extrabold uppercase tracking-[0.14em]">
                {t.brandLine1}
              </p>
              <p className="text-[11px] font-semibold uppercase tracking-[0.3em] text-surface-light/60">
                {t.brandLine2}
              </p>
            </div>
          </div>

          <h1 className="mt-10 max-w-xl text-3xl font-extrabold leading-tight sm:text-4xl">
            {t.heroTitle}{" "}
            <span className="text-[#7fb4e0]">{t.heroTitleAccent}</span>
          </h1>
          <p className="mt-4 max-w-md text-sm leading-relaxed text-surface-light/75">
            {t.heroLead}
          </p>

          <ul className="mt-6 flex flex-wrap gap-2" aria-label={t.heroBadgesLabel}>
            {[t.badgeSecure, t.badgeReliable, t.badgeAccessible].map((badge) => (
              <li
                key={badge}
                className="rounded-full border border-surface-light/25 bg-surface-light/10 px-3.5 py-1.5 text-xs font-semibold"
              >
                {badge}
              </li>
            ))}
          </ul>
        </div>

        {/* Сцена прижата к низу; на мобильных скрыта ради компактности */}
        <LoginScene className="relative z-0 hidden w-full lg:block" />

        <p className="relative z-10 hidden px-12 pb-6 text-[11px] text-surface-light/50 lg:block">
          {t.copyright}
        </p>
      </section>

      {/* Правая панель: форма входа */}
      <section className="flex flex-1 flex-col bg-page px-6 py-8 sm:px-12">
        {/* Переключатель языка */}
        <nav aria-label={dict.languageSwitcher} className="flex justify-end">
          <div
            role="group"
            aria-label={dict.languageSwitcher}
            className="flex gap-0.5 rounded-full border border-card-border bg-card p-1"
          >
            {LANGS.map((code) => (
              <button
                key={code}
                type="button"
                lang={code}
                aria-pressed={lang === code}
                aria-label={LANG_LABELS[code]}
                title={LANG_LABELS[code]}
                onClick={() => setLang(code)}
                className={
                  lang === code
                    ? "rounded-full bg-brand-navy px-3 py-1 text-xs font-bold text-surface-light"
                    : "rounded-full px-3 py-1 text-xs font-semibold text-text-secondary transition-colors hover:bg-brand-navy/10"
                }
              >
                {LANG_SHORT_LABELS[code]}
              </button>
            ))}
          </div>
        </nav>

        <div className="flex flex-1 items-center justify-center py-8">
          <div className="w-full max-w-md">
            <h2 className="text-center text-2xl font-extrabold sm:text-3xl">
              {t.title}
            </h2>
            <p className="mt-2 text-center text-sm text-text-secondary">
              {t.subtitle}
            </p>

            <form action={formAction} className="mt-8 flex flex-col gap-4" noValidate>
              {/* Логин */}
              <label className="block">
                <span className="mb-1.5 block text-sm font-semibold">
                  {t.usernameLabel}
                </span>
                <span className="relative block">
                  <IconUser className="pointer-events-none absolute left-3.5 top-1/2 h-5 w-5 -translate-y-1/2 text-text-secondary" />
                  <input
                    type="text"
                    name="username"
                    autoComplete="username"
                    required
                    placeholder={t.usernamePlaceholder}
                    className="w-full rounded-xl border border-card-border bg-card py-3 pl-11 pr-4 text-sm font-medium outline-none transition-colors placeholder:text-text-secondary/60 focus:border-info"
                  />
                </span>
              </label>

              {/* Пароль */}
              <label className="block">
                <span className="mb-1.5 block text-sm font-semibold">
                  {t.passwordLabel}
                </span>
                <span className="relative block">
                  <IconLock className="pointer-events-none absolute left-3.5 top-1/2 h-5 w-5 -translate-y-1/2 text-text-secondary" />
                  <input
                    type={showPassword ? "text" : "password"}
                    name="password"
                    autoComplete="current-password"
                    required
                    placeholder={t.passwordPlaceholder}
                    className="w-full rounded-xl border border-card-border bg-card py-3 pl-11 pr-12 text-sm font-medium outline-none transition-colors placeholder:text-text-secondary/60 focus:border-info"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((v) => !v)}
                    aria-label={showPassword ? t.hidePassword : t.showPassword}
                    aria-pressed={showPassword}
                    className="absolute right-2 top-1/2 -translate-y-1/2 rounded-lg p-2 text-text-secondary transition-colors hover:bg-brand-navy/5 hover:text-ink"
                  >
                    {showPassword ? (
                      <IconEyeOff className="h-5 w-5" />
                    ) : (
                      <IconEye className="h-5 w-5" />
                    )}
                  </button>
                </span>
              </label>

              {/* Запомнить меня */}
              <label className="flex cursor-pointer items-center gap-2.5 text-sm">
                <input
                  type="checkbox"
                  name="remember"
                  className="h-4 w-4 accent-[var(--brand-navy)]"
                />
                <span className="font-medium text-text-secondary">{t.remember}</span>
              </label>

              {/* Ошибка */}
              {errorText && (
                <p
                  role="alert"
                  className="rounded-xl border border-brand-red/30 bg-brand-red/10 px-4 py-3 text-sm font-semibold text-brand-red"
                >
                  {errorText}
                </p>
              )}

              <button
                type="submit"
                disabled={pending}
                className="mt-1 rounded-xl bg-brand-navy px-4 py-3.5 text-sm font-bold text-surface-light transition-opacity hover:opacity-90 disabled:opacity-60"
              >
                {pending ? t.submitting : t.submit}
              </button>
            </form>

            {/* Разделитель и задел SSO (Keycloak — следующая фаза по ТЗ §9.2) */}
            <div className="mt-6 flex items-center gap-3 text-xs text-text-secondary">
              <span className="h-px flex-1 bg-card-border" aria-hidden="true" />
              {t.or}
              <span className="h-px flex-1 bg-card-border" aria-hidden="true" />
            </div>
            <p
              className="mt-4 flex items-center justify-center gap-2 rounded-xl border border-dashed border-card-border px-4 py-3 text-center text-xs font-medium text-text-secondary"
              title={t.ssoHintTitle}
            >
              <IconShield className="h-4 w-4 shrink-0" />
              {t.ssoHint}
            </p>

            <p className="mt-8 text-center text-xs text-text-secondary">
              {t.help}
            </p>
          </div>
        </div>

        <p className="text-center text-[11px] text-text-secondary lg:hidden">
          {t.copyright}
        </p>
      </section>
    </div>
  );
}
