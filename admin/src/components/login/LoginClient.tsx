"use client";

/**
 * Экран входа по утверждённому макету (photo/ChatGPT Image … 13_07_19):
 * фоновая фотография ночного Душанбе (admin/public/login-bg.jpg — сжатая
 * копия photo/a889422c…png), слева — белый логотип, приветствие и бейдж
 * «Безопасно • Надёжно • Удобно», справа — белая карточка формы с синими
 * акцентами. Пароль проверяется Server Action `login` (lib/auth-actions.ts);
 * сюда возвращается только код ошибки. Языки — общий I18nProvider.
 *
 * Карточка намеренно всегда светлая (не зависит от темы): она лежит поверх
 * фотографии, как в макете.
 */

import Image from "next/image";
import { useActionState, useEffect, useRef, useState } from "react";
import { LANG_LABELS, LANGS, type Lang } from "@/lib/i18n";
import { login, type LoginState } from "@/lib/auth-actions";
import {
  IconArrowRight,
  IconEye,
  IconEyeOff,
  IconLock,
  IconShield,
  IconUser,
} from "@/lib/icons";
import BrandMark from "../BrandMark";
import { useI18n } from "../I18nProvider";

const INITIAL: LoginState = { error: null };

/**
 * Мини-флаг языка для пилюли-переключателя (как в макете: «🇷🇺 Русский»).
 * SVG вместо эмодзи: флаговые эмодзи в Windows отображаются буквами.
 */
function FlagIcon({ code }: { code: Lang }) {
  const common = "h-3.5 w-5 shrink-0 rounded-[2.5px]";
  if (code === "tg") {
    return (
      <svg viewBox="0 0 20 14" className={common} aria-hidden="true">
        <rect width="20" height="14" fill="#ffffff" />
        <rect width="20" height="4" fill="#e21b2d" />
        <rect y="10" width="20" height="4" fill="#138a3d" />
        <circle cx="10" cy="7" r="1.1" fill="#f8c300" />
      </svg>
    );
  }
  if (code === "ru") {
    return (
      <svg viewBox="0 0 20 14" className={common} aria-hidden="true">
        <rect width="20" height="14" fill="#ffffff" />
        <rect y="4.7" width="20" height="4.6" fill="#0039a6" />
        <rect y="9.3" width="20" height="4.7" fill="#d52b1e" />
      </svg>
    );
  }
  return (
    <svg viewBox="0 0 20 14" className={common} aria-hidden="true">
      <rect width="20" height="14" fill="#012169" />
      <path d="M0 0 20 14M20 0 0 14" stroke="#ffffff" strokeWidth="2.8" />
      <path d="M0 0 20 14M20 0 0 14" stroke="#c8102e" strokeWidth="1.2" />
      <path d="M10 0v14M0 7h20" stroke="#ffffff" strokeWidth="4.6" />
      <path d="M10 0v14M0 7h20" stroke="#c8102e" strokeWidth="2.6" />
    </svg>
  );
}

/** Синий акцент формы входа — цвет CTA из утверждённого макета. */
const BLUE = "#2563eb";

