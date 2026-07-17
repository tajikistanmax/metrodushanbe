import type { Metadata } from "next";
import OfflineClient from "@/components/OfflineClient";

export const metadata: Metadata = {
  title: "Офлайн · Метрои Душанбе",
  description: "Офлайн-режим публичного портала метро Душанбе.",
};

export default function OfflinePage() {
  return <OfflineClient />;
}
