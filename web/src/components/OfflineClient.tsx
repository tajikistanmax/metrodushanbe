"use client";

import Link from "next/link";
import BrandMark from "./BrandMark";
import Header from "./Header";
import { useI18n } from "./I18nProvider";

export default function OfflineClient() {
  const { dict } = useI18n();
  return (
    <div className="flex min-h-dvh flex-col bg-[var(--page-bg)]">
      <Header />
      <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col items-center justify-center px-5 py-12 text-center">
        <span className="flex h-28 w-28 items-center justify-center rounded-[2rem] bg-brand-navy shadow-[var(--shadow-card)]">
          <BrandMark className="h-20 w-[86px]" holeColor="var(--brand-navy)" />
        </span>
        <h1 className="mt-7 text-3xl font-extrabold tracking-tight">
          {dict.offlineTitle}
        </h1>
        <p className="mt-3 max-w-xl text-sm leading-relaxed text-text-secondary sm:text-base">
          {dict.offlineBody}
        </p>
        <div className="mt-7 flex flex-wrap justify-center gap-3">
          <button
            type="button"
            onClick={() => window.location.reload()}
            className="rounded-full bg-brand-navy px-5 py-2.5 text-sm font-bold text-surface-light hover:opacity-90"
          >
            {dict.offlineRetry}
          </button>
          <Link
            href="/"
            className="rounded-full border border-[var(--panel-border)] bg-[var(--panel-bg)] px-5 py-2.5 text-sm font-bold hover:bg-[var(--control-hover)]"
          >
            {dict.offlineBack}
          </Link>
        </div>
      </main>
    </div>
  );
}
