import Link from "next/link";

export default function NotFound() {
  return (
    <main className="mx-auto flex min-h-[60dvh] max-w-2xl flex-col items-center justify-center gap-4 px-4 text-center">
      <h1 className="text-2xl font-bold">Раздел не найден</h1>
      <Link href="/" className="rounded-lg bg-brand-navy px-4 py-2 font-bold text-white">В консоль</Link>
    </main>
  );
}
