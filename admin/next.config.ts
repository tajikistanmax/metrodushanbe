import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Минимальный self-contained рантайм для Docker: Next копирует в
  // .next/standalone только нужные файлы + server.js (без установки node_modules
  // в образе). См. next docs → config → output. Каждое приложение монорепо —
  // самостоятельный npm-проект, поэтому корень трассировки = каталог admin/.
  output: "standalone",
};

export default nextConfig;
