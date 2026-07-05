"use client";

/**
 * Локализованный заголовок раздела. Серверные страницы не имеют доступа к
 * словарю (i18n живёт на клиенте), поэтому заголовок выбирается здесь по
 * ключу раздела.
 */

import { useI18n } from "./I18nProvider";
import PageHeader from "./PageHeader";

export type Section = "overview" | "lines" | "stations" | "alerts" | "news";

export default function SectionHeader({ section }: { section: Section }) {
  const { dict } = useI18n();

  const title =
    section === "overview"
      ? dict.overviewTitle
      : section === "lines"
        ? dict.linesTitle
        : section === "stations"
          ? dict.stationsTitle
          : section === "alerts"
            ? dict.alertsTitle
            : dict.newsTitle;

  const lead = section === "overview" ? dict.overviewLead : undefined;

  return <PageHeader title={title} lead={lead} />;
}
