"use client";

import { useEffect, useState } from "react";
import { loadFares } from "@/lib/fare-data";
import { pickName } from "@/lib/i18n";
import type { DataSource, FareProduct } from "@/lib/types";
import Header from "./Header";
import { useI18n } from "./I18nProvider";

const MAIN_ID = "fares-content";

export default function FaresClient() {
  const { lang, dict } = useI18n();
  const [fares, setFares] = useState<FareProduct[]>([]);
  const [source, setSource] = useState<DataSource | null>(null);

  useEffect(() => {
    let cancelled = false;
    loadFares().then((result) => {
      if (!cancelled) {
        setFares(result.data);
        setSource(result.source);
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const locale = lang === "tg" ? "tg-TJ" : lang === "ru" ? "ru-RU" : "en-GB";
  const formatPrice = (fare: FareProduct) =>
    new Intl.NumberFormat(locale, {
      style: "currency",
      currency: fare.currency,
      minimumFractionDigits: 2,
    }).format(fare.amount);
  const validity = (minutes: number | null) => {
    if (minutes === null) return dict.fares.unlimited;
    if (minutes >= 1440 && minutes % 1440 === 0) {
      return `${minutes / 1440} ${dict.fares.days}`;
    }
    return `${minutes} ${dict.fares.minutes}`;
  };

  return (
    <div className="flex min-h-dvh w-full flex-col">
      <a href={`#${MAIN_ID}`} className="skip-link">{dict.route.skipToContent}</a>
      <Header source={source} />
      <main id={MAIN_ID} className="mx-auto w-full max-w-4xl flex-1 px-4 py-8 sm:px-6">
        <h1 className="text-2xl font-extrabold tracking-tight sm:text-3xl">{dict.fares.heading}</h1>
        <p className="mt-2 max-w-2xl text-sm leading-relaxed text-text-secondary">{dict.fares.intro}</p>
        <div role="note" className="mt-5 rounded-xl border border-warning/40 bg-warning/10 px-4 py-3 text-sm font-semibold">
          {dict.fares.demoNotice}
        </div>

        <div className="mt-6 grid gap-4 sm:grid-cols-2">
          {fares.map((fare) => (
            <article key={fare.code} className="rounded-2xl border border-[var(--panel-border)] bg-[var(--panel-bg)] p-5 shadow-[var(--shadow-card)]">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-bold uppercase tracking-wide text-text-secondary">{fare.code}</p>
                  <h2 className="mt-1 text-lg font-extrabold">{pickName(fare.name, lang)}</h2>
                </div>
                <p className="text-xl font-extrabold text-brand-red">{formatPrice(fare)}</p>
              </div>
              <p className="mt-4 text-sm leading-relaxed text-text-secondary">{pickName(fare.description, lang)}</p>
              <dl className="mt-5 grid grid-cols-2 gap-3 border-t border-[var(--panel-border)] pt-4 text-sm">
                <div>
                  <dt className="text-xs font-bold uppercase tracking-wide text-text-secondary">{dict.fares.category}</dt>
                  <dd className="mt-1 font-semibold">{dict.fares.categories[fare.riderCategory]}</dd>
                </div>
                <div>
                  <dt className="text-xs font-bold uppercase tracking-wide text-text-secondary">{dict.fares.validity}</dt>
                  <dd className="mt-1 font-semibold">{validity(fare.validityMinutes)}</dd>
                </div>
              </dl>
            </article>
          ))}
        </div>
      </main>
    </div>
  );
}
