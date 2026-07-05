/**
 * Лёгкая i18n без сторонних библиотек (dev-conventions.md, §6).
 * Языки: tg (по умолчанию), ru, en. Все строки UI — здесь, без хардкода.
 * React-контекст и переключатель — в src/components/I18nProvider.tsx и Header.tsx.
 */

import type {
  AccessibilityFeature,
  AccessibilityFeatureStatus,
  AlertSeverity,
  I18nName,
  LineStatus,
  StationStatus,
} from "./types";

export type Lang = "tg" | "ru" | "en";

export const LANGS: readonly Lang[] = ["tg", "ru", "en"] as const;

export const DEFAULT_LANG: Lang = "tg";

/** Ключ в localStorage для сохранения выбранного языка. */
export const LANG_STORAGE_KEY = "metro-dushanbe.lang";

/** Подписи самих языков (для aria-label кнопок переключателя). */
export const LANG_LABELS: Record<Lang, string> = {
  tg: "Тоҷикӣ",
  ru: "Русский",
  en: "English",
};

/** Короткие подписи для segmented-переключателя в шапке. */
export const LANG_SHORT_LABELS: Record<Lang, string> = {
  tg: "TG",
  ru: "RU",
  en: "EN",
};

/** Словарь строк UI одного языка. */
export type Dict = {
  appTitle: string;
  demoBanner: string;
  demoDismiss: string;
  /** Сервисные уведомления (ТЗ §6.2.6). */
  alertsRegionLabel: string;
  alertDismiss: string;
  /** Префикс списка целей уведомления («Затронуто:»). */
  alertAffected: string;
  alertSeverity: Record<AlertSeverity, string>;
  skipToList: string;
  languageSwitcher: string;
  mapRegionLabel: string;
  mapLoading: string;
  stationsHeading: string;
  searchLabel: string;
  searchPlaceholder: string;
  searchNoResults: string;
  panelCollapse: string;
  panelExpand: string;
  themeLabel: string;
  themeAuto: string;
  themeLight: string;
  themeDark: string;
  legendHeading: string;
  legendStation: string;
  legendTransfer: string;
  /** Префикс бейджа линии: «Л» (tg/ru) или «L» (en) + номер. */
  lineBadgePrefix: string;
  dataSourceLabel: string;
  dataSourceApi: string;
  dataSourceDemo: string;
  dataSourceDemoShort: string;
  loading: string;
  loadError: string;
  popupLines: string;
  popupAccessibility: string;
  popupStatus: string;
  popupClose: string;
  transferBadge: string;
  /** Детальная карточка станции (GET /stations/{code}). */
  exitsHeading: string;
  accessibilityFeaturesHeading: string;
  exitAccessible: string;
  exitNotAccessible: string;
  detailsLoading: string;
  detailsUnavailable: string;
  detailsNoExits: string;
  detailsNoFeatures: string;
  accessibility: Record<AccessibilityFeature, string>;
  featureStatus: Record<AccessibilityFeatureStatus, string>;
  status: Record<LineStatus | StationStatus, string>;
  /** Публичный раздел новостей (ТЗ §6.2.7) и навигация шапки. */
  news: {
    /** Подпись ссылки на раздел новостей в шапке. */
    nav: string;
    /** Подпись ссылки на карту (главную) в шапке. */
    mapNav: string;
    /** Заголовок раздела и <title>-подобный H1. */
    heading: string;
    /** Skip-link к содержимому страницы. */
    skipToContent: string;
    /** Пустой список новостей. */
    empty: string;
    /** Кнопка/ссылка «читать статью». */
    read: string;
    /** Ссылка возврата к списку со страницы статьи. */
    backToList: string;
    /** Префикс даты публикации (aria/визуально). */
    publishedLabel: string;
    /** Состояние «статья не найдена» (null со страницы статьи). */
    notFoundTitle: string;
    notFoundBody: string;
  };
  /** Маршрутный поиск «откуда/куда» (GET /routes). */
  route: {
    /** Подпись ссылки на поиск маршрута в шапке. */
    nav: string;
    /** Заголовок раздела. */
    heading: string;
    /** Вводная подпись под заголовком. */
    intro: string;
    /** Skip-link к содержимому. */
    skipToContent: string;
    fromLabel: string;
    toLabel: string;
    fromPlaceholder: string;
    toPlaceholder: string;
    /** Кнопка обмена «откуда»/«куда» местами. */
    swap: string;
    /** Кнопка построения маршрута. */
    submit: string;
    /** Состояние «строим маршрут». */
    building: string;
    /** Ошибка сети/недоступный backend (в отличие от «нет пути»). */
    error: string;
    /** Подсказка при совпадении станций. */
    sameStation: string;
    /** Подсказка, пока не выбраны обе станции. */
    selectBoth: string;
    /** Валидный ответ found:false — пути между станциями нет. */
    notFound: string;
    /** Подпись плитки времени в пути. */
    timeLabel: string;
    /** Подпись плитки пересадок. */
    transfersLabel: string;
    /** Подпись плитки числа остановок. */
    stopsLabel: string;
    /** Значение плитки при нуле пересадок. */
    transfersNone: string;
    /** Сокращение единицы времени («мин»). */
    minutesSuffix: string;
    /** Сокращение единицы «остановок» для мета-строки участка. */
    stopsCountSuffix: string;
    /** Пометка, что время — оценочное (до реального расписания). */
    estimateNote: string;
    /** Заголовок списка участков по линиям. */
    legsHeading: string;
    /** Заголовок списка остановок маршрута. */
    stopsHeading: string;
    /** Префикс метки пересадки между участками (+ название станции). */
    transferAt: string;
  };
};

