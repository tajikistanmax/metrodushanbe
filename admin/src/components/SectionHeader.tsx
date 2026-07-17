"use client";

/**
 * Локализованный заголовок раздела. Серверные страницы не имеют доступа к
 * словарю (i18n живёт на клиенте), поэтому заголовок выбирается здесь по
 * ключу раздела.
 */

import { useI18n } from "./I18nProvider";
import PageHeader from "./PageHeader";

export type Section =
  | "overview"
  | "lines"
  | "stations"
  | "alerts"
  | "news"
  | "requests"
  | "incidents"
  | "fares"
  | "imports"
  | "calendar"
  | "features"
  | "users"
  | "audit";

export default function SectionHeader({ section }: { section: Section }) {
  const { dict } = useI18n();

  const titles: Record<Section, string> = {
    overview: dict.overviewTitle,
    lines: dict.linesTitle,
    stations: dict.stationsTitle,
    alerts: dict.alertsTitle,
    news: dict.newsTitle,
    requests: dict.operations.requestsTitle,
    incidents: dict.incidents.title,
    fares: dict.operations.faresTitle,
    imports: dict.operations.importsTitle,
    calendar: dict.operations.calendarTitle,
    features: dict.operations.featuresTitle,
    users: dict.users.title,
    audit: dict.auditTitle,
  };
  const leads: Partial<Record<Section, string>> = {
    overview: dict.overviewLead,
    imports: dict.operations.importsLead,
    calendar: dict.operations.calendarLead,
    features: dict.operations.featuresLead,
    requests: dict.operations.requestsLead,
    incidents: dict.incidents.lead,
    fares: dict.operations.faresLead,
    users: dict.users.lead,
  };

  return <PageHeader title={titles[section]} lead={leads[section]} />;
}
