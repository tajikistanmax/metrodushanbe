"use client";

export default function GlobalError({ reset }: { reset: () => void }) {
  return (
    <main className="mx-auto flex min-h-[60dvh] max-w-2xl flex-col items-center justify-center gap-4 px-4 text-center">
      <h1 className="text-2xl font-bold">Ошибка загрузки консоли</h1>
      <p className="text-text-secondary">Повторите запрос или проверьте доступность backend.</p>
      <button type="button" onClick={reset} className="rounded-lg bg-brand-navy px-4 py-2 font-bold text-white">
        Повторить
      </button>
    </main>
  );
}
