/**
 * Лёгкая i18n без сторонних библиотек (dev-conventions.md, §6).
 * Языки: tg (по умолчанию), ru, en. Все строки UI — здесь, без хардкода.
 * React-контекст и переключатель — в src/components/I18nProvider.tsx и Header.tsx.
 */

import type {
  AccessibilityFeature,
  AccessibilityFeatureStatus,
  AlertSeverity,
  CitizenRequestStatus,
  CitizenRequestType,
  FareRiderCategory,
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
  alertShowAll: string;
  alertHideAll: string;
  alertCountLabel: string;
  /** Префикс списка целей уведомления («Затронуто:»). */
  alertAffected: string;
  alertSeverity: Record<AlertSeverity, string>;
  mobility: {
    kicker: string;
    title: string;
    hint: string;
    network: string;
    lines: string;
    stations: string;
    alerts: string;
  };
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
  offlineStatus: string;
  offlineTitle: string;
  offlineBody: string;
  offlineRetry: string;
  offlineBack: string;
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
  /** Оценочные прибытия из статического расписания. */
  arrivals: {
    heading: string;
    loading: string;
    unavailable: string;
    inactive: string;
    noUpcoming: string;
    estimated: string;
    headway: string;
    minuteSuffix: string;
    now: string;
    demo: string;
  };
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
  fares: {
    nav: string;
    heading: string;
    intro: string;
    demoNotice: string;
    category: string;
    categories: Record<FareRiderCategory, string>;
    validity: string;
    minutes: string;
    days: string;
    unlimited: string;
  };
  /** Публичная подача и отслеживание обращений граждан. */
  requests: {
    nav: string;
    heading: string;
    intro: string;
    submitTab: string;
    trackTab: string;
    type: string;
    types: Record<CitizenRequestType, string>;
    subject: string;
    subjectPlaceholder: string;
    message: string;
    messagePlaceholder: string;
    contactName: string;
    contactEmail: string;
    contactPhone: string;
    line: string;
    station: string;
    optional: string;
    anyLine: string;
    anyStation: string;
    consent: string;
    submit: string;
    submitting: string;
    successTitle: string;
    successBody: string;
    code: string;
    token: string;
    tokenHint: string;
    copy: string;
    copied: string;
    trackTitle: string;
    trackIntro: string;
    trackSubmit: string;
    tracking: string;
    statuses: Record<CitizenRequestStatus, string>;
    response: string;
    noResponse: string;
    createdAt: string;
    updatedAt: string;
    error: string;
    required: string;
    consentRequired: string;
  };
};

