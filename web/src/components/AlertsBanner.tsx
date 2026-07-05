"use client";

/**
 * Стопка баннеров активных сервисных уведомлений (ТЗ §6.2.6) под DemoBanner.
 * Порядок — как отдаёт API (critical → warning → info). Severity задаёт
 * цвет фона И текстовую метку с иконкой: цвет — не единственный носитель
 * смысла (WCAG 2.2). critical — role="alert", остальные — role="status"
 * (объявление скринридером, ТЗ A11Y-07).
 *
 * Контраст текста (WCAG AA): белый на --brand-red ≈4.8:1 и на --info ≈7.4:1;
 * на --warning (#E08600) белый даёт лишь ≈2.8:1, поэтому там текст
 * --brand-navy ≈5.5:1 — тот же приём, что в DemoBanner на этом же фоне.
 *
 * Закрытие крестиком — на время жизни страницы (useState, без localStorage).
 * После закрытия фокус переносится на кнопку закрытия следующего оставшегося
 * баннера (для последнего — предыдущего); если баннеров не осталось — на
 * <main> (см. MAIN_CONTENT_ID), чтобы фокус не падал на document.body.
 */

import { useEffect, useMemo, useRef, useState } from "react";
import { lineBadgeLabel, pickName } from "@/lib/i18n";
import {
  isLineFeature,
  isStationFeature,
  type AlertSeverity,
  type I18nName,
  type NetworkGeoJson,
  type ServiceAlert,
} from "@/lib/types";
import { useI18n } from "./I18nProvider";

/**
 * id элемента <main> (задаётся в HomeClient вместе с tabIndex={-1}) —
 * fallback-цель фокуса после закрытия последнего баннера.
 */
export const MAIN_CONTENT_ID = "main-content";

type AlertsBannerProps = {
  /** Активные уведомления в порядке, который отдаёт API. */
  alerts: ServiceAlert[];
  /** Данные сети для резолва цветов линий и названий станций (может ещё грузиться). */
  data: NetworkGeoJson | null;
};

/** Нейтральный фон бейджа линии, пока сеть не загружена (константа темы). */
const NEUTRAL_BADGE_BG = "var(--brand-navy)";

type SeverityStyle = {
  /** CSS-фон баннера. */
  bg: string;
  /** Цвет текста; брендовые константы — не зависят от темы. */
  fg: string;
  /** Класс для правил :focus-visible в globals.css. */
  className: string;
};

/** Оформление баннера по severity (контраст AA — см. комментарий выше). */
const SEVERITY_STYLE: Record<AlertSeverity, SeverityStyle> = {
  critical: {
    bg: "var(--brand-red)",
    fg: "var(--surface-light)",
    className: "alert-banner-critical",
  },
  warning: {
    bg: "var(--warning)",
    fg: "var(--brand-navy)",
    className: "alert-banner-warning",
  },
  info: {
    bg: "var(--info)",
    fg: "var(--surface-light)",
    className: "alert-banner-info",
  },
};

/** Иконка severity: у каждого уровня свой знак — не только цвет. */
function SeverityIcon({ severity }: { severity: AlertSeverity }) {
  if (severity === "critical") {
    // Восьмиугольник («стоп») с восклицательным знаком
    return (
      <svg
        aria-hidden="true"
        focusable="false"
        width={16}
        height={16}
        viewBox="0 0 16 16"
        className="mt-0.5 shrink-0"
        fill="none"
        stroke="currentColor"
        strokeWidth={1.5}
        strokeLinejoin="round"
      >
        <path d="M5.3 1.7h5.4l3.6 3.6v5.4l-3.6 3.6H5.3l-3.6-3.6V5.3l3.6-3.6Z" />
        <path d="M8 4.8v3.9" strokeLinecap="round" strokeWidth={1.8} />
        <circle cx="8" cy="11.1" r="0.9" fill="currentColor" stroke="none" />
      </svg>
    );
  }
  if (severity === "warning") {
    // Треугольник с восклицательным знаком (как в DemoBanner)
    return (
      <svg
        aria-hidden="true"
        focusable="false"
        width={16}
        height={16}
        viewBox="0 0 16 16"
        className="mt-0.5 shrink-0"
        fill="currentColor"
      >
        <path d="M8 1.5c.36 0 .69.19.87.5l6.4 11.1a1 1 0 0 1-.87 1.5H1.6a1 1 0 0 1-.87-1.5L7.13 2c.18-.31.51-.5.87-.5Zm0 4a.8.8 0 0 0-.8.84l.17 3.2a.63.63 0 0 0 1.26 0l.17-3.2A.8.8 0 0 0 8 5.5Zm0 5.6a.9.9 0 1 0 0 1.8.9.9 0 0 0 0-1.8Z" />
      </svg>
    );
  }
  // info: буква «i» в круге
  return (
    <svg
      aria-hidden="true"
      focusable="false"
      width={16}
      height={16}
      viewBox="0 0 16 16"
      className="mt-0.5 shrink-0"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.5}
    >
      <circle cx="8" cy="8" r="6.3" />
      <path d="M8 7.3v3.6" strokeLinecap="round" strokeWidth={1.8} />
      <circle cx="8" cy="4.9" r="0.9" fill="currentColor" stroke="none" />
    </svg>
  );
}

