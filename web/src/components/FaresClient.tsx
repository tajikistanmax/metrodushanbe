"use client";

/**
 * Тарифы: перечень тарифных продуктов и правил их действия.
 *
 * Цены демонстрационные, поэтому пометка об этом стоит НАД списком и не
 * закрывается (Alert tone="warning" с текстовой меткой — цвет не единственный
 * носитель смысла, SC 1.4.1). Тот же приём, что в TicketsClient.
 */

import { useEffect, useState } from "react";
import { MAIN_CONTENT_ID } from "@/lib/dom-ids";
import { loadFares } from "@/lib/fare-data";
import { pickName } from "@/lib/i18n";
import type { DataSource, FareProduct } from "@/lib/types";
import { Alert, Card, Container, Stack } from "@/shared/ui";
import { useReportDataSource } from "./DataSourceProvider";
import { useI18n } from "./I18nProvider";

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

  useReportDataSource(source);

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
    <main id={MAIN_CONTENT_ID} className="flex-1 py-8 sm:py-12">
      <Container>
        <h1 className="text-title-l font-bold sm:text-title-xl">
          {dict.fares.heading}
        </h1>
        <p className="mt-3 max-w-[65ch] text-lead text-text-secondary">
          {dict.fares.intro}
        </p>

        <Alert
          tone="warning"
          label={dict.fares.nav}
          live={false}
          className="mt-6"
        >
          {dict.fares.demoNotice}
        </Alert>

        <Stack as="ul" gap={4} className="mt-6 sm:grid sm:grid-cols-2">
          {fares.map((fare) => (
            <Card as="li" key={fare.code} padding="lg">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  {/* Код тарифа — операционный идентификатор: моно, как коды
                      билетов и обращений (.data-text) */}
                  <p className="data-text text-caption font-semibold uppercase text-text-secondary">
                    {fare.code}
                  </p>
                  <h2 className="mt-1 text-title-s font-bold">
                    {pickName(fare.name, lang)}
                  </h2>
                </div>
                <p className="text-title-m font-bold tabular-nums text-brand-red">
                  {formatPrice(fare)}
                </p>
              </div>

              <p className="mt-4 text-small leading-relaxed text-text-secondary">
                {pickName(fare.description, lang)}
              </p>

              <dl className="mt-5 grid grid-cols-2 gap-3 border-t border-[var(--border-subtle)] pt-4">
                <div>
                  <dt className="text-caption font-semibold uppercase tracking-[0.08em] text-text-secondary">
                    {dict.fares.category}
                  </dt>
                  <dd className="mt-1 text-small font-semibold">
                    {dict.fares.categories[fare.riderCategory]}
                  </dd>
                </div>
                <div>
                  <dt className="text-caption font-semibold uppercase tracking-[0.08em] text-text-secondary">
                    {dict.fares.validity}
                  </dt>
                  <dd className="mt-1 text-small font-semibold">
                    {validity(fare.validityMinutes)}
                  </dd>
                </div>
              </dl>
            </Card>
          ))}
        </Stack>
      </Container>
    </main>
  );
}