export default function LoginClient() {
  const { lang, setLang, dict } = useI18n();
  const [state, formAction, pending] = useActionState(login, INITIAL);
  const [showPassword, setShowPassword] = useState(false);
  const [langOpen, setLangOpen] = useState(false);
  const [note, setNote] = useState<"sso" | "forgot" | null>(null);
  const langRef = useRef<HTMLDivElement>(null);
  const t = dict.login;

  // Закрытие меню языка по клику вне
  useEffect(() => {
    function onPointerDown(e: PointerEvent) {
      if (langRef.current && !langRef.current.contains(e.target as Node)) {
        setLangOpen(false);
      }
    }
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, []);

  const errorText =
    state.error === "invalid"
      ? t.errInvalid
      : state.error === "required"
        ? t.errRequired
        : state.error === "unavailable"
          ? t.errUnavailable
          : null;

  return (
    <div className="relative min-h-dvh overflow-hidden bg-[#0b1622]">
      {/* Фон: ночной Душанбе с поездом (сжатая копия референса из photo/) */}
      <Image
        src="/login-bg.jpg"
        alt=""
        fill
        priority
        sizes="100vw"
        className="object-cover"
        aria-hidden="true"
      />
      {/* Градиент для читаемости текста поверх фотографии */}
      <div
        className="absolute inset-0 bg-gradient-to-r from-[#050d16]/85 via-[#050d16]/30 to-[#050d16]/55"
        aria-hidden="true"
      />

      {/* Переключатель языка — пилюля с флагом (верхний правый угол) */}
      <div ref={langRef} className="absolute right-5 top-5 z-30 sm:right-8 sm:top-7">
        <button
          type="button"
          onClick={() => setLangOpen((v) => !v)}
          aria-expanded={langOpen}
          aria-haspopup="listbox"
          aria-label={dict.languageSwitcher}
          className="flex items-center gap-2 rounded-xl border border-white/25 bg-[#0b1622]/55 px-4 py-2.5 text-sm font-semibold text-white backdrop-blur-md transition-colors hover:bg-[#0b1622]/75"
        >
          <FlagIcon code={lang} />
          {LANG_LABELS[lang]}
          <svg
            viewBox="0 0 24 24"
            className={`h-4 w-4 transition-transform ${langOpen ? "rotate-180" : ""}`}
            fill="none"
            stroke="currentColor"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="m6 9 6 6 6-6" />
          </svg>
        </button>
        {langOpen && (
          <ul
            role="listbox"
            aria-label={dict.languageSwitcher}
            className="absolute right-0 top-full mt-2 w-44 overflow-hidden rounded-xl border border-white/20 bg-[#0d1c2c]/95 py-1.5 shadow-[0_16px_48px_rgba(0,0,0,0.5)] backdrop-blur-md"
          >
            {LANGS.map((code) => (
              <li key={code} role="option" aria-selected={lang === code}>
                <button
                  type="button"
                  lang={code}
                  onClick={() => {
                    setLang(code);
                    setLangOpen(false);
                  }}
                  className={
                    lang === code
                      ? "flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-sm font-bold text-white"
                      : "flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-sm font-semibold text-white/70 transition-colors hover:bg-white/10 hover:text-white"
                  }
                >
                  <FlagIcon code={code} />
                  {LANG_LABELS[code]}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* Контент */}
      <div className="relative z-10 mx-auto flex min-h-dvh w-full max-w-[1440px] flex-col px-6 pb-6 pt-8 sm:px-10 lg:px-14">
        <div className="flex flex-1 flex-col gap-10 lg:grid lg:grid-cols-[minmax(0,1fr)_minmax(420px,560px)] lg:items-stretch lg:gap-16">
          {/* Левая колонка: логотип, приветствие, бейдж */}
          <div className="flex flex-col text-white">
            {/* Белый логотип (монохром поверх фотографии) */}
            <div className="flex items-center gap-4">
              <BrandMark
                monochrome
                holeColor="rgba(0,0,0,0)"
                className="h-16 w-[70px] shrink-0 text-white"
              />
              <p className="text-2xl font-extrabold uppercase leading-[1.08] tracking-wide">
                Dushanbe
                <br />
                Metro
                <span className="mt-2 block h-px w-24 bg-white/40" aria-hidden="true" />
              </p>
            </div>

            <div className="mt-10 lg:mt-16">
              <h1 className="text-3xl font-extrabold leading-tight sm:text-[42px] sm:leading-[1.15]">
                {t.welcome1}
                <br />
                {t.welcome2Prefix}
                <span className="text-[#4d8dff]">{t.welcome2Accent}</span>
              </h1>
              <p className="mt-5 max-w-md text-sm leading-relaxed text-white/75 sm:text-[15px]">
                {t.heroLead}
              </p>
            </div>

            {/* Бейдж прижат к низу — как в макете, над футером */}
            <div className="mt-auto hidden pt-10 lg:block">
              <span className="inline-flex items-center gap-2.5 rounded-lg bg-[#0b1622]/70 px-4 py-2.5 text-[13px] font-semibold backdrop-blur-md">
                <IconShield className="h-[18px] w-[18px] text-[#4d8dff]" />
                {t.badgeSecure} • {t.badgeReliable} • {t.badgeConvenient}
              </span>
            </div>
          </div>

          {/* Правая колонка: карточка формы (всегда светлая, как в макете) */}
          <div className="flex items-center lg:py-10">
            <div className="w-full rounded-[28px] bg-white p-7 shadow-[0_24px_80px_rgba(3,10,20,0.5)] sm:p-10">
              <h2 className="text-center text-[28px] font-extrabold tracking-tight text-[#0b1b33] sm:text-[32px]">
                {t.title}
              </h2>
              <p className="mt-1.5 text-center text-sm text-[#5b6b7f]">{t.subtitle}</p>

              <form action={formAction} className="mt-8 flex flex-col gap-4" noValidate>
                {/* Логин */}
                <label className="flex items-center gap-3.5 rounded-2xl border border-[#e2e8f0] bg-white p-3 pr-4 transition-colors focus-within:border-[#2563eb]">
                  <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-[#eaf1ff] text-[#2563eb]">
                    <IconUser className="h-5 w-5" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block text-[13px] font-bold text-[#0b1b33]">
                      {t.usernameLabel}
                    </span>
                    <input
                      type="text"
                      name="username"
                      autoComplete="username"
                      required
                      placeholder={t.usernamePlaceholder}
                      className="w-full bg-transparent text-sm font-medium text-[#0b1b33] outline-none placeholder:text-[#93a1b3]"
                    />
                  </span>
                </label>

                {/* Пароль */}
                <label className="flex items-center gap-3.5 rounded-2xl border border-[#e2e8f0] bg-white p-3 pr-2.5 transition-colors focus-within:border-[#2563eb]">
                  <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-[#eaf1ff] text-[#2563eb]">
                    <IconLock className="h-5 w-5" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block text-[13px] font-bold text-[#0b1b33]">
                      {t.passwordLabel}
                    </span>
                    <input
                      type={showPassword ? "text" : "password"}
                      name="password"
                      autoComplete="current-password"
                      required
                      placeholder={t.passwordPlaceholder}
                      className="w-full bg-transparent text-sm font-medium text-[#0b1b33] outline-none placeholder:text-[#93a1b3]"
                    />
                  </span>
                  <button
                    type="button"
                    onClick={() => setShowPassword((v) => !v)}
                    aria-label={showPassword ? t.hidePassword : t.showPassword}
                    aria-pressed={showPassword}
                    className="shrink-0 rounded-lg p-2 text-[#93a1b3] transition-colors hover:bg-[#f1f5f9] hover:text-[#0b1b33]"
                  >
                    {showPassword ? (
                      <IconEyeOff className="h-5 w-5" />
                    ) : (
                      <IconEye className="h-5 w-5" />
                    )}
                  </button>
                </label>

                {/* Запомнить меня / Забыли пароль */}
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <label className="flex cursor-pointer items-center gap-2.5 text-sm">
                    <input
                      type="checkbox"
                      name="remember"
                      defaultChecked
                      className="h-4 w-4 rounded accent-[#2563eb]"
                    />
                    <span className="font-medium text-[#3c4c60]">{t.remember}</span>
                  </label>
                  <button
                    type="button"
                    onClick={() => setNote(note === "forgot" ? null : "forgot")}
                    className="text-sm font-semibold text-[#2563eb] hover:underline"
                  >
                    {t.forgot}
                  </button>
                </div>
                {note === "forgot" && (
                  <p className="rounded-xl bg-[#f1f5f9] px-4 py-3 text-xs leading-relaxed text-[#3c4c60]">
                    {t.forgotNote}
                  </p>
                )}

                {/* Ошибка */}
                {errorText && (
                  <p
                    role="alert"
                    className="rounded-xl border border-[#f5b5bc] bg-[#fdecee] px-4 py-3 text-sm font-semibold text-[#c31424]"
                  >
                    {errorText}
                  </p>
                )}

                <button
                  type="submit"
                  disabled={pending}
                  className="mt-1 flex items-center justify-center gap-2.5 rounded-xl py-4 text-[15px] font-bold text-white transition-colors disabled:opacity-60"
                  style={{ background: BLUE }}
                >
                  <IconArrowRight className="h-5 w-5" />
                  {pending ? t.submitting : t.submit}
                </button>
              </form>

              {/* Разделитель */}
              <div className="mt-6 flex items-center gap-3 text-xs font-medium text-[#93a1b3]">
                <span className="h-px flex-1 bg-[#e2e8f0]" aria-hidden="true" />
                {t.or}
                <span className="h-px flex-1 bg-[#e2e8f0]" aria-hidden="true" />
              </div>

              {/* SSO (Keycloak — следующая фаза по ТЗ §9.2) */}
              <button
                type="button"
                onClick={() => setNote(note === "sso" ? null : "sso")}
                className="mt-6 flex w-full items-center justify-center gap-2.5 rounded-xl border border-[#bcd0f7] py-3.5 text-sm font-semibold text-[#2563eb] transition-colors hover:bg-[#f3f7ff]"
              >
                <IconShield className="h-[18px] w-[18px]" />
                {t.ssoButton}
              </button>
              {note === "sso" && (
                <p className="mt-3 rounded-xl bg-[#f1f5f9] px-4 py-3 text-xs leading-relaxed text-[#3c4c60]">
                  {t.ssoNote}
                </p>
              )}

              <p className="mt-7 text-center text-sm text-[#5b6b7f]">
                {t.noAccount}{" "}
                <span className="font-semibold text-[#2563eb]">{t.contactAdmin}</span>
              </p>
            </div>
          </div>
        </div>

        {/* Футер поверх фотографии */}
        <footer className="mt-8 flex flex-col items-center gap-2 text-xs text-white/60 sm:flex-row sm:justify-between">
          <p>{t.copyright}</p>
          <p className="flex flex-wrap items-center justify-center gap-x-6 gap-y-1 text-white/70">
            <span>{t.footerPrivacy}</span>
            <span>{t.footerTerms}</span>
            <span>{t.footerSupport}</span>
          </p>
        </footer>
      </div>
    </div>
  );
}
