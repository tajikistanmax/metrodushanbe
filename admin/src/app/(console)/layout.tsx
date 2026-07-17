import SkipLink from "@/components/SkipLink";
import Sidebar from "@/components/Sidebar";
import Topbar from "@/components/Topbar";
import { requireAdminSession } from "@/lib/server-auth";

/**
 * Каркас операционной консоли: слева — навигация (Sidebar), сверху — топбар
 * с поиском и профилем, контент — на приглушённом фоне. Доступ проверяется
 * на сервере; proxy.ts выполняет дополнительный ранний redirect.
 *
 * Claims сессии приходят сюда с сервера и передаются в клиентские Sidebar/Topbar
 * пропсами: так навигация скрывает недоступные разделы, а топбар показывает
 * реального оператора вместо константы.
 */
export default async function ConsoleLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const session = await requireAdminSession();

  return (
    <div className="flex min-h-dvh flex-col lg:flex-row">
      <SkipLink />
      <Sidebar role={session.role} />
      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar displayName={session.displayName} role={session.role} />
        <main id="main" className="min-w-0 flex-1 p-4 sm:p-6 lg:p-8">
          {children}
        </main>
      </div>
    </div>
  );
}
