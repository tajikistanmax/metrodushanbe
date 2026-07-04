/**
 * Лёгкая i18n без сторонних библиотек (dev-conventions.md, §6).
 * Языки: tg (по умолчанию), ru, en. Все строки UI — здесь, без хардкода.
 * React-контекст и переключатель — в src/components/I18nProvider.tsx и Header.tsx.
 */

import type {
  AccessibilityFeature,
  I18nName,
  LineStatus,
  StationStatus,
} from "./types";

export type Lang = "tg" | "ru" | "en";

export const LANGS: readonly Lang[] = ["tg", "ru", "en"] as const;

export const DEFAULT_LANG: Lang = "tg";

/** Ключ в localStorage для сохранения выбранного языка. */
export const LANG_STORAGE_KEY = "metro-dushanbe.lang";

/** Подписи самих языков (для кнопок переключателя). */
export const LANG_LABELS: Record<Lang, string> = {
  tg: "Тоҷикӣ",
  ru: "Русский",
  en: "English",
};

/** Словарь строк UI одного языка. */
export type Dict = {
  appTitle: string;
  demoBanner: string;
  skipToList: string;
  languageSwitcher: string;
  mapRegionLabel: string;
  mapLoading: string;
  stationsHeading: string;
  legendHeading: string;
  legendStation: string;
  legendTransfer: string;
  dataSourceLabel: string;
  dataSourceApi: string;
  dataSourceDemo: string;
  loading: string;
  loadError: string;
  popupLines: string;
  popupAccessibility: string;
  popupStatus: string;
  transferBadge: string;
  accessibility: Record<AccessibilityFeature, string>;
  status: Record<LineStatus | StationStatus, string>;
};

const tg: Dict = {
  appTitle: "Метрои Душанбе",
  demoBanner: "Нақшаи намоишӣ — маълумот тасдиқ нашудааст",
  skipToList: "Гузаштан ба рӯйхати истгоҳҳо",
  languageSwitcher: "Забон",
  mapRegionLabel: "Харитаи шабакаи метро",
  mapLoading: "Харита бор мешавад…",
  stationsHeading: "Истгоҳҳо",
  legendHeading: "Аломатҳо",
  legendStation: "Истгоҳ",
  legendTransfer: "Истгоҳи гузариш",
  dataSourceLabel: "Манбаи маълумот",
  dataSourceApi: "API",
  dataSourceDemo: "намоишӣ (офлайн)",
  loading: "Бор шуда истодааст…",
  loadError: "Маълумот бор нашуд",
  popupLines: "Хатҳо",
  popupAccessibility: "Дастрасӣ",
  popupStatus: "Ҳолат",
  transferBadge: "Гузариш",
  accessibility: {
    elevator: "Лифт",
    escalator: "Эскалатор",
    ramp: "Пандус",
    tactile: "Роҳнамои ламсӣ",
    audio_assist: "Ёрии садоӣ",
  },
  status: {
    planned: "Банақшагирифташуда",
    under_construction: "Дар сохтмон",
    testing: "Дар санҷиш",
    active: "Фаъол",
    suspended: "Боздошташуда",
    temporarily_closed: "Муваққатан баста",
    decommissioned: "Аз кор баровардашуда",
  },
};

const ru: Dict = {
  appTitle: "Метро Душанбе",
  demoBanner: "Демонстрационная схема — данные не утверждены",
  skipToList: "Перейти к списку станций",
  languageSwitcher: "Язык",
  mapRegionLabel: "Карта сети метро",
  mapLoading: "Карта загружается…",
  stationsHeading: "Станции",
  legendHeading: "Легенда",
  legendStation: "Станция",
  legendTransfer: "Пересадочная станция",
  dataSourceLabel: "Источник данных",
  dataSourceApi: "API",
  dataSourceDemo: "демо (офлайн)",
  loading: "Загрузка…",
  loadError: "Не удалось загрузить данные",
  popupLines: "Линии",
  popupAccessibility: "Доступность",
  popupStatus: "Статус",
  transferBadge: "Пересадка",
  accessibility: {
    elevator: "Лифт",
    escalator: "Эскалатор",
    ramp: "Пандус",
    tactile: "Тактильная навигация",
    audio_assist: "Аудиосопровождение",
  },
  status: {
    planned: "Запланирована",
    under_construction: "Строится",
    testing: "Тестирование",
    active: "Действует",
    suspended: "Приостановлена",
    temporarily_closed: "Временно закрыта",
    decommissioned: "Выведена из эксплуатации",
  },
};

const en: Dict = {
  appTitle: "Dushanbe Metro",
  demoBanner: "Demonstration diagram — data is not approved",
  skipToList: "Skip to station list",
  languageSwitcher: "Language",
  mapRegionLabel: "Metro network map",
  mapLoading: "Loading map…",
  stationsHeading: "Stations",
  legendHeading: "Legend",
  legendStation: "Station",
  legendTransfer: "Transfer station",
  dataSourceLabel: "Data source",
  dataSourceApi: "API",
  dataSourceDemo: "demo (offline)",
  loading: "Loading…",
  loadError: "Failed to load data",
  popupLines: "Lines",
  popupAccessibility: "Accessibility",
  popupStatus: "Status",
  transferBadge: "Transfer",
  accessibility: {
    elevator: "Elevator",
    escalator: "Escalator",
    ramp: "Ramp",
    tactile: "Tactile guidance",
    audio_assist: "Audio assistance",
  },
  status: {
    planned: "Planned",
    under_construction: "Under construction",
    testing: "Testing",
    active: "Active",
    suspended: "Suspended",
    temporarily_closed: "Temporarily closed",
    decommissioned: "Decommissioned",
  },
};

export const DICTIONARIES: Record<Lang, Dict> = { tg, ru, en };

/** Проверка, что строка — поддерживаемый код языка. */
export function isLang(value: unknown): value is Lang {
  return value === "tg" || value === "ru" || value === "en";
}

/** Словарь для языка. */
export function getDict(lang: Lang): Dict {
  return DICTIONARIES[lang];
}

/**
 * Название станции/линии на текущем языке
 * с фолбэком tg → ru → en (dev-conventions.md, §6).
 */
export function pickName(
  name: Partial<I18nName> | undefined,
  lang: Lang,
): string {
  if (!name) {
    return "";
  }
  return name[lang] ?? name.tg ?? name.ru ?? name.en ?? "";
}
