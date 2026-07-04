import type { Metadata } from "next";

// Montserrat self-host через @fontsource: файлы бандлятся локально,
// внешних запросов нет (dev-conventions.md, §5 и §8). Каждый импорт
// включает @font-face c unicode-range: latin, cyrillic и cyrillic-ext —
// последний покрывает таджикские буквы ӣ ӯ қ ғ ҳ ҷ (U+0460–052F).
import "@fontsource/montserrat/400.css";
import "@fontsource/montserrat/600.css";
import "@fontsource/montserrat/700.css";
import "@fontsource/montserrat/800.css";

import "./globals.css";
import { I18nProvider } from "@/components/I18nProvider";
import { ThemeProvider } from "@/components/ThemeProvider";

export const metadata: Metadata = {
  title: "Метрои Душанбе — Dushanbe Metro",
  description:
    "Публичный портал метро Душанбе: демонстрационная схема сети, станции и линии.",
};

/**
 * Инлайн-скрипт до гидратации: выставляет data-theme из localStorage /
 * prefers-color-scheme, чтобы тёмная тема не «мигала» светлой.
 * Ключ хранилища должен совпадать с THEME_STORAGE_KEY (ThemeProvider.tsx).
 */
const themeInitScript = `(function(){try{var m=localStorage.getItem("metro-dushanbe.theme");var d=m==="dark"||(m!=="light"&&window.matchMedia("(prefers-color-scheme: dark)").matches);document.documentElement.dataset.theme=d?"dark":"light";}catch(e){document.documentElement.dataset.theme="light";}})();`;

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  // lang по умолчанию — tg; при смене языка <html lang> обновляется
  // на клиенте в I18nProvider. data-theme выставляется до гидратации
  // инлайн-скриптом — suppressHydrationWarning гасит diff атрибутов.
  return (
    <html lang="tg" className="h-full antialiased" suppressHydrationWarning>
      <body className="flex min-h-full flex-col">
        <script dangerouslySetInnerHTML={{ __html: themeInitScript }} />
        <ThemeProvider>
          <I18nProvider>{children}</I18nProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
