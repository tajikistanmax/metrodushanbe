import type { Metadata } from "next";
import NotificationsClient from "@/components/NotificationsClient";

export const metadata: Metadata = {
  title: "Огоҳиномаҳо — Уведомления — Notifications · Метрои Душанбе",
  description:
    "Лента официальных сообщений метро Душанбе: инциденты, плановые работы и информация по линиям и станциям.",
};

export default function NotificationsPage() {
  return <NotificationsClient />;
}
