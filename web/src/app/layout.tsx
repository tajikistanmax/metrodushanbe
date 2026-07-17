import type { Metadata, Viewport } from "next";

// Montserrat self-host через @fontsource: файлы бандлятся локально,
// внешних запросов нет (dev-conventions.md, §5 и §8). Каждый импорт
// включает @font-face c unicode-range: latin, cyrillic и cyrillic-ext —
// последний покрывает таджикские буквы ӣ ӯ қ ғ ҳ ҷ (U+0460–052F).
import "@fontsource/montserrat/400.css";
import "@fontsource/montserrat/600.css";
import "@fontsource/montserrat/700.css";
import "@fontsource/montserrat/800.css";

import "./globals.css";
import { I18nProvider } from "@/shared/I18nProvider";
import { ThemeProvider } from "@/shared/ThemeProvider";
import { THEME_INIT_SCRIPT } from "@/shared/theme-init";
import { DataSourceProvider } from "@/components/DataSourceProvider";
import Footer from "@/components/Footer";
import Header from "@/components/Header";
import PortalSkipLink from "@/components/PortalSkipLink";
import PwaRuntime from "@/components/PwaRuntime";

export const metadata: Metadata = {
  title: "Метрои Душанбе — Dushanbe Metro",
  applicationName: "Метрои Душанбе",
  description:
    "Публичный портал метро Душанбе: демонстрационная схема сети, станции и линии.",
  manifest: "/manifest.webmanifest",
  appleWebApp: {
    capable: true,
    title: "Метро Душанбе",
    statusBarStyle: "black-translucent",
  },
};

export const viewport: Viewport = {
  themeColor: "#082742",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  // lang по умолчанию — tg; при смене языка <html lang> обновляется
  // на клиенте в I18nProvider. data-theme выставляется до гидратации
  // инлайн-скриптом — suppressHydrationWarning гасит diff атрибутов.
  //
  // КАРКАС ПОРТАЛА. Шапка, подвал и skip-link живут здесь, а не в каждом
  // page-client, как было раньше: восемь копий <Header /> расходились между
  // собой и оставляли портал вовсе без подвала. Порядок в DOM важен:
  // skip-link — ПЕРВЫЙ фокусируемый элемент документа, поэтому он выше шапки;
  // цель у него одна на весь портал — <main id="main-content"> (MAIN_CONTENT_ID).
  return (
    <html lang="tg" className="h-full antialiased" suppressHydrationWarning>
      <body className="flex min-h-full flex-col">
        <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
        <ThemeProvider>
          <I18nProvider>
            <PwaRuntime />
            <DataSourceProvider>
              <PortalSkipLink />
              <Header />
              {children}
              <Footer />
            </DataSourceProvider>
          </I18nProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
