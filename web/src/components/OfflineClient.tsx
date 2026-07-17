"use client";

/**
 * Офлайн-страница: её отдаёт service worker, когда сети нет вовсе
 * (dev-conventions.md §8). Раздел не работает с данными сети — источник в
 * шапку не публикуется, пилюли источника нет.
 */

import Link from "next/link";
import { MAIN_CONTENT_ID } from "@/lib/dom-ids";
import { Button, Container } from "@/shared/ui";
import BrandMark from "@/shared/BrandMark";
import { useI18n } from "./I18nProvider";

export default function OfflineClient() {
  const { dict } = useI18n();
  return (
    <main
      id={MAIN_CONTENT_ID}
      className="flex flex-1 flex-col items-center justify-center py-12 sm:py-16"
    >
      <Container width="narrow" className="text-center">
        {/* Марка на navy-плашке: радиус из шкалы (--corner-panel), без тени —
            плашка ничего не перекрывает и висеть ей незачем */}
        <span className="mx-auto flex h-28 w-28 items-center justify-center rounded-panel bg-brand-navy">
          <BrandMark className="h-20 w-[86px]" holeColor="var(--brand-navy)" />
        </span>

        <h1 className="mt-8 text-title-l font-bold sm:text-title-xl">
          {dict.offlineTitle}
        </h1>
        <p className="mx-auto mt-4 max-w-[65ch] text-lead text-text-secondary">
          {dict.offlineBody}
        </p>

        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <Button
            variant="primary"
            size="lg"
            onClick={() => window.location.reload()}
          >
            {dict.offlineRetry}
          </Button>
          {/* Ссылка, а не кнопка: это переход на главную. Вид совпадает с
              Button variant="secondary" размера lg. */}
          <Link
            href="/"
            className="inline-flex h-11 shrink-0 items-center justify-center rounded-control border border-[var(--border-strong)] bg-[var(--surface-raised)] px-5 text-body font-bold text-[var(--text-primary)] transition-colors duration-150 ease-out hover:bg-[var(--surface-hover)]"
          >
            {dict.offlineBack}
          </Link>
        </div>
      </Container>
    </main>
  );
}
