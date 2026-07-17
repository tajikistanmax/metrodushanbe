"use client";

/**
 * Экран входа по утверждённому макету (photo/ChatGPT Image … 13_07_19):
 * фоновая фотография ночного Душанбе (admin/public/login-bg.jpg — сжатая
 * копия photo/a889422c…png), слева — белый логотип, приветствие и бейдж
 * «Безопасно • Надёжно • Удобно», справа — белая карточка формы с синими
 * акцентами. Пароль проверяется Server Action `login` (lib/auth-actions.ts);
 * сюда возвращается только код ошибки. Языки — общий I18nProvider.
 *
 * ГРАНИЦА С ИНСТИТУЦИОНАЛЬНОЙ ТЕМОЙ — осознанная, макет утверждён владельцем:
 *  1) карточка всегда светлая и не реагирует на тёмную тему — она лежит поверх
 *     ночной фотографии, перекрасить её значит разрушить макет;
 *  2) CTA синий (--login-accent #2563eb), а не --brand-navy;
 *  3) скругления крупнее шкалы (--corner-panel 8px): карточка 28px, поля 16px.
 * Всё это оставлено как в макете. Приведено к общему слою другое: сырые hex
 * вынесены в токены --login-* (packages/design/tokens.mjs), размеры — на шкалу
 * --type-*, вес 500 (не загружен) заменён на загруженные 400/600/700.
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
 * Цвета государственных флагов — НЕ дизайн-токены и намеренно не вынесены в
 * packages/design: это факт о флаге другого государства, а не решение о теме
 * платформы. Тема их перекрашивать не должна и не может. Собраны в одну
 * таблицу (регистр — нижний, как требует dev-conventions §5), чтобы не быть
 * россыпью инлайновых литералов по разметке.
 *
 * Красный и зелёный флага Таджикистана совпадают с --brand-red/--brand-green
 * не случайно: бренд платформы выведен из флага. Но здесь это именно флаг,
 * поэтому источник значения — спецификация флага, а не токен бренда.
 */
const FLAG = {
  white: "#ffffff",
  tjRed: "#e21b2d",
  tjGreen: "#138a3d",
  tjGold: "#f8c300",
  ruBlue: "#0039a6",
  ruRed: "#d52b1e",
  gbNavy: "#012169",
  gbRed: "#c8102e",
} as const;

/**
 * Мини-флаг языка для пилюли-переключателя (как в макете: «🇷🇺 Русский»).
 * SVG вместо эмодзи: флаговые эмодзи в Windows отображаются буквами.
 */
