"use client";

/**
 * Лента in-app уведомлений (NTF-01).
 *
 * ПОЧЕМУ ОТДЕЛЬНЫЙ РАЗДЕЛ, А НЕ ВСТРАИВАНИЕ В AlertsBanner. Это разные сущности,
 * и смешивать их в одном контуре нельзя:
 *   • сервисный алерт (/alerts, AlertsBanner) — ТЕКУЩЕЕ СОСТОЯНИЕ сети. Он живёт
 *     ровно пока действует (startsAt…endsAt), обязан быть виден на любом экране
 *     и потому показан полосой поверх контента;
 *   • рассылка (/notifications) — СОБЫТИЕ, уже случившееся и отправленное
 *     (status=sent, sentAt в прошлом). Она не «действует» и не заканчивается,
 *     её место — хронологическая лента, которую открывают намеренно.
 * Показать рассылки полосой значило бы кричать о вчерашних новостях; показать
 * алерты только в ленте — спрятать аварию. Поэтому: баннер остаётся баннером,
 * лента — отдельная страница, а связь между ними видна через alertCode
 * (рассылка «по сервисному уведомлению»).
 *
 * Фильтры повторяют семантику /alerts (dev-conventions.md §3): рассылки без
 * таргетов адресованы всей сети и попадают в выдачу всегда, lineCode и
 * stationCode объединяются, а не пересекаются. Фильтрует backend — портал лишь
 * передаёт параметры, чтобы правило жило в одном месте.
 *
 * Офлайн (§8): loadNotifications при любой ошибке отдаёт пустой список, поэтому
 * страница деградирует до объяснимого пустого состояния, а не до экрана ошибки.
 */

import { useEffect, useMemo, useState } from "react";
import { formatDushanbeDateTime } from "@/lib/date-time";
import { lineBadgeLabel, pickName } from "@/lib/i18n";
import { MAIN_CONTENT_ID } from "@/lib/dom-ids";
import { loadNetworkData } from "@/lib/network-data";
import { loadNotifications } from "@/lib/notification-data";
import {
  isLineFeature,
  isStationFeature,
  type DataSource,
  type NetworkGeoJson,
  type NotificationMessage,
  type NotificationType,
} from "@/lib/types";
import { Alert, Badge, Card, Container, Select, Stack, type BadgeTone } from "@/shared/ui";
import { useReportDataSource } from "./DataSourceProvider";
import { useI18n } from "./I18nProvider";

/**
 * Тон бейджа типа рассылки. Цвет только ДУБЛИРУЕТ подпись типа (SC 1.4.1):
 * подпись выводится всегда, а promo и info намеренно не различаются цветом —
 * различать объявление и информацию оттенком бессмысленно.
 */
const TYPE_TONE: Record<NotificationType, BadgeTone> = {
  info: "info",
  warning: "warning",
  incident: "critical",
  maintenance: "neutral",
  promo: "neutral",
};