const tg: Dict = {
  appTitle: "Метрои Душанбе",
  demoBanner: "Нақшаи намоишӣ — маълумот тасдиқ нашудааст",
  demoDismiss: "Пинҳон кардани огоҳӣ",
  alertsRegionLabel: "Огоҳиҳои хидматрасонӣ",
  alertDismiss: "Пӯшидани огоҳӣ",
  alertAffected: "Дахл дорад:",
  alertSeverity: {
    info: "Маълумот",
    warning: "Огоҳӣ",
    critical: "Фавқулодда",
  },
  skipToList: "Гузаштан ба рӯйхати истгоҳҳо",
  languageSwitcher: "Забон",
  mapRegionLabel: "Харитаи шабакаи метро",
  mapLoading: "Харита бор мешавад…",
  stationsHeading: "Истгоҳҳо",
  searchLabel: "Ҷустуҷӯи истгоҳ",
  searchPlaceholder: "Ҷустуҷӯи истгоҳ…",
  searchNoResults: "Ҳеҷ чиз ёфт нашуд",
  panelCollapse: "Пинҳон кардани рӯйхат",
  panelExpand: "Кушодани рӯйхат",
  themeLabel: "Намуди зоҳирӣ",
  themeAuto: "Худкор",
  themeLight: "Равшан",
  themeDark: "Торик",
  legendHeading: "Аломатҳо",
  legendStation: "Истгоҳ",
  legendTransfer: "Гузариш",
  lineBadgePrefix: "Л",
  dataSourceLabel: "Манбаи маълумот",
  dataSourceApi: "API",
  dataSourceDemo: "намоишӣ (офлайн)",
  dataSourceDemoShort: "намоишӣ",
  loading: "Бор шуда истодааст…",
  loadError: "Маълумот бор нашуд",
  popupLines: "Хатҳо",
  popupAccessibility: "Дастрасӣ",
  popupStatus: "Ҳолат",
  popupClose: "Пӯшидан",
  transferBadge: "Гузариш",
  exitsHeading: "Баромадгоҳҳо",
  accessibilityFeaturesHeading: "Объектҳои дастрасӣ",
  exitAccessible: "Дастрас",
  exitNotAccessible: "Дастнорас",
  detailsLoading: "Тафсилот бор мешавад…",
  detailsUnavailable: "Тафсилот дастрас нест",
  detailsNoExits: "Баромадгоҳҳо нишон дода нашудаанд",
  detailsNoFeatures: "Объектҳои дастрасӣ нишон дода нашудаанд",
  accessibility: {
    elevator: "Лифт",
    escalator: "Эскалатор",
    ramp: "Пандус",
    tactile: "Роҳнамои ламсӣ",
    audio_assist: "Ёрии садоӣ",
  },
  featureStatus: {
    available: "Дастрас",
    out_of_service: "Аз кор баромада",
    planned: "Ба нақша гирифташуда",
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
  news: {
    nav: "Ахбор",
    mapNav: "Харита",
    heading: "Ахбор",
    skipToContent: "Гузаштан ба мундариҷа",
    empty: "Ҳоло ахборе нест",
    read: "Хондан",
    backToList: "Ба ҳамаи ахбор",
    publishedLabel: "Нашр шуд",
    notFoundTitle: "Мақола ёфт нашуд",
    notFoundBody:
      "Мумкин аст мақола нашр нашуда бошад ё нишонӣ нодуруст аст.",
  },
  route: {
    nav: "Масир",
    heading: "Ҷустуҷӯи масир",
    intro:
      "Истгоҳҳои ибтидо ва ниҳоро интихоб кунед — портал масири беҳтаринро бо гузаришҳо ва вақти тахминӣ нишон медиҳад.",
    skipToContent: "Гузаштан ба мундариҷа",
    fromLabel: "Аз куҷо",
    toLabel: "Ба куҷо",
    fromPlaceholder: "Истгоҳи ибтидо",
    toPlaceholder: "Истгоҳи ниҳоӣ",
    swap: "Ҷойивазкунии истгоҳҳо",
    submit: "Сохтани масир",
    building: "Масир сохта мешавад…",
    error: "Масир сохта нашуд. Лутфан баъдтар такрор кунед.",
    sameStation: "Ду истгоҳи гуногунро интихоб кунед",
    selectBoth: "Ҳарду истгоҳро интихоб кунед",
    notFound: "Байни ин истгоҳҳо масир ёфт нашуд",
    timeLabel: "Вақти тахминӣ",
    transfersLabel: "Гузаришҳо",
    stopsLabel: "Истгоҳҳо",
    transfersNone: "бе гузариш",
    minutesSuffix: "дақ",
    stopsCountSuffix: "истгоҳ",
    estimateNote: "Вақт тахминист — то интишори ҷадвали расмии ҳаракат.",
    legsHeading: "Қитъаҳо аз рӯи хатҳо",
    stopsHeading: "Истгоҳҳои масир",
    transferAt: "Гузариш дар",
  },
};

const ru: Dict = {
  appTitle: "Метро Душанбе",
  demoBanner: "Демонстрационная схема — данные не утверждены",
  demoDismiss: "Скрыть предупреждение",
  alertsRegionLabel: "Сервисные уведомления",
  alertDismiss: "Скрыть уведомление",
  alertAffected: "Затронуто:",
  alertSeverity: {
    info: "Информация",
    warning: "Предупреждение",
    critical: "Критично",
  },
  skipToList: "Перейти к списку станций",
  languageSwitcher: "Язык",
  mapRegionLabel: "Карта сети метро",
  mapLoading: "Карта загружается…",
  stationsHeading: "Станции",
  searchLabel: "Поиск станции",
  searchPlaceholder: "Поиск станции…",
  searchNoResults: "Ничего не найдено",
  panelCollapse: "Свернуть список",
  panelExpand: "Развернуть список",
  themeLabel: "Тема оформления",
  themeAuto: "Авто",
  themeLight: "Светлая",
  themeDark: "Тёмная",
  legendHeading: "Легенда",
  legendStation: "Станция",
  legendTransfer: "Пересадка",
  lineBadgePrefix: "Л",
  dataSourceLabel: "Источник данных",
  dataSourceApi: "API",
  dataSourceDemo: "демо (офлайн)",
  dataSourceDemoShort: "демо",
  loading: "Загрузка…",
  loadError: "Не удалось загрузить данные",
  popupLines: "Линии",
  popupAccessibility: "Доступность",
  popupStatus: "Статус",
  popupClose: "Закрыть",
  transferBadge: "Пересадка",
  exitsHeading: "Выходы",
  accessibilityFeaturesHeading: "Объекты доступности",
  exitAccessible: "Доступен",
  exitNotAccessible: "Недоступен",
  detailsLoading: "Загрузка деталей…",
  detailsUnavailable: "Детали недоступны",
  detailsNoExits: "Выходы не указаны",
  detailsNoFeatures: "Объекты доступности не указаны",
  accessibility: {
    elevator: "Лифт",
    escalator: "Эскалатор",
    ramp: "Пандус",
    tactile: "Тактильная навигация",
    audio_assist: "Аудиосопровождение",
  },
  featureStatus: {
    available: "Доступен",
    out_of_service: "Не работает",
    planned: "Запланирован",
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
  news: {
    nav: "Новости",
    mapNav: "Карта",
    heading: "Новости",
    skipToContent: "Перейти к содержимому",
    empty: "Новостей пока нет",
    read: "Читать",
    backToList: "Ко всем новостям",
    publishedLabel: "Опубликовано",
    notFoundTitle: "Статья не найдена",
    notFoundBody:
      "Возможно, статья не опубликована или ссылка неверна.",
  },
  route: {
    nav: "Маршрут",
    heading: "Поиск маршрута",
    intro:
      "Выберите станции отправления и назначения — портал покажет оптимальный маршрут с пересадками и оценочным временем в пути.",
    skipToContent: "Перейти к содержимому",
    fromLabel: "Откуда",
    toLabel: "Куда",
    fromPlaceholder: "Станция отправления",
    toPlaceholder: "Станция назначения",
    swap: "Поменять станции местами",
    submit: "Построить маршрут",
    building: "Строим маршрут…",
    error: "Не удалось построить маршрут. Попробуйте позже.",
    sameStation: "Выберите две разные станции",
    selectBoth: "Выберите обе станции",
    notFound: "Маршрут между этими станциями не найден",
    timeLabel: "Время в пути",
    transfersLabel: "Пересадки",
    stopsLabel: "Остановки",
    transfersNone: "без пересадок",
    minutesSuffix: "мин",
    stopsCountSuffix: "ст.",
    estimateNote: "Время оценочное — до публикации официального расписания.",
    legsHeading: "Участки по линиям",
    stopsHeading: "Остановки маршрута",
    transferAt: "Пересадка на",
  },
};

const en: Dict = {
  appTitle: "Dushanbe Metro",
  demoBanner: "Demonstration diagram — data is not approved",
  demoDismiss: "Dismiss warning",
  alertsRegionLabel: "Service alerts",
  alertDismiss: "Dismiss alert",
  alertAffected: "Affected:",
  alertSeverity: {
    info: "Info",
    warning: "Warning",
    critical: "Critical",
  },
  skipToList: "Skip to station list",
  languageSwitcher: "Language",
  mapRegionLabel: "Metro network map",
  mapLoading: "Loading map…",
  stationsHeading: "Stations",
  searchLabel: "Search stations",
  searchPlaceholder: "Search stations…",
  searchNoResults: "Nothing found",
  panelCollapse: "Collapse list",
  panelExpand: "Expand list",
  themeLabel: "Theme",
  themeAuto: "Auto",
  themeLight: "Light",
  themeDark: "Dark",
  legendHeading: "Legend",
  legendStation: "Station",
  legendTransfer: "Transfer",
  lineBadgePrefix: "L",
  dataSourceLabel: "Data source",
  dataSourceApi: "API",
  dataSourceDemo: "demo (offline)",
  dataSourceDemoShort: "demo",
  loading: "Loading…",
  loadError: "Failed to load data",
  popupLines: "Lines",
  popupAccessibility: "Accessibility",
  popupStatus: "Status",
  popupClose: "Close",
  transferBadge: "Transfer",
  exitsHeading: "Exits",
  accessibilityFeaturesHeading: "Accessibility features",
  exitAccessible: "Accessible",
  exitNotAccessible: "Not accessible",
  detailsLoading: "Loading details…",
  detailsUnavailable: "Details unavailable",
  detailsNoExits: "No exits listed",
  detailsNoFeatures: "No accessibility features listed",
  accessibility: {
    elevator: "Elevator",
    escalator: "Escalator",
    ramp: "Ramp",
    tactile: "Tactile guidance",
    audio_assist: "Audio assistance",
  },
  featureStatus: {
    available: "Available",
    out_of_service: "Out of service",
    planned: "Planned",
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
  news: {
    nav: "News",
    mapNav: "Map",
    heading: "News",
    skipToContent: "Skip to content",
    empty: "No news yet",
    read: "Read",
    backToList: "All news",
    publishedLabel: "Published",
    notFoundTitle: "Article not found",
    notFoundBody: "The article may be unpublished or the link is incorrect.",
  },
  route: {
    nav: "Route",
    heading: "Route planner",
    intro:
      "Pick your origin and destination — the portal shows the best route with transfers and an estimated travel time.",
    skipToContent: "Skip to content",
    fromLabel: "From",
    toLabel: "To",
    fromPlaceholder: "Origin station",
    toPlaceholder: "Destination station",
    swap: "Swap stations",
    submit: "Find route",
    building: "Finding route…",
    error: "Could not build the route. Please try again later.",
    sameStation: "Choose two different stations",
    selectBoth: "Select both stations",
    notFound: "No route found between these stations",
    timeLabel: "Travel time",
    transfersLabel: "Transfers",
    stopsLabel: "Stops",
    transfersNone: "no transfers",
    minutesSuffix: "min",
    stopsCountSuffix: "stops",
    estimateNote: "Travel time is an estimate — pending the official timetable.",
    legsHeading: "Legs by line",
    stopsHeading: "Route stops",
    transferAt: "Transfer at",
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

/**
 * Подпись бейджа линии по текущему языку: «Л1»/«Л2» (tg, ru) или «L1» (en).
 * Код линии стабилен и всегда начинается с латинской L (dev-conventions.md, §4).
 */
export function lineBadgeLabel(code: string, lang: Lang): string {
  return code.replace(/^L/i, getDict(lang).lineBadgePrefix);
}
