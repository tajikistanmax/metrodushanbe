import { fetchApiJson } from "./api";
import type { DataSource, FareProduct } from "./types";

const DEMO_FARES: FareProduct[] = [
  {
    code: "DEMO-SINGLE",
    name: {
      tg: "Сафари яккарата (намоишӣ)",
      ru: "Разовая поездка (демо)",
      en: "Single ride (demo)",
    },
    description: {
      tg: "Нархи намунавӣ то тасдиқи тарофаи расмӣ.",
      ru: "Демонстрационная цена до утверждения официального тарифа.",
      en: "Illustrative price pending approval of the official fare.",
    },
    amount: 3,
    currency: "TJS",
    riderCategory: "all",
    validityMinutes: 90,
    active: true,
    updatedAt: null,
  },
  {
    code: "DEMO-MONTHLY",
    name: {
      tg: "Роҳхати моҳона (намоишӣ)",
      ru: "Месячный проездной (демо)",
      en: "Monthly pass (demo)",
    },
    description: {
      tg: "Маҳсулоти намунавӣ; шартҳо баъд аз тасдиқ нав мешаванд.",
      ru: "Демонстрационный продукт; условия будут обновлены после утверждения.",
      en: "Illustrative product; terms will be updated after approval.",
    },
    amount: 50,
    currency: "TJS",
    riderCategory: "all",
    validityMinutes: 43_200,
    active: true,
    updatedAt: null,
  },
];

function assertFares(payload: unknown): FareProduct[] {
  if (!Array.isArray(payload)) throw new Error("Invalid fares response");
  return payload as FareProduct[];
}

export async function loadFares(): Promise<{ data: FareProduct[]; source: DataSource }> {
  try {
    return { data: assertFares(await fetchApiJson("/fares")), source: "api" };
  } catch {
    return { data: DEMO_FARES, source: "demo" };
  }
}
