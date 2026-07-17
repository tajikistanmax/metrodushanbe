"use client";

/**
 * Подвал портала — одна на весь портал реализация (рендерится из
 * app/layout.tsx). До редизайна подвала не было ВООБЩЕ: у государственной
 * платформы это пробел, а не минимализм — сайт нигде не объяснял своего
 * назначения, не давал канала обращений вне навигации и не помечал себя как
 * демонстрационный за пределами закрываемого DemoBanner.
 *
 * ПОЧЕМУ NAVY. Подвал держит те же смыслы, что шапка (принадлежность,
 * навигация, язык), и составляет с ней пару: два тёмных края и светлое поле
 * контента между ними. Плюс практическая причина: LangSwitcher рассчитан на
 * тёмную подложку (см. его шапку), и на светлом подвале его пришлось бы либо
 * переписывать, либо дублировать.
 *
 * Контрасты на navy (#082742) — посчитаны:
 *   белый (заголовки, ссылки)        ≈ 15.2:1  ✓ AA
 *   text-surface-light/80 (абзацы)   ≈ 10.2:1  ✓ AA
 *   индикатор фокуса — белый (globals.css: footer :focus-visible)
 * Слабее /80 здесь не использовать: /45–/65 в сайдбаре консоли — отдельная
 * история, повторять её в публичном портале не нужно.
 *
 * ПОМЕТКА ДЕМО. Не закрывается — в отличие от DemoBanner. Тот сообщает «схема
 * не утверждена» и его разумно скрыть, наглядевшись; здесь же — постоянная
 * сноска о статусе всего контура, включая имитацию платежей. Тон продублирован
 * СЛОВАМИ (footer.demoLabel), а не только цветом полосы (SC 1.4.1).
 */

import Link from "next/link";
import { usePathname } from "next/navigation";
import { isNavActive, PORTAL_NAV } from "@/lib/nav";
import LangSwitcher from "@/shared/LangSwitcher";
import { useI18n } from "@/shared/I18nProvider";

/**
 * Маршруты, где подвала нет. Главная — не документ, а холст: карта занимает
 * ровно вьюпорт под шапкой и не прокручивается (overflow-hidden в HomeClient).
 * Подвал под ней либо отрезал бы у карты высоту, либо оказался бы в недостижимой
 * зоне — на телефоне жест прокрутки достаётся карте, а не странице.
 * Назначение портала, языки и обращения при этом остаются доступны с любой
 * другой страницы, а демо-контур на главной объявляет DemoBanner.
 */
const WITHOUT_FOOTER: readonly string[] = ["/"];

export default function Footer() {
  const { dict } = useI18n();
  const pathname = usePathname();

  if (WITHOUT_FOOTER.includes(pathname)) {
    return null;
  }

  return (
    <footer
      aria-label={dict.footer.regionLabel}
      className="mt-auto shrink-0 bg-brand-navy text-surface-light"
    >
      {/* Лента флага РТ замыкает страницу так же, как открывает её в шапке */}
      <div className="ribbon-flag" aria-hidden="true" />

      <div className="mx-auto w-full max-w-5xl px-4 py-8 sm:py-12">
        <div className="grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
          {/* Назначение портала */}
          <div className="lg:col-span-2">
            <h2 className="text-caption font-bold uppercase tracking-[0.08em] text-surface-light/80">
              {dict.footer.aboutHeading}
            </h2>
            <p className="mt-3 max-w-[65ch] text-small leading-relaxed text-surface-light/80">
              {dict.footer.about}
            </p>
          </div>

          {/* Разделы — из того же списка, что и навигация шапки */}
          <nav aria-label={dict.footer.sectionsHeading}>
            <h2 className="text-caption font-bold uppercase tracking-[0.08em] text-surface-light/80">
              {dict.footer.sectionsHeading}
            </h2>
            <ul className="mt-3 flex flex-col gap-2">
              {PORTAL_NAV.map((item) => (
                <li key={item.key}>
                  <Link
                    href={item.href}
                    aria-current={isNavActive(pathname, item.href) ? "page" : undefined}
                    className="text-small font-semibold text-surface-light hover:underline"
                  >
                    {item.label(dict)}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>

          {/* Обратная связь и язык */}
          <div className="flex flex-col gap-8">
            <div>
              <h2 className="text-caption font-bold uppercase tracking-[0.08em] text-surface-light/80">
                {dict.footer.contactHeading}
              </h2>
              <p className="mt-3 text-small leading-relaxed text-surface-light/80">
                {dict.footer.contactBody}
              </p>
              <p className="mt-3">
                <Link
                  href="/requests"
                  className="text-small font-bold text-surface-light hover:underline"
                >
                  {dict.requests.nav}
                </Link>
              </p>
            </div>

            <div>
              <h2 className="text-caption font-bold uppercase tracking-[0.08em] text-surface-light/80">
                {dict.footer.languageHeading}
              </h2>
              <div className="mt-3">
                <LangSwitcher />
              </div>
            </div>
          </div>
        </div>

        {/* Пометка демо-контура: постоянная, не закрывается */}
        <div className="mt-8 border-l-4 border-l-warning bg-surface-light/10 px-4 py-3">
          <p className="text-caption font-bold uppercase tracking-[0.08em]">
            {dict.footer.demoLabel}
          </p>
          <p className="mt-1 max-w-[65ch] text-small leading-relaxed text-surface-light/80">
            {dict.footer.demoNotice}
          </p>
        </div>

        <p className="mt-8 border-t border-surface-light/20 pt-6 text-small text-surface-light/80">
          {dict.footer.legal}
        </p>
      </div>
    </footer>
  );
}
