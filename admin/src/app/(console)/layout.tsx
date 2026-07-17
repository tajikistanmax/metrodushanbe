import SkipLink from "@/components/SkipLink";
import Sidebar from "@/components/Sidebar";
import Topbar from "@/components/Topbar";
import { requireAdminSession } from "@/lib/server-auth";

/**
 * Каркас операционной консоли: слева — навигация (Sidebar), сверху — топбар
 * с поиском и профилем, контент — на приглушённом фоне. Доступ проверяется
 * на сервере; proxy.ts выполняет дополнительный ранний redirect.
 */
export default async function ConsoleLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  await requireAdminSession();

  return (
    <div className="flex min-h-dvh flex-col lg:flex-row">
      <SkipLink />
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar />
        <main id="main" className="min-w-0 flex-1 p-4 sm:p-6 lg:p-8">
          {children}
        </main>
      </div>
    </div>
  );
}
