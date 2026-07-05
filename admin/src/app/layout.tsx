import type { Metadata } from "next";

// Montserrat self-host через @fontsource: файлы бандлятся локально,
// внешних запросов нет (dev-conventions.md, §5 и §8). Субсет cyrillic-ext
// покрывает таджикские буквы ӣ ӯ қ ғ ҳ ҷ (U+0460–052F).
import "@fontsource/montserrat/400.css";
import "@fontsource/montserrat/600.css";
import "@fontsource/montserrat/700.css";
import "@fontsource/montserrat/800.css";

import "./globals.css";
import { I18nProvider } from "@/components/I18nProvider";
import { ThemeProvider } from "@/components/ThemeProvider";
import Sidebar from "@/components/Sidebar";
import SkipLink from "@/components/SkipLink";

export const metadata: Metadata = {
  title: "Консоль управления — Метро Душанбе",
  description:
    "Операционная консоль метро Душанбе: линии, станции, сервисные уведомления и новости (только чтение).",
};

/**
 * Инлайн-скрипт до гидратации: выставляет data-theme из localStorage /
 * prefers-color-scheme, чтобы тёмная тема не «мигала» светлой.
 * Ключ хранилища совпадает с THEME_STORAGE_KEY (ThemeProvider.tsx) и с web.
 */
const themeInitScript = `(function(){try{var m=localStorage.getItem("metro-dushanbe.theme");var d=m==="dark"||(m!=="light"&&window.matchMedia("(prefers-color-scheme: dark)").matches);document.documentElement.dataset.theme=d?"dark":"light";}catch(e){document.documentElement.dataset.theme="light";}})();`;

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
        <script dangerouslySetInnerHTML={{ __html: themeInitScript }} />
        <ThemeProvider>
          <I18nProvider>
            <SkipLink />
            <div className="flex flex-col lg:flex-row">
              <Sidebar />
              <main id="main" className="min-w-0 flex-1 p-4 sm:p-6 lg:p-8">
                {children}
              </main>
            </div>
          </I18nProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
