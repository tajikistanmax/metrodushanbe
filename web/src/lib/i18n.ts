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
  NotificationType,
  StationStatus,
  TicketKind,
  TicketStatus,
  TicketValidationReason,
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
  /**
   * Skip-link к основному содержимому. Живёт в общем шапко-подвальном каркасе
   * (app/layout.tsx), поэтому строка одна на весь портал, а не своя у каждого
   * раздела, как было до появления общего layout.
   */
  skipToContent: string;
  languageSwitcher: string;
  /**
   * Подвал портала. Для государственной платформы это не декор: подвал обязан
   * объяснять НАЗНАЧЕНИЕ сайта, давать язык, канал обращений и — главное —
   * честно помечать, что контур демонстрационный. Тот же принцип, что у
   * DemoBanner: пометка демо не прячется и не закрывается.
   */
  footer: {
    /** aria-label региона <footer>. */
    regionLabel: string;
    /** Заголовок колонки о назначении портала. */
    aboutHeading: string;
    /** Назначение портала в одном абзаце. */
    about: string;
    /** Заголовок колонки со ссылками на разделы. */
    sectionsHeading: string;
    /** Заголовок колонки обратной связи. */
    contactHeading: string;
    /** Пояснение к ссылке на обращения. */
    contactBody: string;
    /** Заголовок колонки выбора языка. */
    languageHeading: string;
    /** Текстовая метка врезки демо-контура (не только цвет — SC 1.4.1). */
    demoLabel: string;
    /** Явная пометка демо-контура в подвале. */
    demoNotice: string;
    /** Правовая строка внизу подвала. */
    legal: string;
  };
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
    /**
     * Построение маршрута двумя нажатиями прямо по карте главной страницы.
     * Отдельный подсловарь: это не планировщик со списками, а режим карты,
     * и состояния у него свои («выбрана только первая станция»).
     */
    map: {
      /** Кнопка включения режима маршрута на карте. */
      toggleOn: string;
      /** Кнопка выхода из режима маршрута. */
      toggleOff: string;
      /** Заголовок панели режима маршрута. */
      title: string;
      /** Ничего не выбрано. */
      hintIdle: string;
      /** Выбрана только первая станция — обязательное состояние. */
      hintFrom: string;
      /** Повторное нажатие по той же станции: маршрут из А в А не строится. */
      hintSame: string;
      /** Что сделает третье нажатие при готовом маршруте. */
      hintReplace: string;
      /** Буква точки отправления на карте и в списке. */
      fromShort: string;
      /** Буква точки назначения на карте и в списке. */
      toShort: string;
      /** Значение поля, пока станция не выбрана. */
      notPicked: string;
      /** Сброс выбора. */
      reset: string;
      /** Переход в планировщик со списками (?from=&to=). */
      openPlanner: string;
      /** Клик по карте недоступен с клавиатуры — где клавиатурный путь. */
      keyboardNote: string;
      /** Кнопка «сделать эту станцию точкой отправления» в панели станции. */
      setFrom: string;
      /** Кнопка «сделать эту станцию точкой назначения» в панели станции. */
      setTo: string;
    };
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
  /**
   * Билеты (U-CIT-08, TKT-02/03/04). ДЕМО-КОНТУР: строки обязаны сообщать
   * пассажиру, что платежа не было и билет недействителен для проезда.
   */
  tickets: {
    nav: string;
    heading: string;
    intro: string;
    skipToContent: string;
    /** Заметная врезка demo-контура над всеми действиями. */
    demoLabel: string;
    demoTitle: string;
    demoBody: string;
    /** Компактный бейдж «демо» на билете и в результате проверки. */
    demoBadge: string;
    /** Пассажир без сети: покупка — операция записи, офлайн невозможна. */
    offlineLabel: string;
    offlineTitle: string;
    offlineBody: string;
    tabs: { buy: string; list: string; manage: string; validate: string };
    /* --- покупка --- */
    buyTitle: string;
    buyIntro: string;
    fareLabel: string;
    faresLoading: string;
    faresEmpty: string;
    /** Выбор тест-сценария demo-эквайринга — существует только в демо. */
    scenarioLabel: string;
    scenarioHint: string;
    scenarios: { approve: string; decline: string };
    /** Пояснение, что карточные данные не собираются и не нужны. */
    noCardNotice: string;
    buySubmit: string;
    buying: string;
    buyAnother: string;
    /* --- выпущенный билет --- */
    issuedLabel: string;
    issuedTitle: string;
    issuedBody: string;
    tokenLabel: string;
    /** Главное предупреждение: токен показывается один раз. */
    tokenWarning: string;
    tokenHint: string;
    /** Почему вместо QR-картинки показан код (TKT-04). */
    tokenQrNote: string;
    copy: string;
    copied: string;
    copyFailed: string;
    /* --- отказ платежа (HTTP 402) --- */
    declinedLabel: string;
    declinedTitle: string;
    declinedBody: string;
    declinedReason: string;
    /* --- карточка билета --- */
    ticketCode: string;
    manageTitle: string;
    manageIntro: string;
    lookupSubmit: string;
    lookingUp: string;
    /** HTTP 404: билета с таким номером нет — действие пассажира «проверьте номер». */
    lookupNotFound: string;
    /** Сервер не ответил: про билет ничего не известно — действие «повторите позже». */
    lookupUnreachable: string;
    fare: string;
    kind: string;
    kinds: Record<TicketKind, string>;
    status: string;
    statuses: Record<TicketStatus, string>;
    category: string;
    price: string;
    balance: string;
    validFrom: string;
    validUntil: string;
    usedAt: string;
    /* --- мои билеты (список на устройстве) --- */
    listTitle: string;
    listIntro: string;
    /** Название live-региона со списком билетов. */
    listRegionLabel: string;
    /** Сколько билетов сохранено на устройстве. */
    listCount: string;
    /** Приватность: localStorage — это устройство, а не учётная запись. */
    listDeviceLabel: string;
    listDeviceTitle: string;
    listDeviceBody: string;
    /** Пустое состояние — человеческим текстом, а не «нет данных». */
    listEmptyTitle: string;
    listEmptyBody: string;
    /** Хранилище недоступно (приватный режим) — список физически невозможен. */
    listStorageBlocked: string;
    listRefresh: string;
    listRefreshing: string;
    listSavedAt: string;
    /** У билета старого формата даты сохранения нет. */
    listSavedAtUnknown: string;
    /** Билет читается как демонстрационный и в списке. */
    listDemoNote: string;
    /* --- честность статуса в списке --- */
    /** Статус не запрашивался или запрос не удался: показывать старый нельзя. */
    listStatusUnknown: string;
    listStatusOffline: string;
    listStatusUnreachable: string;
    /** Сервер ответил 404 — билета с этим номером на сервере нет. */
    listStatusMissing: string;
    listStatusLoading: string;
    /* --- действия над билетом в списке --- */
    listOpen: string;
    listCheck: string;
    /** Токен не сохранён (приватный режим/старое устройство) — проверка невозможна. */
    listNoToken: string;
    listRemove: string;
    listRemoveTitle: string;
    /** Главное: удаление с устройства — не возврат билета. */
    listRemoveBody: string;
    listRemoveConfirm: string;
    listRemoveCancel: string;
    listRemovedLabel: string;
    listRemoved: string;
    /* --- пополнение --- */
    topUpTitle: string;
    topUpIntro: string;
    topUpAmount: string;
    topUpSubmit: string;
    topUpBusy: string;
    topUpDoneLabel: string;
    topUpDone: string;
    topUpUnavailable: string;
    amountInvalid: string;
    /* --- возврат --- */
    refundTitle: string;
    refundIntro: string;
    refundReason: string;
    refundReasonPlaceholder: string;
    refundSubmit: string;
    refundBusy: string;
    refundDoneLabel: string;
    refundDone: string;
    /* --- проверка билета --- */
    validateTitle: string;
    validateIntro: string;
    validateToken: string;
    validateSubmit: string;
    validating: string;
    /** Название live-региона с результатом проверки. */
    validateResultRegion: string;
    validLabel: string;
    validTitle: string;
    invalidLabel: string;
    invalidTitle: string;
    /** Подписи машиночитаемых причин отказа из 200-ответа. */
    reasons: Record<TicketValidationReason, string>;
    required: string;
    /** Подписи кодов ошибок API (единый envelope). */
    errors: Record<string, string>;
    unknownError: string;
  };
  /**
   * Лента рассылок (NTF-01). Не путать с сервисными уведомлениями:
   * алерт — текущее состояние сети (баннер), рассылка — событие в ленте.
   */
  notifications: {
    nav: string;
    heading: string;
    intro: string;
    skipToContent: string;
    /** Врезка, объясняющая разницу с баннером сервисных уведомлений. */
    vsAlertsLabel: string;
    vsAlerts: string;
    filtersTitle: string;
    filterLine: string;
    filterStation: string;
    anyLine: string;
    anyStation: string;
    /** Пометка рассылки без таргетов. */
    networkWide: string;
    /** Префикс списка таргетов. */
    addressedTo: string;
    /** Пометка рассылки, порождённой сервисным алертом. */
    fromAlert: string;
    sentAt: string;
    types: Record<NotificationType, string>;
    loading: string;
    empty: string;
    /** Лента пуста и backend недоступен — офлайн-состояние. */
    offline: string;
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
  skipToContent: "Гузаштан ба мазмуни асосӣ",
  languageSwitcher: "Забон",
  footer: {
    regionLabel: "Маълумоти хидматӣ",
    aboutHeading: "Дар бораи портал",
    about: "Портали расмии иттилоотии Метрои Душанбе: схемаи шабака, истгоҳҳо, масир, тарофаҳо ва муроҷиати шаҳрвандон.",
    sectionsHeading: "Бахшҳо",
    contactHeading: "Бозхӯрд",
    contactBody: "Шикоят, пешниҳод ё савол — тавассути шакли муроҷиат.",
    languageHeading: "Забон",
    demoLabel: "Контури намоишӣ",
    demoNotice: "Ин контури намоишӣ аст. Маълумот тасдиқ нашудааст, пардохтҳо тақлид мешаванд, чиптаҳо барои сафар эътибор надоранд.",
    legal: "Метрои Душанбе — платформаи давлатии иттилоотӣ",
  },
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
    map: {
      toggleOn: "Масир аз рӯи харита",
      toggleOff: "Баромадан аз реҷаи масир",
      title: "Масир аз рӯи харита",
      hintIdle: "Дар харита истгоҳро пахш кунед — он истгоҳи ибтидо мешавад.",
      hintFrom: "Акнун истгоҳи дуюмро пахш кунед — масир худаш сохта мешавад.",
      hintSame:
        "Ин ҳамон истгоҳ аст. Истгоҳи дигарро интихоб кунед: масир аз истгоҳ ба худи он вуҷуд надорад.",
      hintReplace: "Пахши навбатӣ дар харита истгоҳи ниҳоиро иваз мекунад.",
      fromShort: "А",
      toShort: "Б",
      notPicked: "интихоб нашудааст",
      reset: "Тоза кардан",
      openPlanner: "Кушодан дар ҷустуҷӯи масир",
      keyboardNote:
        "Пахши харита бо клавиатура дастрас нест. Ҳамон истгоҳҳоро дар рӯйхати истгоҳҳо ё дар ҷустуҷӯи масир интихоб кардан мумкин аст.",
      setFrom: "Аз ин ҷо",
      setTo: "Ба ин ҷо",
    },
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
  tickets: {
    nav: "Чиптаҳо",
    heading: "Чиптаҳо ва роҳхатҳо",
    intro: "Чиптаи намоишӣ харед, роҳхатро пур кунед, ҳолати чиптаро санҷед ё маблағро баргардонед.",
    skipToContent: "Гузаштан ба мундариҷа",
    demoLabel: "Контури намоишӣ",
    demoTitle: "Ин намоиш аст: пул гирифта намешавад",
    demoBody: "Пардохт танҳо тақлид карда мешавад — маблағ аз ҳисоби шумо гирифта намешавад ва чиптаи бадастомада барои сафар эътибор надорад. Маълумоти корти бонкӣ ҳеҷ гоҳ пурсида намешавад.",
    demoBadge: "Намоишӣ",
    offlineLabel: "Пайваст нест",
    offlineTitle: "Хариду санҷиш бе интернет имконнопазир аст",
    offlineBody: "Хариди чипта, пур кардани роҳхат, санҷиш ва баргардонидани маблағ аз сервер иҷро мешаванд. Ҳангоми барқарор шудани пайваст саҳифа боз дастрас мешавад; чиптаҳои қаблан харидашуда дар дастгоҳи шумо боқӣ мемонанд.",
    tabs: {
      buy: "Харид",
      list: "Чиптаҳои ман",
      manage: "Ҳолати чипта",
      validate: "Санҷиши чипта",
    },
    buyTitle: "Хариди чиптаи намоишӣ",
    buyIntro: "Маҳсулоти тарофавиро интихоб кунед. Нархро сервер аз тарофаи фаъол мегирад.",
    fareLabel: "Маҳсулоти тарофавӣ",
    faresLoading: "Тарофаҳо бор карда мешаванд…",
    faresEmpty: "Тарофаи фаъол дастрас нест",
    scenarioLabel: "Сенарияи санҷишии пардохт",
    scenarioHint: "танҳо дар контури намоишӣ",
    scenarios: { approve: "Тасдиқи пардохт", decline: "Радди пардохт" },
    noCardNotice: "Маълумоти корти бонкӣ дар ин саҳифа ҷамъоварӣ намешавад ва сервер онро қабул намекунад.",
    buySubmit: "Хариди чиптаи намоишӣ",
    buying: "Иҷро шуда истодааст…",
    buyAnother: "Хариди чиптаи дигар",
    issuedLabel: "Чипта бароварда шуд",
    issuedTitle: "Чиптаи намоишӣ бароварда шуд",
    issuedBody: "Ин чипта танҳо барои санҷиши платформа аст: пардохт тақлидӣ буд ва чипта барои сафар эътибор надорад.",
    tokenLabel: "Калиди чипта (QR)",
    tokenWarning: "Калидро нигоҳ доред: он танҳо ҳозир нишон дода мешавад ва дигар ҳеҷ гоҳ нишон дода намешавад.",
    tokenHint: "Дар система танҳо ҳеши калид нигоҳ дошта мешавад, бинобар ин калиди гумшуда барқарор карда намешавад.",
    tokenQrNote: "Калид барои QR пешбинӣ шудааст. Ҳоло он ҳамчун матн нишон дода мешавад — тасвири QR баъд аз пайвасти турникетҳои воқеӣ илова карда мешавад.",
    copy: "Нусхабардорӣ",
    copied: "Нусхабардорӣ шуд",
    copyFailed: "Нусхабардорӣ нашуд — калидро дастӣ нависед",
    declinedLabel: "Пардохт рад шуд",
    declinedTitle: "Пардохт рад карда шуд, чипта бароварда нашуд",
    declinedBody: "Ин ҳолати муқаррарии контури намоишӣ аст, на хатогии система. Маблағ гирифта нашуд. Боз кӯшиш кунед.",
    declinedReason: "Сабаби провайдер",
    ticketCode: "Рақами чипта",
    manageTitle: "Ҳолати чипта",
    manageIntro: "Рақами чиптаро ворид кунед. Калид барои ин лозим нест.",
    lookupSubmit: "Нишон додани чипта",
    lookingUp: "Ҷустуҷӯ…",
    lookupNotFound: "Чипта бо чунин рақам ёфт нашуд. Рақамро санҷед: ҳангоми нусхабардорӣ хато кардан осон аст.",
    lookupUnreachable: "Сервер ҷавоб надод, бинобар ин ҳолати чипта номаълум аст. Пайвастро санҷед ва боз кӯшиш кунед.",
    fare: "Тарофа",
    kind: "Навъи чипта",
    kinds: { single: "Сафари яккарата", pass: "Роҳхат" },
    status: "Ҳолат",
    statuses: {
      issued: "Бароварда шуд",
      active: "Фаъол",
      used: "Истифода шуд",
      expired: "Муҳлаташ гузашт",
      refunded: "Маблағ баргардонида шуд",
      blocked: "Баста шуд",
    },
    category: "Категорияи мусофир",
    price: "Нарх",
    balance: "Бақия",
    validFrom: "Аз",
    validUntil: "То",
    usedAt: "Санаи истифода",
    listTitle: "Чиптаҳо дар ин дастгоҳ",
    listIntro: "Дар ин ҷо чиптаҳое ҷамъ шудаанд, ки дар ҳамин браузер харида шудаанд. Ҳолати ҳар чипта аз сервер аз рӯи рақами он пурсида мешавад.",
    listRegionLabel: "Рӯйхати чиптаҳо дар дастгоҳ",
    listCount: "чипта нигоҳ дошта шудааст",
    listDeviceLabel: "Ин дастгоҳ аст, на кабинети шахсӣ",
    listDeviceTitle: "Рӯйхат танҳо дар ҳамин браузер нигоҳ дошта мешавад",
    listDeviceBody: "Портал ҳисоби корбарӣ надорад, бинобар ин рӯйхат ба браузер вобаста аст, на ба шумо: дар браузери дигар, дастгоҳи дигар ё пас аз тоза кардани маълумоти сомона он холӣ мешавад. Барои гум нашудани чипта рақами онро ҷудогона нигоҳ доред.",
    listEmptyTitle: "Ҳоло ин ҷо холӣ аст",
    listEmptyBody: "Чиптаҳое, ки дар ҳамин браузер харида мешаванд, худашон дар рӯйхат пайдо мешаванд. Агар чипта пештар — дар дастгоҳи дигар ё пеш аз тоза кардани маълумот — харида шуда бошад, онро аз рӯи рақам дар варақаи «Ҳолати чипта» кушоед.",
    listStorageBlocked: "Браузер ба сомона иҷозати нигоҳдории маълумот намедиҳад (масалан, дар реҷаи хусусӣ), бинобар ин рӯйхати чиптаҳоро пеш бурдан ғайриимкон аст. Чиптаро аз рӯи рақам дар варақаи «Ҳолати чипта» кушода метавонед.",
    listRefresh: "Навсозии ҳолатҳо",
    listRefreshing: "Навсозӣ…",
    listSavedAt: "Нигоҳ дошта шуд",
    listSavedAtUnknown: "санаи нигоҳдорӣ номаълум",
    listDemoNote: "Чиптаи намоишӣ: пардохт тақлид карда шуд, чипта барои сафар эътибор надорад.",
    listStatusUnknown: "Ҳолат номаълум",
    listStatusOffline: "Рӯйхат аз дастгоҳ хонда мешавад ва бе интернет дастрас аст, вале ҳолати чипта дар сервер нигоҳ дошта мешавад. Бе пайваст мо онро тамоман нишон намедиҳем: чипта метавонист истифода шавад, муҳлаташ гузарад ё маблағаш баргардонида шавад — нишонаи кӯҳна шуморо ба иштибоҳ меандохт.",
    listStatusUnreachable: "Сервер ҷавоб надод, бинобар ин ҳолати ҷории ин чипта номаълум аст. Баъдтар кӯшиш кунед.",
    listStatusMissing: "Сервер чиптаро бо чунин рақам намешиносад. Эҳтимол рақам бо хато нигоҳ дошта шудааст ё чипта дар контури дигар бароварда шудааст.",
    listStatusLoading: "Ҳолат пурсида мешавад…",
    listOpen: "Кушодани чипта",
    listCheck: "Санҷидани чипта",
    listNoToken: "Калиди ин чипта дар дастгоҳ нигоҳ дошта нашудааст, бинобар ин санҷидани он дар ин ҷо имконнопазир аст. Ҳолат аз рӯи рақам ҳамчунон дастрас аст.",
    listRemove: "Аз дастгоҳ хориҷ кардан",
    listRemoveTitle: "Чиптаро аз ин дастгоҳ хориҷ кунем?",
    listRemoveBody: "Ин баргардонидани маблағ нест: чипта бароварда боқӣ мемонад ва маблағ (дар намоиш — тақлидӣ) барнамегардад. Аз браузер танҳо сабти чипта ва калиди он нест мешаванд, ва калидро барқарор кардан ғайриимкон аст. Баргардонидани маблағ ҷудогона — дар варақаи «Ҳолати чипта» расмӣ карда мешавад.",
    listRemoveConfirm: "Ҳа, сабтро хориҷ кунед",
    listRemoveCancel: "Чиптаро нигоҳ доред",
    listRemovedLabel: "Сабт хориҷ шуд",
    listRemoved: "Чипта аз ин дастгоҳ хориҷ карда шуд. Ин баргардонидани маблағ нест: худи чипта вуҷуд дорад ва онро аз рӯи рақам кушодан мумкин аст.",
    topUpTitle: "Пур кардани роҳхат",
    topUpIntro: "Пур кардан муҳлати амали роҳхатро ба муҳлати тарофаи ибтидоӣ дароз мекунад.",
    topUpAmount: "Маблағ",
    topUpSubmit: "Пур кардани роҳхат",
    topUpBusy: "Иҷро шуда истодааст…",
    topUpDoneLabel: "Пур карда шуд",
    topUpDone: "Роҳхат пур карда шуд (намоишӣ: маблағ гирифта нашуд).",
    topUpUnavailable: "Сафари яккарата пур карда намешавад — ин танҳо барои роҳхат аст.",
    amountInvalid: "Маблағи дурустро аз 0,01 то 10 000,00 ворид кунед",
    refundTitle: "Баргардонидани маблағ",
    refundIntro: "Чиптаи истифоданашударо баргардонидан мумкин аст. Асос ҳатмист.",
    refundReason: "Асоси баргардонидан",
    refundReasonPlaceholder: "Сабабро кӯтоҳ нависед",
    refundSubmit: "Баргардонидани маблағ",
    refundBusy: "Иҷро шуда истодааст…",
    refundDoneLabel: "Баргардонида шуд",
    refundDone: "Дархости баргардонидан қабул шуд (намоишӣ: маблағи воқеӣ ҳаракат накард).",
    validateTitle: "Санҷиши чипта",
    validateIntro: "Калиди чиптаро ворид кунед — система мисли турникет ҷавоб медиҳад.",
    validateToken: "Калиди чипта",
    validateSubmit: "Санҷидани чипта",
    validating: "Санҷида мешавад…",
    validateResultRegion: "Натиҷаи санҷиши чипта",
    validLabel: "Иҷозат",
    validTitle: "Чипта эътибор дорад",
    invalidLabel: "Рад",
    invalidTitle: "Чипта эътибор надорад",
    reasons: {
      "ticket.token_unknown": "Чипта бо чунин калид ёфт нашуд",
      "ticket.blocked": "Чипта баста шудааст",
      "ticket.already_used": "Чипта аллакай истифода шудааст",
      "ticket.expired": "Муҳлати амали чипта гузаштааст",
      "ticket.refunded": "Маблағи чипта баргардонида шудааст",
      "ticket.not_started": "Муҳлати амали чипта ҳанӯз оғоз нашудааст",
      "ticket.validation_too_soon": "Санҷиши такрорӣ хеле барвақт аст",
    },
    required: "Майдонҳои ҳатмиро пур кунед",
    errors: {
      "fare.not_found": "Чунин тарофа вуҷуд надорад",
      "ticketing.fare_inactive": "Тарофа фаъол нест",
      "ticketing.fare_not_payable": "Аз рӯи ин тарофа чипта харидан мумкин нест",
      "ticketing.rider_blocked": "Харид барои ин дастгоҳ маҳдуд карда шудааст",
      "ticketing.purchase_limit": "Ҳадди хариди чипта гузашт. Баъдтар кӯшиш кунед.",
      "ticketing.disabled": "Фурӯши чипта муваққатан қатъ шудааст",
      "ticket.not_found": "Чипта бо чунин рақам ёфт нашуд",
      "ticket.topup_not_supported": "Сафари яккарата пур карда намешавад",
      "ticket.not_topupable": "Чиптаи беэътибор пур карда намешавад",
      "ticketing.ticket_blocked": "Чипта баста шудааст",
      "ticket.not_refundable": "Ин чипта баргардонида намешавад",
      "payment.not_refundable": "Пардохти ин чипта баргардонида намешавад",
    },
    unknownError: "Амалиёт иҷро нашуд. Баъдтар кӯшиш кунед.",
  },
  notifications: {
    nav: "Огоҳиномаҳо",
    heading: "Огоҳиномаҳо",
    intro: "Хабарҳои расмии метро: ҳодисаҳо, корҳои нақшавӣ ва иттилооти умумӣ.",
    skipToContent: "Гузаштан ба мундариҷа",
    vsAlertsLabel: "Маълумот",
    vsAlerts: "Дар ин ҷо хабарҳои алоҳида ҷамъ мешаванд. Ҳолати ҷории шабака дар навори болои саҳифа нишон дода мешавад.",
    filtersTitle: "Полоиш",
    filterLine: "Хат",
    filterStation: "Истгоҳ",
    anyLine: "Ҳама хатҳо",
    anyStation: "Ҳама истгоҳҳо",
    networkWide: "Тамоми шабака",
    addressedTo: "Дахл дорад:",
    fromAlert: "Аз огоҳии хидматрасонӣ",
    sentAt: "Фиристода шуд",
    types: {
      info: "Иттилоот",
      warning: "Огоҳӣ",
      incident: "Ҳодиса",
      maintenance: "Корҳои нақшавӣ",
      promo: "Эълон",
    },
    loading: "Бор карда мешавад…",
    empty: "Огоҳиномаҳо нестанд",
    offline: "Огоҳиномаҳоро нишон додан имконнопазир аст: сервер дастрас нест. Ҳангоми барқарор шудани пайваст лента нав мешавад.",
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
  skipToContent: "Перейти к основному содержимому",
  languageSwitcher: "Язык",
  footer: {
    regionLabel: "Служебная информация",
    aboutHeading: "О портале",
    about: "Официальный информационный портал метрополитена Душанбе: схема сети, станции, маршрут, тарифы и обращения граждан.",
    sectionsHeading: "Разделы",
    contactHeading: "Обратная связь",
    contactBody: "Жалоба, предложение или вопрос — через форму обращения.",
    languageHeading: "Язык",
    demoLabel: "Демонстрационный контур",
    demoNotice: "Это демонстрационный контур. Данные не утверждены, платежи имитируются, билеты недействительны для проезда.",
    legal: "Метро Душанбе — государственная информационная платформа",
  },
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
    map: {
      toggleOn: "Маршрут по карте",
      toggleOff: "Выйти из режима маршрута",
      title: "Маршрут по карте",
      hintIdle: "Нажмите станцию на карте — она станет точкой отправления.",
      hintFrom: "Теперь нажмите вторую станцию — маршрут построится сам.",
      hintSame:
        "Это та же станция. Выберите другую: маршрута из станции в неё же не бывает.",
      hintReplace: "Следующее нажатие по карте заменит станцию назначения.",
      fromShort: "А",
      toShort: "Б",
      notPicked: "не выбрана",
      reset: "Сбросить",
      openPlanner: "Открыть в планировщике",
      keyboardNote:
        "Нажатие по карте недоступно с клавиатуры. Те же станции можно выбрать в списке станций или в планировщике маршрута.",
      setFrom: "Отсюда",
      setTo: "Сюда",
    },
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
  tickets: {
    nav: "Билеты",
    heading: "Билеты и проездные",
    intro: "Купите демонстрационный билет, пополните проездной, проверьте статус билета или оформите возврат.",
    skipToContent: "Перейти к содержимому",
    demoLabel: "Демонстрационный контур",
    demoTitle: "Это демонстрация: деньги не списываются",
    demoBody: "Платёж только имитируется — средства с вас не списываются, а выпущенный билет недействителен для проезда. Данные банковской карты не запрашиваются ни на одном шаге.",
    demoBadge: "Демо",
    offlineLabel: "Нет соединения",
    offlineTitle: "Покупка и проверка без интернета невозможны",
    offlineBody: "Покупка билета, пополнение, проверка и возврат выполняются на сервере. Раздел снова заработает, когда соединение восстановится; ранее купленные билеты остаются на вашем устройстве.",
    tabs: {
      buy: "Покупка",
      list: "Мои билеты",
      manage: "Статус билета",
      validate: "Проверка билета",
    },
    buyTitle: "Покупка демонстрационного билета",
    buyIntro: "Выберите тарифный продукт. Цену сервер берёт из действующего тарифа.",
    fareLabel: "Тарифный продукт",
    faresLoading: "Загружаем тарифы…",
    faresEmpty: "Действующих тарифов нет",
    scenarioLabel: "Тестовый сценарий оплаты",
    scenarioHint: "только в демо-контуре",
    scenarios: { approve: "Платёж одобрен", decline: "Платёж отклонён" },
    noCardNotice: "Данные банковской карты на этой странице не собираются, и сервер их не принимает.",
    buySubmit: "Купить демонстрационный билет",
    buying: "Выполняем…",
    buyAnother: "Купить ещё один билет",
    issuedLabel: "Билет выпущен",
    issuedTitle: "Демонстрационный билет выпущен",
    issuedBody: "Билет предназначен только для проверки платформы: платёж был имитирован, для проезда билет недействителен.",
    tokenLabel: "Ключ билета (QR)",
    tokenWarning: "Сохраните ключ: он показывается только сейчас и повторно показан не будет.",
    tokenHint: "В системе хранится лишь хеш ключа, поэтому потерянный ключ восстановить невозможно.",
    tokenQrNote: "Ключ предназначен для QR-кода. Пока он показан текстом — изображение QR добавим вместе с подключением реальных турникетов.",
    copy: "Копировать",
    copied: "Скопировано",
    copyFailed: "Скопировать не удалось — перепишите ключ вручную",
    declinedLabel: "Платёж отклонён",
    declinedTitle: "Платёж отклонён, билет не выпущен",
    declinedBody: "Это штатный сценарий демо-контура, а не сбой системы. Деньги не списаны. Попробуйте ещё раз.",
    declinedReason: "Причина от провайдера",
    ticketCode: "Номер билета",
    manageTitle: "Статус билета",
    manageIntro: "Введите номер билета. Ключ для этого не нужен.",
    lookupSubmit: "Показать билет",
    lookingUp: "Ищем…",
    lookupNotFound: "Билет с таким номером не найден. Проверьте номер: в нём легко ошибиться при переписывании.",
    lookupUnreachable: "Сервер не ответил, поэтому статус билета неизвестен. Проверьте соединение и повторите попытку.",
    fare: "Тариф",
    kind: "Вид билета",
    kinds: { single: "Разовая поездка", pass: "Проездной" },
    status: "Статус",
    statuses: {
      issued: "Выпущен",
      active: "Действует",
      used: "Использован",
      expired: "Срок истёк",
      refunded: "Возвращён",
      blocked: "Заблокирован",
    },
    category: "Категория пассажира",
    price: "Стоимость",
    balance: "Остаток",
    validFrom: "Действует с",
    validUntil: "Действует до",
    usedAt: "Использован",
    listTitle: "Билеты на этом устройстве",
    listIntro: "Здесь собраны билеты, купленные в этом браузере. Статус каждого билета запрашивается у сервера по его номеру.",
    listRegionLabel: "Список билетов на устройстве",
    listCount: "билетов сохранено",
    listDeviceLabel: "Это устройство, а не личный кабинет",
    listDeviceTitle: "Список хранится только в этом браузере",
    listDeviceBody: "Учётных записей у портала нет, поэтому список привязан к браузеру, а не к вам: в другом браузере, на другом устройстве или после очистки данных сайта он будет пустым. Чтобы билет не потерялся, сохраните его номер отдельно.",
    listEmptyTitle: "Пока здесь пусто",
    listEmptyBody: "Билеты, купленные в этом браузере, появятся в списке сами. Если билет куплен раньше — на другом устройстве или до очистки данных сайта, — откройте его по номеру на вкладке «Статус билета».",
    listStorageBlocked: "Браузер не разрешает сайту хранить данные (например, в приватном режиме), поэтому список билетов вести негде. Открыть билет по номеру можно на вкладке «Статус билета».",
    listRefresh: "Обновить статусы",
    listRefreshing: "Обновляем…",
    listSavedAt: "Сохранён",
    listSavedAtUnknown: "дата сохранения неизвестна",
    listDemoNote: "Демонстрационный билет: оплата была имитирована, права на проезд билет не даёт.",
    listStatusUnknown: "Статус неизвестен",
    listStatusOffline: "Список читается с устройства и доступен без интернета, но статус билета хранится на сервере. Без соединения мы не показываем его вовсе: билет мог быть погашен, просрочен или возвращён, и старая отметка ввела бы вас в заблуждение.",
    listStatusUnreachable: "Сервер не ответил, поэтому текущий статус этого билета неизвестен. Повторите позже.",
    listStatusMissing: "Сервер не знает билета с таким номером. Возможно, номер сохранён с ошибкой или билет выпущен в другом контуре.",
    listStatusLoading: "Запрашиваем статус…",
    listOpen: "Открыть билет",
    listCheck: "Проверить билет",
    listNoToken: "Ключ этого билета на устройстве не сохранён, поэтому проверить его здесь нельзя. Статус по номеру по-прежнему доступен.",
    listRemove: "Убрать с устройства",
    listRemoveTitle: "Убрать билет с этого устройства?",
    listRemoveBody: "Это не возврат: билет останется выпущенным, а деньги (в демо — имитированные) никуда не вернутся. Из браузера пропадут только запись о билете и его ключ, а ключ восстановить невозможно. Возврат оформляется отдельно — на вкладке «Статус билета».",
    listRemoveConfirm: "Да, убрать запись",
    listRemoveCancel: "Оставить билет",
    listRemovedLabel: "Запись убрана",
    listRemoved: "Билет убран с этого устройства. Это не возврат: сам билет продолжает существовать, и его можно открыть по номеру.",
    topUpTitle: "Пополнение проездного",
    topUpIntro: "Пополнение продлевает срок действия проездного на срок исходного тарифа.",
    topUpAmount: "Сумма",
    topUpSubmit: "Пополнить проездной",
    topUpBusy: "Выполняем…",
    topUpDoneLabel: "Пополнено",
    topUpDone: "Проездной пополнен (демо: деньги не списаны).",
    topUpUnavailable: "Разовая поездка не пополняется — пополнение доступно только для проездного.",
    amountInvalid: "Введите сумму от 0,01 до 10 000,00",
    refundTitle: "Возврат билета",
    refundIntro: "Вернуть можно неиспользованный билет. Основание обязательно.",
    refundReason: "Основание возврата",
    refundReasonPlaceholder: "Кратко опишите причину",
    refundSubmit: "Оформить возврат",
    refundBusy: "Выполняем…",
    refundDoneLabel: "Возврат оформлен",
    refundDone: "Возврат оформлен (демо: реальные деньги не двигались).",
    validateTitle: "Проверка билета",
    validateIntro: "Введите ключ билета — система ответит так же, как турникет.",
    validateToken: "Ключ билета",
    validateSubmit: "Проверить билет",
    validating: "Проверяем…",
    validateResultRegion: "Результат проверки билета",
    validLabel: "Проход разрешён",
    validTitle: "Билет действителен",
    invalidLabel: "Проход запрещён",
    invalidTitle: "Билет недействителен",
    reasons: {
      "ticket.token_unknown": "Билет с таким ключом не найден",
      "ticket.blocked": "Билет заблокирован",
      "ticket.already_used": "Билет уже использован",
      "ticket.expired": "Срок действия билета истёк",
      "ticket.refunded": "Билет возвращён",
      "ticket.not_started": "Срок действия билета ещё не начался",
      "ticket.validation_too_soon": "Повторная проверка слишком рано",
    },
    required: "Заполните обязательные поля",
    errors: {
      "fare.not_found": "Такого тарифа не существует",
      "ticketing.fare_inactive": "Тариф недействующий",
      "ticketing.fare_not_payable": "По этому тарифу билет купить нельзя",
      "ticketing.rider_blocked": "Покупка для этого устройства ограничена",
      "ticketing.purchase_limit": "Превышен лимит покупок билетов. Попробуйте позже.",
      "ticketing.disabled": "Продажа билетов временно приостановлена",
      "ticket.not_found": "Билет с таким номером не найден",
      "ticket.topup_not_supported": "Разовая поездка не пополняется",
      "ticket.not_topupable": "Недействующий билет пополнить нельзя",
      "ticketing.ticket_blocked": "Билет заблокирован",
      "ticket.not_refundable": "Этот билет вернуть нельзя",
      "payment.not_refundable": "Платёж по этому билету вернуть нельзя",
    },
    unknownError: "Не удалось выполнить операцию. Попробуйте позже.",
  },
  notifications: {
    nav: "Уведомления",
    heading: "Уведомления",
    intro: "Официальные сообщения метро: инциденты, плановые работы и общая информация.",
    skipToContent: "Перейти к содержимому",
    vsAlertsLabel: "Справка",
    vsAlerts: "Здесь собраны отдельные сообщения. Текущее состояние сети показывает полоса в верхней части страницы.",
    filtersTitle: "Фильтры",
    filterLine: "Линия",
    filterStation: "Станция",
    anyLine: "Все линии",
    anyStation: "Все станции",
    networkWide: "Вся сеть",
    addressedTo: "Затрагивает:",
    fromAlert: "По сервисному уведомлению",
    sentAt: "Отправлено",
    types: {
      info: "Информация",
      warning: "Предупреждение",
      incident: "Инцидент",
      maintenance: "Плановые работы",
      promo: "Объявление",
    },
    loading: "Загружаем…",
    empty: "Уведомлений нет",
    offline: "Показать уведомления не удалось: сервер недоступен. Лента обновится, когда соединение восстановится.",
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
  skipToContent: "Skip to main content",
  languageSwitcher: "Language",
  footer: {
    regionLabel: "Service information",
    aboutHeading: "About this portal",
    about: "The official information portal of the Dushanbe Metro: network map, stations, route planner, fares and citizen requests.",
    sectionsHeading: "Sections",
    contactHeading: "Contact",
    contactBody: "Complaints, suggestions and questions go through the request form.",
    languageHeading: "Language",
    demoLabel: "Demonstration environment",
    demoNotice: "This is a demonstration environment. Data is not approved, payments are simulated and tickets are not valid for travel.",
    legal: "Dushanbe Metro — state information platform",
  },
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
    map: {
      toggleOn: "Route on the map",
      toggleOff: "Exit route mode",
      title: "Route on the map",
      hintIdle: "Tap a station on the map — it becomes your origin.",
      hintFrom: "Now tap a second station — the route is built automatically.",
      hintSame:
        "That is the same station. Pick a different one: there is no route from a station to itself.",
      hintReplace: "The next tap on the map replaces the destination.",
      fromShort: "A",
      toShort: "B",
      notPicked: "not selected",
      reset: "Reset",
      openPlanner: "Open in the route planner",
      keyboardNote:
        "Tapping the map is not available from the keyboard. The same stations can be picked in the station list or in the route planner.",
      setFrom: "From here",
      setTo: "To here",
    },
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
  tickets: {
    nav: "Tickets",
    heading: "Tickets and passes",
    intro: "Buy a demonstration ticket, top up a pass, check a ticket status, or request a refund.",
    skipToContent: "Skip to content",
    demoLabel: "Demonstration environment",
    demoTitle: "This is a demonstration: no money is charged",
    demoBody: "Payments are only simulated — nothing is charged to you, and the issued ticket is not valid for travel. Card details are never requested at any step.",
    demoBadge: "Demo",
    offlineLabel: "No connection",
    offlineTitle: "Buying and checking tickets require a connection",
    offlineBody: "Purchases, top-ups, validation, and refunds are performed on the server. This section will work again once the connection is restored; tickets bought earlier stay on your device.",
    tabs: {
      buy: "Buy",
      list: "My tickets",
      manage: "Ticket status",
      validate: "Check a ticket",
    },
    buyTitle: "Buy a demonstration ticket",
    buyIntro: "Choose a fare product. The price is taken by the server from the active fare.",
    fareLabel: "Fare product",
    faresLoading: "Loading fares…",
    faresEmpty: "No active fares available",
    scenarioLabel: "Payment test scenario",
    scenarioHint: "demo environment only",
    scenarios: { approve: "Payment approved", decline: "Payment declined" },
    noCardNotice: "Card details are not collected on this page, and the server does not accept them.",
    buySubmit: "Buy a demonstration ticket",
    buying: "Processing…",
    buyAnother: "Buy another ticket",
    issuedLabel: "Ticket issued",
    issuedTitle: "Demonstration ticket issued",
    issuedBody: "This ticket exists only to test the platform: the payment was simulated, and the ticket is not valid for travel.",
    tokenLabel: "Ticket key (QR)",
    tokenWarning: "Save the key: it is shown only now and will never be shown again.",
    tokenHint: "Only a hash of the key is stored, so a lost key cannot be recovered.",
    tokenQrNote: "The key is meant for a QR code. For now it is shown as text — the QR image will arrive together with real turnstiles.",
    copy: "Copy",
    copied: "Copied",
    copyFailed: "Copying failed — write the key down manually",
    declinedLabel: "Payment declined",
    declinedTitle: "Payment declined, no ticket issued",
    declinedBody: "This is a normal demo scenario, not a system failure. Nothing was charged. Please try again.",
    declinedReason: "Provider reason",
    ticketCode: "Ticket number",
    manageTitle: "Ticket status",
    manageIntro: "Enter the ticket number. The key is not needed for this.",
    lookupSubmit: "Show ticket",
    lookingUp: "Searching…",
    lookupNotFound: "No ticket matches this number. Check the number — it is easy to mistype when copying it.",
    lookupUnreachable: "The server did not respond, so the ticket status is unknown. Check your connection and try again.",
    fare: "Fare",
    kind: "Ticket type",
    kinds: { single: "Single ride", pass: "Pass" },
    status: "Status",
    statuses: {
      issued: "Issued",
      active: "Active",
      used: "Used",
      expired: "Expired",
      refunded: "Refunded",
      blocked: "Blocked",
    },
    category: "Rider category",
    price: "Price",
    balance: "Balance",
    validFrom: "Valid from",
    validUntil: "Valid until",
    usedAt: "Used at",
    listTitle: "Tickets on this device",
    listIntro: "These are the tickets bought in this browser. The status of each one is requested from the server by its number.",
    listRegionLabel: "Tickets saved on this device",
    listCount: "tickets saved",
    listDeviceLabel: "This is a device, not an account",
    listDeviceTitle: "The list is kept in this browser only",
    listDeviceBody: "The portal has no user accounts, so the list belongs to the browser rather than to you: in another browser, on another device, or after clearing site data it will be empty. Keep the ticket number somewhere else so the ticket is not lost.",
    listEmptyTitle: "Nothing here yet",
    listEmptyBody: "Tickets bought in this browser appear here on their own. If a ticket was bought earlier — on another device or before site data was cleared — open it by number on the “Ticket status” tab.",
    listStorageBlocked: "This browser does not let the site store data (private mode, for example), so no list of tickets can be kept. You can still open a ticket by number on the “Ticket status” tab.",
    listRefresh: "Refresh statuses",
    listRefreshing: "Refreshing…",
    listSavedAt: "Saved",
    listSavedAtUnknown: "date saved is unknown",
    listDemoNote: "Demonstration ticket: the payment was simulated, and the ticket is not valid for travel.",
    listStatusUnknown: "Status unknown",
    listStatusOffline: "The list is read from your device and works offline, but a ticket status lives on the server. Without a connection we do not show it at all: the ticket may have been used, expired, or refunded, and a stale marker would mislead you.",
    listStatusUnreachable: "The server did not respond, so the current status of this ticket is unknown. Try again later.",
    listStatusMissing: "The server does not know a ticket with this number. It may have been saved incorrectly, or issued in a different environment.",
    listStatusLoading: "Requesting status…",
    listOpen: "Open ticket",
    listCheck: "Check ticket",
    listNoToken: "The key for this ticket is not stored on this device, so it cannot be checked here. The status by number is still available.",
    listRemove: "Remove from device",
    listRemoveTitle: "Remove this ticket from this device?",
    listRemoveBody: "This is not a refund: the ticket stays issued and the money (simulated, in this demo) does not come back. Only the record and its key disappear from the browser, and the key cannot be recovered. Refunds are requested separately, on the “Ticket status” tab.",
    listRemoveConfirm: "Yes, remove the record",
    listRemoveCancel: "Keep the ticket",
    listRemovedLabel: "Record removed",
    listRemoved: "The ticket has been removed from this device. This is not a refund: the ticket still exists and can be opened by its number.",
    topUpTitle: "Top up a pass",
    topUpIntro: "A top-up extends the pass by the validity period of its original fare.",
    topUpAmount: "Amount",
    topUpSubmit: "Top up the pass",
    topUpBusy: "Processing…",
    topUpDoneLabel: "Topped up",
    topUpDone: "The pass has been topped up (demo: nothing was charged).",
    topUpUnavailable: "A single ride cannot be topped up — top-ups apply to passes only.",
    amountInvalid: "Enter an amount between 0.01 and 10,000.00",
    refundTitle: "Refund a ticket",
    refundIntro: "An unused ticket can be refunded. A reason is required.",
    refundReason: "Refund reason",
    refundReasonPlaceholder: "Briefly describe the reason",
    refundSubmit: "Request a refund",
    refundBusy: "Processing…",
    refundDoneLabel: "Refund recorded",
    refundDone: "The refund has been recorded (demo: no real money moved).",
    validateTitle: "Check a ticket",
    validateIntro: "Enter the ticket key — the system answers exactly as a turnstile would.",
    validateToken: "Ticket key",
    validateSubmit: "Check the ticket",
    validating: "Checking…",
    validateResultRegion: "Ticket check result",
    validLabel: "Entry allowed",
    validTitle: "The ticket is valid",
    invalidLabel: "Entry refused",
    invalidTitle: "The ticket is not valid",
    reasons: {
      "ticket.token_unknown": "No ticket matches this key",
      "ticket.blocked": "The ticket is blocked",
      "ticket.already_used": "The ticket has already been used",
      "ticket.expired": "The ticket has expired",
      "ticket.refunded": "The ticket has been refunded",
      "ticket.not_started": "The ticket is not valid yet",
      "ticket.validation_too_soon": "Repeat check attempted too soon",
    },
    required: "Complete the required fields",
    errors: {
      "fare.not_found": "No such fare exists",
      "ticketing.fare_inactive": "The fare is not active",
      "ticketing.fare_not_payable": "Tickets cannot be bought under this fare",
      "ticketing.rider_blocked": "Purchases from this device are restricted",
      "ticketing.purchase_limit": "Ticket purchase limit reached. Try again later.",
      "ticketing.disabled": "Ticket sales are temporarily suspended",
      "ticket.not_found": "No ticket matches this number",
      "ticket.topup_not_supported": "A single ride cannot be topped up",
      "ticket.not_topupable": "An inactive ticket cannot be topped up",
      "ticketing.ticket_blocked": "The ticket is blocked",
      "ticket.not_refundable": "This ticket cannot be refunded",
      "payment.not_refundable": "The payment for this ticket cannot be refunded",
    },
    unknownError: "The operation could not be completed. Try again later.",
  },
  notifications: {
    nav: "Notifications",
    heading: "Notifications",
    intro: "Official metro messages: incidents, planned works, and general information.",
    skipToContent: "Skip to content",
    vsAlertsLabel: "About this page",
    vsAlerts: "This page collects individual messages. The current state of the network is shown by the banner at the top of the page.",
    filtersTitle: "Filters",
    filterLine: "Line",
    filterStation: "Station",
    anyLine: "All lines",
    anyStation: "All stations",
    networkWide: "Whole network",
    addressedTo: "Affects:",
    fromAlert: "From a service alert",
    sentAt: "Sent",
    types: {
      info: "Information",
      warning: "Warning",
      incident: "Incident",
      maintenance: "Planned works",
      promo: "Announcement",
    },
    loading: "Loading…",
    empty: "No notifications",
    offline: "Notifications could not be loaded: the server is unavailable. The feed will refresh once the connection is restored.",
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