const tg: Dict = {
  appTitle: "Метрои Душанбе",
  demoBanner: "Нақшаи намоишӣ — маълумот тасдиқ нашудааст",
  demoDismiss: "Пинҳон кардани огоҳӣ",
  alertsRegionLabel: "Огоҳиҳои хидматрасонӣ",
  alertDismiss: "Пӯшидани огоҳӣ",
  alertShowAll: "Дидани ҳама",
  alertHideAll: "Пинҳон кардан",
  alertCountLabel: "огоҳии фаъол",
  alertAffected: "Дахл дорад:",
  alertSeverity: {
    info: "Маълумот",
    warning: "Огоҳӣ",
    critical: "Фавқулодда",
  },
  mobility: {
    kicker: "Ҳаракат дар як нигоҳ",
    title: "Сафарро аз харита оғоз кунед",
    hint: "Масир, дастрасӣ ва хизматрасониҳо — дар як ҷо.",
    network: "Шабакаи лоиҳа",
    lines: "хат",
    stations: "истгоҳ",
    alerts: "огоҳӣ",
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
  offlineStatus: "Пайвастшавӣ нест — маълумоти захирашуда истифода мешавад",
  offlineTitle: "Шумо офлайн ҳастед",
  offlineBody: "Харитаи намоишӣ ва саҳифаҳои қаблан кушодашуда дастрасанд. Барои маълумоти нав пайвастшавиро барқарор кунед.",
  offlineRetry: "Аз нав санҷидан",
  offlineBack: "Бозгашт ба харита",
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
  arrivals: {
    heading: "Омадани наздиктарин",
    loading: "Маълумоти омадан бор мешавад…",
    unavailable: "Маълумоти омадан дастрас нест",
    inactive: "Ҳоло ҳаракат дар ин хат фаъол нест",
    noUpcoming: "Омадани навбатӣ имрӯз нест",
    estimated: "Вақтҳо аз рӯи фосилаи ҳаракат ҳисоб шудаанд",
    headway: "Фосила",
    minuteSuffix: "дақ",
    now: "ҳозир",
    demo: "намоишӣ",
  },
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
  fares: {
    nav: "Тарофаҳо",
    heading: "Тарофаҳо ва роҳхатҳо",
    intro: "Нархи сафар ва муҳлати амали маҳсулоти тарофавиро бинед.",
    demoNotice: "Тарофаҳои дорои нишони «намоишӣ» тасдиқ нашудаанд ва танҳо барои санҷиши платформа нишон дода мешаванд.",
    category: "Категорияи мусофир",
    categories: { all: "Ҳама", adult: "Калонсол", child: "Кӯдак", student: "Донишҷӯ", senior: "Солхӯрда" },
    validity: "Муҳлати амал",
    minutes: "дақиқа",
    days: "рӯз",
    unlimited: "Бемаҳдуд",
  },
  requests: {
    nav: "Муроҷиат",
    heading: "Муроҷиати шаҳрвандон",
    intro: "Шикоят, пешниҳод, хабар дар бораи ҳодиса, савол ё маълумот дар бораи ашёи гумшударо ирсол кунед.",
    submitTab: "Муроҷиати нав",
    trackTab: "Санҷиши ҳолат",
    type: "Навъи муроҷиат",
    types: {
      complaint: "Шикоят",
      suggestion: "Пешниҳод",
      incident: "Ҳодиса",
      question: "Савол",
      lost_item: "Ашёи гумшуда",
    },
    subject: "Мавзуъ",
    subjectPlaceholder: "Кӯтоҳ мазмуни муроҷиатро нависед",
    message: "Матни муроҷиат",
    messagePlaceholder: "Вазъиятро муфассал шарҳ диҳед",
    contactName: "Ном",
    contactEmail: "Email",
    contactPhone: "Телефон",
    line: "Хат",
    station: "Истгоҳ",
    optional: "ихтиёрӣ",
    anyLine: "Бе пайваст ба хат",
    anyStation: "Бе пайваст ба истгоҳ",
    consent: "Ман ба коркарди маълумоти пешниҳодшуда барои баррасии муроҷиат розӣ ҳастам.",
    submit: "Ирсоли муроҷиат",
    submitting: "Ирсол шуда истодааст…",
    successTitle: "Муроҷиат қабул шуд",
    successBody: "Рақам ва калиди пайгириро нигоҳ доред. Калид баъдтар дигар нишон дода намешавад.",
    code: "Рақами муроҷиат",
    token: "Калиди пайгирӣ",
    tokenHint: "Ин калид махфӣ аст ва танҳо барои дидани ҳолат истифода мешавад.",
    copy: "Нусхабардорӣ",
    copied: "Нусхабардорӣ шуд",
    trackTitle: "Санҷиши ҳолати муроҷиат",
    trackIntro: "Рақам ва калиди ҳангоми ирсол гирифташударо ворид кунед.",
    trackSubmit: "Санҷидани ҳолат",
    tracking: "Санҷида мешавад…",
    statuses: {
      new: "Нав",
      in_progress: "Дар кор",
      awaiting_info: "Интизори маълумот",
      resolved: "Ҳал шуд",
      closed: "Пӯшида",
      reopened: "Аз нав кушода",
    },
    response: "Ҷавоби оператор",
    noResponse: "Ҷавоб ҳоло нашр нашудааст",
    createdAt: "Ирсол шуд",
    updatedAt: "Навсозӣ шуд",
    error: "Амалиёт иҷро нашуд. Маълумотро санҷед ва боз кӯшиш кунед.",
    required: "Майдонҳои ҳатмиро пур кунед",
    consentRequired: "Барои ирсол розигиро тасдиқ кунед",
  },
};

const ru: Dict = {
  appTitle: "Метро Душанбе",
  demoBanner: "Демонстрационная схема — данные не утверждены",
  demoDismiss: "Скрыть предупреждение",
  alertsRegionLabel: "Сервисные уведомления",
  alertDismiss: "Скрыть уведомление",
  alertShowAll: "Показать все",
  alertHideAll: "Свернуть",
  alertCountLabel: "активных уведомлений",
  alertAffected: "Затронуто:",
  alertSeverity: {
    info: "Информация",
    warning: "Предупреждение",
    critical: "Критично",
  },
  mobility: {
    kicker: "Вся поездка в одном месте",
    title: "Начните маршрут с карты",
    hint: "Маршрут, доступность и городские сервисы — без лишних экранов.",
    network: "Сеть проекта",
    lines: "линии",
    stations: "станций",
    alerts: "уведомления",
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
  offlineStatus: "Нет соединения — используются сохранённые данные",
  offlineTitle: "Вы находитесь офлайн",
  offlineBody: "Демонстрационная карта и ранее открытые страницы доступны. Для свежих данных восстановите соединение.",
  offlineRetry: "Проверить снова",
  offlineBack: "Вернуться к карте",
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
  arrivals: {
    heading: "Ближайшие прибытия",
    loading: "Загружаем ближайшие прибытия…",
    unavailable: "Данные о прибытиях недоступны",
    inactive: "Сейчас движение по этой линии не выполняется",
    noUpcoming: "Ближайших прибытий сегодня больше нет",
    estimated: "Время рассчитано по интервалу движения",
    headway: "Интервал",
    minuteSuffix: "мин",
    now: "сейчас",
    demo: "демо",
  },
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
  fares: {
    nav: "Тарифы",
    heading: "Тарифы и проездные",
    intro: "Узнайте стоимость поездки и срок действия тарифных продуктов.",
    demoNotice: "Тарифы с пометкой «демо» не утверждены и показаны только для проверки платформы.",
    category: "Категория пассажира",
    categories: { all: "Все", adult: "Взрослый", child: "Ребёнок", student: "Студент", senior: "Пенсионер" },
    validity: "Срок действия",
    minutes: "мин",
    days: "дн.",
    unlimited: "Без ограничения",
  },
  requests: {
    nav: "Обращения",
    heading: "Обращения граждан",
    intro: "Отправьте жалобу, предложение, сообщение об инциденте, вопрос или информацию о потерянной вещи.",
    submitTab: "Новое обращение",
    trackTab: "Проверить статус",
    type: "Тип обращения",
    types: {
      complaint: "Жалоба",
      suggestion: "Предложение",
      incident: "Инцидент",
      question: "Вопрос",
      lost_item: "Потерянная вещь",
    },
    subject: "Тема",
    subjectPlaceholder: "Кратко опишите суть обращения",
    message: "Текст обращения",
    messagePlaceholder: "Подробно опишите ситуацию",
    contactName: "Имя",
    contactEmail: "Email",
    contactPhone: "Телефон",
    line: "Линия",
    station: "Станция",
    optional: "необязательно",
    anyLine: "Без привязки к линии",
    anyStation: "Без привязки к станции",
    consent: "Я согласен на обработку предоставленных данных для рассмотрения обращения.",
    submit: "Отправить обращение",
    submitting: "Отправляем…",
    successTitle: "Обращение принято",
    successBody: "Сохраните номер и ключ отслеживания. Ключ больше не будет показан позднее.",
    code: "Номер обращения",
    token: "Ключ отслеживания",
    tokenHint: "Этот ключ секретный и используется только для просмотра статуса.",
    copy: "Копировать",
    copied: "Скопировано",
    trackTitle: "Проверить статус обращения",
    trackIntro: "Введите номер и ключ, полученные после отправки.",
    trackSubmit: "Проверить статус",
    tracking: "Проверяем…",
    statuses: {
      new: "Новое",
      in_progress: "В работе",
      awaiting_info: "Ожидает информации",
      resolved: "Решено",
      closed: "Закрыто",
      reopened: "Открыто повторно",
    },
    response: "Ответ оператора",
    noResponse: "Ответ пока не опубликован",
    createdAt: "Отправлено",
    updatedAt: "Обновлено",
    error: "Не удалось выполнить операцию. Проверьте данные и попробуйте снова.",
    required: "Заполните обязательные поля",
    consentRequired: "Подтвердите согласие перед отправкой",
  },
};

const en: Dict = {
  appTitle: "Dushanbe Metro",
  demoBanner: "Demonstration diagram — data is not approved",
  demoDismiss: "Dismiss warning",
  alertsRegionLabel: "Service alerts",
  alertDismiss: "Dismiss alert",
  alertShowAll: "Show all",
  alertHideAll: "Collapse",
  alertCountLabel: "active alerts",
  alertAffected: "Affected:",
  alertSeverity: {
    info: "Info",
    warning: "Warning",
    critical: "Critical",
  },
  mobility: {
    kicker: "Your journey at a glance",
    title: "Start from the map",
    hint: "Routes, accessibility and city services in one clear place.",
    network: "Project network",
    lines: "lines",
    stations: "stations",
    alerts: "alerts",
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
  offlineStatus: "No connection — saved data is being used",
  offlineTitle: "You are offline",
  offlineBody: "The demo map and previously opened pages remain available. Reconnect for fresh information.",
  offlineRetry: "Try again",
  offlineBack: "Back to map",
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
  arrivals: {
    heading: "Next arrivals",
    loading: "Loading upcoming arrivals…",
    unavailable: "Arrival information is unavailable",
    inactive: "This line is not operating right now",
    noUpcoming: "There are no more arrivals today",
    estimated: "Times are estimated from the service interval",
    headway: "Every",
    minuteSuffix: "min",
    now: "now",
    demo: "demo",
  },
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
  fares: {
    nav: "Fares",
    heading: "Fares and passes",
    intro: "View journey prices and the validity of available fare products.",
    demoNotice: "Products marked “demo” are not approved fares and are shown only for platform testing.",
    category: "Rider category",
    categories: { all: "All riders", adult: "Adult", child: "Child", student: "Student", senior: "Senior" },
    validity: "Validity",
    minutes: "min",
    days: "days",
    unlimited: "Unlimited",
  },
  requests: {
    nav: "Requests",
    heading: "Citizen requests",
    intro: "Submit a complaint, suggestion, incident report, question, or information about a lost item.",
    submitTab: "New request",
    trackTab: "Track status",
    type: "Request type",
    types: {
      complaint: "Complaint",
      suggestion: "Suggestion",
      incident: "Incident",
      question: "Question",
      lost_item: "Lost item",
    },
    subject: "Subject",
    subjectPlaceholder: "Briefly describe your request",
    message: "Request details",
    messagePlaceholder: "Describe the situation in detail",
    contactName: "Name",
    contactEmail: "Email",
    contactPhone: "Phone",
    line: "Line",
    station: "Station",
    optional: "optional",
    anyLine: "No specific line",
    anyStation: "No specific station",
    consent: "I consent to the processing of the provided data for handling this request.",
    submit: "Submit request",
    submitting: "Submitting…",
    successTitle: "Request accepted",
    successBody: "Save the reference number and tracking key. The key will not be shown again later.",
    code: "Request number",
    token: "Tracking key",
    tokenHint: "This key is secret and is used only to view the request status.",
    copy: "Copy",
    copied: "Copied",
    trackTitle: "Track request status",
    trackIntro: "Enter the reference number and key received after submission.",
    trackSubmit: "Track status",
    tracking: "Checking…",
    statuses: {
      new: "New",
      in_progress: "In progress",
      awaiting_info: "Awaiting information",
      resolved: "Resolved",
      closed: "Closed",
      reopened: "Reopened",
    },
    response: "Operator response",
    noResponse: "No response has been published yet",
    createdAt: "Submitted",
    updatedAt: "Updated",
    error: "The operation could not be completed. Check the details and try again.",
    required: "Complete the required fields",
    consentRequired: "Confirm consent before submitting",
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
