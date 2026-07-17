import { Suspense } from "react";
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
 *
 * Suspense обязателен: планировщик читает `?from=&to=` через useSearchParams,
 * а он на пререндере приостанавливает дерево — без границы Next вывел бы всю
 * страницу из статики.
 */
export default function RoutePage() {
  return (
    <Suspense>
      <RoutePlannerClient />
    </Suspense>
  );
}