export default function NotificationsClient() {
  const { lang, dict } = useI18n();
  const [network, setNetwork] = useState<NetworkGeoJson | null>(null);
  const [source, setSource] = useState<DataSource | null>(null);
  const [lineCode, setLineCode] = useState("");
  const [stationCode, setStationCode] = useState("");
  /**
   * Загруженная лента вместе с ключом фильтра, при котором она получена.
   * Ключ хранится рядом с данными, чтобы «загружаем» выводилось сравнением
   * (loaded.key !== filterKey), а не сбросом состояния прямо в эффекте:
   * синхронный setState в теле эффекта — каскадный рендер (react-hooks).
   */
  const [loaded, setLoaded] = useState<{
    key: string;
    data: NotificationMessage[];
  } | null>(null);
  const filterKey = `${lineCode}|${stationCode}`;

  useEffect(() => {
    let cancelled = false;
    loadNetworkData()
      .then((result) => {
        if (cancelled) return;
        setNetwork(result.data);
        setSource(result.source);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  // Фильтрует backend: правило таргетинга должно жить в одном месте
  useEffect(() => {
    let cancelled = false;
    loadNotifications({
      lineCode: lineCode || undefined,
      stationCode: stationCode || undefined,
    }).then((result) => {
      if (!cancelled) setLoaded({ key: filterKey, data: result });
    });
    return () => {
      cancelled = true;
    };
  }, [filterKey, lineCode, stationCode]);

  /** null — данные под текущий фильтр ещё не получены. */
  const items = loaded !== null && loaded.key === filterKey ? loaded.data : null;

  const lines = useMemo(
    () =>
      network?.features
        .filter(isLineFeature)
        .sort((a, b) => a.properties.sort_order - b.properties.sort_order) ?? [],
    [network],
  );
  const stations = useMemo(
    () =>
      network?.features
        .filter(isStationFeature)
        .filter(
          (station) => lineCode === "" || station.properties.lines.includes(lineCode),
        )
        .sort((a, b) =>
          pickName(a.properties.name, lang).localeCompare(
            pickName(b.properties.name, lang),
            lang,
          ),
        ) ?? [],
    [lang, lineCode, network],
  );

  const stationNames = useMemo(() => {
    const map = new Map<string, string>();
    network?.features.filter(isStationFeature).forEach((feature) => {
      map.set(feature.properties.code, pickName(feature.properties.name, lang));
    });
    return map;
  }, [lang, network]);

  useReportDataSource(source);

  return (
    <main id={MAIN_CONTENT_ID} className="flex-1 py-8 sm:py-12">
      <Container width="narrow">
          <h1 className="text-title-l font-bold sm:text-title-xl">
            {dict.notifications.heading}
          </h1>
          <p className="mt-3 max-w-[65ch] text-lead text-text-secondary">
            {dict.notifications.intro}
          </p>

          {/* Разница «алерт против рассылки» объяснена пассажиру, а не только
              в комментарии: иначе он ищет в ленте состояние сети */}
          <Alert
            tone="info"
            label={dict.notifications.vsAlertsLabel}
            live={false}
            className="mt-5"
          >
            {dict.notifications.vsAlerts}
          </Alert>

          <Card heading={dict.notifications.filtersTitle} className="mt-5">
            <Stack gap={4} className="sm:grid sm:grid-cols-2 sm:gap-4">
              <Select
                label={dict.notifications.filterLine}
                value={lineCode}
                onChange={(event) => {
                  setLineCode(event.target.value);
                  // Станция могла принадлежать прежней линии — сбрасываем,
                  // иначе фильтр молча остаётся вне выбранной линии
                  setStationCode("");
                }}
                options={[
                  { value: "", label: dict.notifications.anyLine },
                  ...lines.map((line) => ({
                    value: line.properties.code,
                    label: pickName(line.properties.name, lang),
                  })),
                ]}
              />
              <Select
                label={dict.notifications.filterStation}
                value={stationCode}
                onChange={(event) => setStationCode(event.target.value)}
                options={[
                  { value: "", label: dict.notifications.anyStation },
                  ...stations.map((station) => ({
                    value: station.properties.code,
                    label: pickName(station.properties.name, lang),
                  })),
                ]}
              />
            </Stack>
          </Card>

          {items === null ? (
            <p className="mt-6 text-small text-text-secondary">
              {dict.notifications.loading}
            </p>
          ) : items.length === 0 ? (
            // Пустая лента и недоступный backend неразличимы (см. loadNotifications):
            // объясняем оба случая одним честным текстом
            <Stack gap={3} className="mt-6">
              <p className="text-small font-semibold">{dict.notifications.empty}</p>
              <p className="text-small text-text-secondary">
                {dict.notifications.offline}
              </p>
            </Stack>
          ) : (
            <Stack as="ul" gap={4} className="mt-6">
              {items.map((item) => (
                <Card as="li" key={item.code} padding="md">
                  <Stack direction="horizontal" gap={2} align="center" wrap>
                    <Badge tone={TYPE_TONE[item.type]} dot>
                      {dict.notifications.types[item.type]}
                    </Badge>
                    {item.alertCode ? (
                      <Badge tone="neutral">{dict.notifications.fromAlert}</Badge>
                    ) : null}
                    {item.sentAt ? (
                      <span className="text-caption text-text-secondary">
                        {dict.notifications.sentAt}:{" "}
                        <time dateTime={item.sentAt}>
                          {formatDushanbeDateTime(item.sentAt, lang)}
                        </time>
                      </span>
                    ) : null}
                  </Stack>
                  <h2 className="mt-3 text-title-s font-bold">
                    {pickName(item.title, lang)}
                  </h2>
                  <p className="mt-2 whitespace-pre-wrap text-small leading-relaxed">
                    {pickName(item.body, lang)}
                  </p>
                  <p className="mt-3 flex flex-wrap items-center gap-2 text-caption font-semibold text-text-secondary">
                    {item.targets.length === 0 ? (
                      // Пустой массив targets = рассылка на всю сеть
                      <Badge tone="neutral">{dict.notifications.networkWide}</Badge>
                    ) : (
                      <>
                        <span>{dict.notifications.addressedTo}</span>
                        {item.targets.map((target) => (
                          <Badge key={`${target.type}-${target.code}`} tone="neutral">
                            {target.type === "line"
                              ? lineBadgeLabel(target.code, lang)
                              : target.type === "station"
                                ? (stationNames.get(target.code) ?? target.code)
                                : target.code}
                          </Badge>
                        ))}
                      </>
                    )}
                  </p>
                </Card>
              ))}
            </Stack>
          )}
      </Container>
    </main>
  );
}
