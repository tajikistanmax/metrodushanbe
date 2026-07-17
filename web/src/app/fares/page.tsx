import type { Metadata } from "next";
import FaresClient from "@/components/FaresClient";

export const metadata: Metadata = {
  title: "Тарофаҳо — Тарифы — Fares · Метрои Душанбе",
  description: "Тарифные продукты и правила их действия на портале метро Душанбе.",
};

export default function FaresPage() {
  return <FaresClient />;
}
