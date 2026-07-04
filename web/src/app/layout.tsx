import type { Metadata } from "next";
import "./globals.css";
import { I18nProvider } from "@/components/I18nProvider";

// Шрифты — системные (см. globals.css). next/font/google не используется:
// внешние CDN запрещены офлайн-принципом (dev-conventions.md, §8).
// Montserrat будет добавлен self-host позже.

export const metadata: Metadata = {
  title: "Метрои Душанбе — Dushanbe Metro",
  description:
    "Публичный портал метро Душанбе: демонстрационная схема сети, станции и линии.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  // lang по умолчанию — tg; при смене языка <html lang> обновляется
  // на клиенте в I18nProvider.
  return (
    <html lang="tg" className="h-full antialiased">
      <body className="flex min-h-full flex-col">
        <I18nProvider>{children}</I18nProvider>
      </body>
    </html>
  );
}
