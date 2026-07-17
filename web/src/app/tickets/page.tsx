import type { Metadata } from "next";
import TicketsClient from "@/components/TicketsClient";

export const metadata: Metadata = {
  title: "Чиптаҳо — Билеты — Tickets · Метрои Душанбе",
  description:
    "Демонстрационная покупка билета, пополнение проездного, проверка и возврат на портале метро Душанбе. Платежи имитируются, деньги не списываются.",
};

export default function TicketsPage() {
  return <TicketsClient />;
}