function FlagIcon({ code }: { code: Lang }) {
  const common = "h-3.5 w-5 shrink-0 rounded-chip";
  if (code === "tg") {
    return (
      <svg viewBox="0 0 20 14" className={common} aria-hidden="true">
        <rect width="20" height="14" fill={FLAG.white} />
        <rect width="20" height="4" fill={FLAG.tjRed} />
        <rect y="10" width="20" height="4" fill={FLAG.tjGreen} />
        <circle cx="10" cy="7" r="1.1" fill={FLAG.tjGold} />
      </svg>
    );
  }
  if (code === "ru") {
    return (
      <svg viewBox="0 0 20 14" className={common} aria-hidden="true">
        <rect width="20" height="14" fill={FLAG.white} />
        <rect y="4.7" width="20" height="4.6" fill={FLAG.ruBlue} />
        <rect y="9.3" width="20" height="4.7" fill={FLAG.ruRed} />
      </svg>
    );
  }
  return (
    <svg viewBox="0 0 20 14" className={common} aria-hidden="true">
      <rect width="20" height="14" fill={FLAG.gbNavy} />
      <path d="M0 0 20 14M20 0 0 14" stroke={FLAG.white} strokeWidth="2.8" />
      <path d="M0 0 20 14M20 0 0 14" stroke={FLAG.gbRed} strokeWidth="1.2" />
      <path d="M10 0v14M0 7h20" stroke={FLAG.white} strokeWidth="4.6" />
      <path d="M10 0v14M0 7h20" stroke={FLAG.gbRed} strokeWidth="2.6" />
    </svg>
  );
}

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
    <div className="relative min-h-dvh overflow-hidden bg-surface-dark">
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
      {/* Затемнение фотографии. Это не декор, а носитель контраста: без него
          белый текст лёг бы на произвольные пиксели снимка (SC 1.4.3). */}
      <div
        className="absolute inset-0 bg-gradient-to-r from-[var(--login-scrim)]/85 via-[var(--login-scrim)]/30 to-[var(--login-scrim)]/55"
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
          className="flex items-center gap-2 rounded-control border border-surface-light/40 bg-surface-dark/70 px-4 py-2.5 text-small font-semibold text-surface-light transition-colors hover:bg-surface-dark/85 focus-visible:outline-[var(--focus-ring-on-dark)]"
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
            className="absolute right-0 top-full mt-2 w-44 overflow-hidden rounded-panel border border-surface-light/25 bg-[var(--login-overlay-bg)] py-1.5 shadow-overlay"
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
                    // /70 на #0d1c2c ≈ 7.4:1; выбранный — 700 + полная непрозрачность
                    lang === code
                      ? "flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-small font-bold text-surface-light"
                      : "flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-small font-semibold text-surface-light/70 transition-colors hover:bg-surface-light/10 hover:text-surface-light"
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
          <div className="flex flex-col text-surface-light">
            {/* Белый логотип (монохром поверх фотографии) */}
            <div className="flex items-center gap-4">
              <BrandMark
                monochrome
                holeColor="rgba(0,0,0,0)"
                className="h-16 w-[70px] shrink-0 text-white"
              />
              <p className="text-title-m font-extrabold uppercase leading-[1.08] tracking-wide">
                Dushanbe
                <br />
                Metro
                <span className="mt-2 block h-px w-24 bg-surface-light/40" aria-hidden="true" />
              </p>
            </div>

            <div className="mt-10 lg:mt-16">
              {/* Было text-3xl → sm:text-[42px]; шкала: title-l 32 → title-xl 40 */}
              <h1 className="text-title-l font-extrabold leading-tight sm:text-title-xl">
                {t.welcome1}
                <br />
                {t.welcome2Prefix}
                <span className="text-[var(--login-hero-accent)]">{t.welcome2Accent}</span>
              </h1>
              <p className="mt-5 max-w-md text-body leading-relaxed text-surface-light/80">
                {t.heroLead}
              </p>
            </div>

            {/* Бейдж прижат к низу — как в макете, над футером */}
            <div className="mt-auto hidden pt-10 lg:block">
              <span className="inline-flex items-center gap-2.5 rounded-control bg-surface-dark/80 px-4 py-2.5 text-small font-semibold">
                <IconShield className="h-[18px] w-[18px] text-[var(--login-hero-accent)]" aria-hidden="true" />
                {t.badgeSecure} • {t.badgeReliable} • {t.badgeConvenient}
              </span>
            </div>
          </div>

          {/* Правая колонка: карточка формы (всегда светлая, как в макете) */}
          <div className="flex items-center lg:py-10">
            {/*
              Радиус 28px и тень карточки — из макета, вне шкалы --corner-*.
              Оставлены намеренно: карточка физически лежит поверх фотографии,
              это единственный экран платформы с таким слоем.
            */}
            <div className="w-full rounded-[28px] bg-[var(--login-card-bg)] p-7 shadow-overlay sm:p-10">
              <h2 className="text-center text-title-l font-extrabold text-[var(--login-ink)]">
                {t.title}
              </h2>
              <p className="mt-1.5 text-center text-small text-[var(--login-muted)]">{t.subtitle}</p>

              <form action={formAction} className="mt-8 flex flex-col gap-4" noValidate>
                {/* Логин */}
                <label className="flex items-center gap-3.5 rounded-panel border border-[var(--login-field-border)] p-3 pr-4 transition-colors focus-within:border-[var(--login-accent)]">
                  <span aria-hidden="true" className="flex h-11 w-11 shrink-0 items-center justify-center rounded-control bg-[var(--login-accent-tint)] text-[var(--login-accent)]">
                    <IconUser className="h-5 w-5" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block text-small font-bold text-[var(--login-ink)]">
                      {t.usernameLabel}
                    </span>
                    {/* font-medium(500) не загружен — 400 */}
                    <input
                      type="text"
                      name="username"
                      autoComplete="username"
                      required
                      placeholder={t.usernamePlaceholder}
                      className="w-full bg-transparent text-small text-[var(--login-ink)] outline-none placeholder:text-[var(--login-placeholder)]"
                    />
                  </span>
                </label>

                {/* Пароль */}
                <label className="flex items-center gap-3.5 rounded-panel border border-[var(--login-field-border)] p-3 pr-2.5 transition-colors focus-within:border-[var(--login-accent)]">
                  <span aria-hidden="true" className="flex h-11 w-11 shrink-0 items-center justify-center rounded-control bg-[var(--login-accent-tint)] text-[var(--login-accent)]">
                    <IconLock className="h-5 w-5" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block text-small font-bold text-[var(--login-ink)]">
                      {t.passwordLabel}
                    </span>
                    <input
                      type={showPassword ? "text" : "password"}
                      name="password"
                      autoComplete="current-password"
                      required
                      placeholder={t.passwordPlaceholder}
                      className="w-full bg-transparent text-small text-[var(--login-ink)] outline-none placeholder:text-[var(--login-placeholder)]"
                    />
                  </span>
                  <button
                    type="button"
                    onClick={() => setShowPassword((v) => !v)}
                    aria-label={showPassword ? t.hidePassword : t.showPassword}
                    aria-pressed={showPassword}
                    className="shrink-0 rounded-control p-2 text-[var(--login-muted)] transition-colors hover:bg-[var(--login-field-hover)] hover:text-[var(--login-ink)]"
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
                  <label className="flex cursor-pointer items-center gap-2.5 text-small">
                    <input
                      type="checkbox"
                      name="remember"
                      defaultChecked
                      className="h-4 w-4 rounded-chip accent-[var(--login-accent)]"
                    />
                    <span className="font-semibold text-[var(--login-ink-soft)]">{t.remember}</span>
                  </label>
                  <button
                    type="button"
                    onClick={() => setNote(note === "forgot" ? null : "forgot")}
                    className="rounded-chip text-small font-semibold text-[var(--login-accent)] hover:underline"
                  >
                    {t.forgot}
                  </button>
                </div>
                {note === "forgot" && (
                  <p className="rounded-control bg-[var(--login-field-hover)] px-4 py-3 text-small leading-relaxed text-[var(--login-ink-soft)]">
                    {t.forgotNote}
                  </p>
                )}

                {/* Ошибка: role="alert" объявляется немедленно; смысл несёт
                    текст, красный лишь дублирует его (SC 1.4.1) */}
                {errorText && (
                  <p
                    role="alert"
                    className="rounded-control border-l-4 border-[var(--login-error-border)] bg-[var(--login-error-bg)] px-4 py-3 text-small font-semibold text-[var(--login-error-text)]"
                  >
                    {errorText}
                  </p>
                )}

                {/* CTA из макета: синий, а не --brand-navy. Инлайновый style
                    заменён токеном — цвет теперь редактируется в одном месте. */}
                <button
                  type="submit"
                  disabled={pending}
                  className="mt-1 flex items-center justify-center gap-2.5 rounded-control bg-[var(--login-accent)] py-4 text-body font-bold text-surface-light transition-colors hover:bg-[var(--login-accent-strong)] disabled:opacity-60"
                >
                  <IconArrowRight className="h-5 w-5" aria-hidden="true" />
                  {pending ? t.submitting : t.submit}
                </button>
              </form>

              {/* Разделитель */}
              <div className="mt-6 flex items-center gap-3 text-caption font-semibold text-[var(--login-muted)]">
                <span className="h-px flex-1 bg-[var(--login-field-border)]" aria-hidden="true" />
                {t.or}
                <span className="h-px flex-1 bg-[var(--login-field-border)]" aria-hidden="true" />
              </div>

              {/* SSO (Keycloak — следующая фаза по ТЗ §9.2) */}
              <button
                type="button"
                onClick={() => setNote(note === "sso" ? null : "sso")}
                className="mt-6 flex w-full items-center justify-center gap-2.5 rounded-control border border-[var(--login-accent-border)] py-3.5 text-small font-semibold text-[var(--login-accent)] transition-colors hover:bg-[var(--login-accent-soft)]"
              >
                <IconShield className="h-[18px] w-[18px]" aria-hidden="true" />
                {t.ssoButton}
              </button>
              {note === "sso" && (
                <p className="mt-3 rounded-control bg-[var(--login-field-hover)] px-4 py-3 text-small leading-relaxed text-[var(--login-ink-soft)]">
                  {t.ssoNote}
                </p>
              )}

              <p className="mt-7 text-center text-small text-[var(--login-muted)]">
                {t.noAccount}{" "}
                <span className="font-semibold text-[var(--login-accent)]">{t.contactAdmin}</span>
              </p>
            </div>
          </div>
        </div>

        {/* Футер поверх фотографии */}
        {/* /60 на затемнённом снимке даёт <4.5:1 — поднято до /75 */}
        <footer className="mt-8 flex flex-col items-center gap-2 text-caption text-surface-light/75 sm:flex-row sm:justify-between">
          <p>{t.copyright}</p>
          <p className="flex flex-wrap items-center justify-center gap-x-6 gap-y-1 text-surface-light/75">
            <span>{t.footerPrivacy}</span>
            <span>{t.footerTerms}</span>
            <span>{t.footerSupport}</span>
          </p>
        </footer>
      </div>
    </div>
  );
}
