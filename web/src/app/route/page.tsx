import type { Metadata } from "next";
import RoutePlannerClient from "@/components/RoutePlannerClient";

export const metadata: Metadata = {
  title: "Масир — Маршрут — Route · Метрои Душанбе",
  description:
    "Поиск маршрута метро Душанбе «откуда/куда»: участки по линиям, пересадки и оценочное время в пути на трёх языках.",
};

/**
 * Роут /route — маршрутный поиск «откуда/куда». Вся интерактивность, загрузка
 * списка станций (с офлайн-деградацией) и построение маршрута — в клиентском
 * RoutePlannerClient, как на главной и в разделе новостей.
 */
export default function RoutePage() {
  return <RoutePlannerClient />;
}