export default function AlertsBanner({ alerts, data }: AlertsBannerProps) {
  const { lang, dict } = useI18n();
  // Коды уведомлений, скрытых пользователем (только на время жизни страницы)
  const [dismissed, setDismissed] = useState<ReadonlySet<string>>(
    () => new Set(),
  );

  // Кнопки закрытия по коду уведомления — цели переноса фокуса после dismiss.
  // При размонтировании кнопки callback-ref вызывается с null — запись
  // удаляется из карты, устаревших DOM-узлов в ней не остаётся.
  const closeButtons = useRef(new Map<string, HTMLButtonElement>());
  // Флаг «после обновления state перенести фокус»: code кнопки-цели,
  // { code: null } — на <main>, null — переноса не требуется
  const pendingFocus = useRef<{ code: string | null } | null>(null);

  // Перенос фокуса после закрытия баннера: кнопка исчезла из DOM, и без
  // переноса фокус падает на document.body (потеря контекста для клавиатуры
  // и скринридера)
  useEffect(() => {
    const pending = pendingFocus.current;
    if (!pending) {
      return;
    }
    pendingFocus.current = null;
    const button =
      pending.code !== null ? closeButtons.current.get(pending.code) : undefined;
    if (button) {
      button.focus();
    } else {
      // Баннеров не осталось (или цель уже размонтирована) — фокус на <main>
      document.getElementById(MAIN_CONTENT_ID)?.focus();
    }
  }, [dismissed]);

  // Справочники по кодам из network data: цвет линии и название станции
  const lineColors = useMemo(() => {
    const map = new Map<string, string>();
    data?.features.filter(isLineFeature).forEach((feature) => {
      map.set(feature.properties.code, feature.properties.color_hex);
    });
    return map;
  }, [data]);

  const stationNames = useMemo(() => {
    const map = new Map<string, I18nName>();
    data?.features.filter(isStationFeature).forEach((feature) => {
      map.set(feature.properties.code, feature.properties.name);
    });
    return map;
  }, [data]);

  const visible = alerts.filter((alert) => !dismissed.has(alert.code));
  if (visible.length === 0) {
    return null;
  }

  const dismiss = (code: string) => {
    // Цель фокуса: следующий оставшийся баннер; для последнего в списке —
    // предыдущий; если баннеров не осталось — <main> (code: null)
    const index = visible.findIndex((alert) => alert.code === code);
    const remaining = visible.filter((alert) => alert.code !== code);
    const target = remaining[Math.min(index, remaining.length - 1)];
    pendingFocus.current = { code: target?.code ?? null };
    setDismissed((prev) => new Set(prev).add(code));
  };

  return (
    <section aria-label={dict.alertsRegionLabel} className="shrink-0">
      {visible.map((alert) => {
        const style = SEVERITY_STYLE[alert.severity];
        // Уникальное имя кнопки закрытия: у баннеров одинаковый крестик,
        // без заголовка в имени они неразличимы для скринридера
        const dismissLabel = `${dict.alertDismiss}: ${pickName(alert.title, lang)}`;
        return (
          <div
            key={alert.code}
            role={alert.severity === "critical" ? "alert" : "status"}
            className={`flex items-start gap-2 px-3 py-2 sm:px-4 ${style.className}`}
            style={{ background: style.bg, color: style.fg }}
          >
            <SeverityIcon severity={alert.severity} />

            <div className="min-w-0 flex-1">
              {/* Заголовок жирно, перед ним — текстовая метка severity */}
              <p className="text-[13px] font-bold leading-snug">
                <span className="mr-1.5 inline-block rounded-md border border-current px-1.5 align-[1px] text-[10px] font-bold uppercase tracking-wider">
                  {dict.alertSeverity[alert.severity]}
                </span>
                {pickName(alert.title, lang)}
              </p>
              <p className="mt-0.5 text-[13px] leading-snug">
                {pickName(alert.body, lang)}
              </p>

              {/* Бейджи целей; пустой список = уведомление на всю сеть */}
              {alert.targets.length > 0 && (
                <p className="mt-1.5 flex flex-wrap items-center gap-1.5 text-xs font-semibold">
                  <span>{dict.alertAffected}</span>
                  {alert.targets.map((target) => {
                    const key = `${target.type}-${target.code}`;
                    if (target.type === "line") {
                      return (
                        <span
                          key={key}
                          className="line-badge"
                          style={{
                            background:
                              lineColors.get(target.code) ?? NEUTRAL_BADGE_BG,
                          }}
                        >
                          {lineBadgeLabel(target.code, lang)}
                        </span>
                      );
                    }
                    const name = stationNames.get(target.code);
                    return (
                      <span
                        key={key}
                        className="rounded-full border border-current px-2 py-0.5 text-[11px] font-semibold"
                      >
                        {name ? pickName(name, lang) : target.code}
                      </span>
                    );
                  })}
                </p>
              )}
            </div>

            <button
              type="button"
              ref={(node) => {
                if (node) {
                  closeButtons.current.set(alert.code, node);
                } else {
                  closeButtons.current.delete(alert.code);
                }
              }}
              aria-label={dismissLabel}
              title={dismissLabel}
              onClick={() => dismiss(alert.code)}
              className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md transition-colors duration-150 ease-out hover:bg-current/15"
            >
              <svg
                aria-hidden="true"
                focusable="false"
                width={14}
                height={14}
                viewBox="0 0 14 14"
                fill="none"
                stroke="currentColor"
                strokeWidth={2}
                strokeLinecap="round"
              >
                <path d="M3 3l8 8M11 3l-8 8" />
              </svg>
            </button>
          </div>
        );
      })}
    </section>
  );
}
