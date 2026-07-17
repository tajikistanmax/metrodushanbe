import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Метрои Душанбе — Dushanbe Metro",
    short_name: "Метро Душанбе",
    description:
      "Карта, станции, маршруты, расписание и новости метро Душанбе.",
    start_url: "/",
    scope: "/",
    display: "standalone",
    orientation: "any",
    background_color: "#edf1f5",
    theme_color: "#082742",
    lang: "tg",
    categories: ["travel", "navigation", "public-transport"],
    icons: [
      {
        src: "/brand/app-icon.svg",
        sizes: "any",
        type: "image/svg+xml",
        purpose: "any",
      },
      {
        src: "/brand/app-icon.svg",
        sizes: "any",
        type: "image/svg+xml",
        purpose: "maskable",
      },
    ],
  };
}
