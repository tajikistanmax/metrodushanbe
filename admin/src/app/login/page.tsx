import type { Metadata } from "next";
import LoginClient from "@/components/login/LoginClient";

export const metadata: Metadata = {
  title: "Вход — Метро Душанбе",
  description:
    "Вход в операционную консоль национальной цифровой платформы «Метро Душанбе».",
};

/** Страница входа: вся вёрстка — в клиентском компоненте (i18n-контекст). */
export default function LoginPage() {
  return <LoginClient />;
}
