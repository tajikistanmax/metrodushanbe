import type { Metadata } from "next";

// Montserrat self-host через @fontsource: файлы бандлятся локально,
// внешних запросов нет (dev-conventions.md, §5 и §8). Субсет cyrillic-ext
// покрывает таджикские буквы ӣ ӯ қ ғ ҳ ҷ (U+0460–052F).
import "@fontsource/montserrat/400.css";
import "@fontsource/montserrat/600.css";
import "@fontsource/montserrat/700.css";
import "@fontsource/montserrat/800.css";

import "./globals.css";
import { I18nProvider } from "@/shared/I18nProvider";
import { ThemeProvider } from "@/shared/ThemeProvider";
import { THEME_INIT_SCRIPT } from "@/shared/theme-init";
import { ToastProvider } from "@/components/admin/ToastProvider";

export const metadata: Metadata = {
  title: "Консоль управления — Метро Душанбе",
  description:
    "Операционная консоль национальной цифровой платформы «Метро Душанбе»: сеть, события, контент, аудит.",
};

/**
 * Корневой layout: только провайдеры (тема, язык, тосты). Каркас консоли
 * (сайдбар + топбар) — в группе (console)/layout.tsx; страница входа /login
 * рендерится без каркаса.
 */

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  // lang по умолчанию — tg; при смене языка <html lang> обновляется на клиенте
  // в I18nProvider. data-theme выставляется до гидратации инлайн-скриптом —
  // suppressHydrationWarning гасит diff атрибутов.
  return (
    <html lang="tg" className="h-full antialiased" suppressHydrationWarning>
      <body className="min-h-full">
        <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
        <ThemeProvider>
          <I18nProvider>
            <ToastProvider>{children}</ToastProvider>
          </I18nProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
