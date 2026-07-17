import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Минимальный self-contained рантайм для Docker: Next копирует в
  // .next/standalone только нужные файлы + server.js (без установки node_modules
  // в образе). См. next docs → config → output. Каждое приложение монорепо —
  // самостоятельный npm-проект, поэтому корень трассировки = каталог web/.
  output: "standalone",
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(self)" },
        ],
      },
    ];
  },
};

export default nextConfig;
