import type { Metadata } from "next";
import RequestsClient from "@/components/RequestsClient";

export const metadata: Metadata = {
  title: "Муроҷиат — Обращения — Requests · Метрои Душанбе",
  description:
    "Подача и отслеживание обращений граждан на портале метро Душанбе.",
};

export default function RequestsPage() {
  return <RequestsClient />;
}
