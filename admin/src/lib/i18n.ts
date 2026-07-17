/**
 * Лёгкая i18n админ-панели без сторонних библиотек (dev-conventions.md, §6).
 * Языки: tg (по умолчанию), ru, en. Все строки UI — здесь, без хардкода.
 * React-контекст и переключатель — в src/components/I18nProvider.tsx и Sidebar.tsx.
 *
 * Словарь адаптирован под операционную консоль (навигация, таблицы, счётчики);
 * переводы статусов/доступности синхронизированы с web/src/lib/i18n.ts.
 */

import type {
  AccessibilityFeature,
  AlertSeverity,
  CitizenRequestPriority,
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

/** Короткие подписи для segmented-переключателя. */
export const LANG_SHORT_LABELS: Record<Lang, string> = {
  tg: "TG",
  ru: "RU",
  en: "EN",
};

/** Словарь строк UI одного языка. */
export type Dict = {
  appTitle: string;
  appSubtitle: string;
  skipToContent: string;
  languageSwitcher: string;
  themeLabel: string;
  themeAuto: string;
  themeLight: string;
  themeDark: string;
  readOnlyBadge: string;
  readOnlyHint: string;

  /** Навигация (ключи совпадают с сегментами маршрутов). */
  nav: {
    overview: string;
    map: string;
    analytics: string;
    lines: string;
    stations: string;
    alerts: string;
    news: string;
    requests: string;
    fares: string;
    imports: string;
    calendar: string;
    features: string;
    agents: string;
    audit: string;
  };

  /** Заголовки групп разделов в сайдбаре. */
  navGroups: {
    network: string;
    content: string;
    system: string;
  };

  /** Подпись версии/контура внизу сайдбара. */
  sidebarFootnote: string;

  /** Топбар консоли. */
  topbar: {
    searchLabel: string;
    searchPlaceholder: string;
    demoBadge: string;
    demoHint: string;
    userName: string;
    userRole: string;
    logout: string;
  };

  /** Страница входа (макет photo/ChatGPT Image … 13_07_19). */
  login: {
    welcome1: string;
    welcome2Prefix: string;
    welcome2Accent: string;
    heroLead: string;
    badgeSecure: string;
    badgeReliable: string;
    badgeConvenient: string;
    copyright: string;
    title: string;
    subtitle: string;
    usernameLabel: string;
    usernamePlaceholder: string;
    passwordLabel: string;
    passwordPlaceholder: string;
    remember: string;
    forgot: string;
    forgotNote: string;
    submit: string;
    submitting: string;
    or: string;
    ssoButton: string;
    ssoNote: string;
    noAccount: string;
    contactAdmin: string;
    footerPrivacy: string;
    footerTerms: string;
    footerSupport: string;
    errInvalid: string;
    errRequired: string;
    showPassword: string;
    hidePassword: string;
  };

  /** Операционный дашборд. */
  dash: {
    greetingMorning: string;
    greetingDay: string;
    greetingEvening: string;
    greetingNight: string;
    lead: string;
    kpiLinesSub: string;
    kpiStationsSub: string;
    kpiAlertsSub: string;
    kpiNewsSub: string;
    kpiAgentsSub: string;
    networkTitle: string;
    sourceApi: string;
    sourceDemo: string;
    legendStations: string;
    legendTransfer: string;
    viewAll: string;
    alertsEmpty: string;
    quickTitle: string;
    quickLine: string;
    quickStation: string;
    quickAlert: string;
    quickNews: string;
    aiTitle: string;
    aiPosture: string;
    auditTitle: string;
    auditEmpty: string;
    systemTitle: string;
    healthBackend: string;
    healthUp: string;
    healthDown: string;
    healthData: string;
    healthAlerts: string;
    healthCalm: string;
    systemFootnote: string;
    phaseBadge: string;
    heroTitle: string;
    heroLead: string;
    openPublicPortal: string;
    openCityMap: string;
    countryTitle: string;
    countrySubtitle: string;
    cityTitle: string;
    citySubtitle: string;
    countrySource: string;
    capitalLabel: string;
    kpiDataReady: string;
    kpiDataReadySub: string;
    kpiRequests: string;
    kpiRequestsSub: string;
    kpiImports: string;
    kpiImportsSub: string;
    readinessTitle: string;
    readinessLead: string;
    readinessCatalog: string;
    readinessGeo: string;
    readinessContent: string;
    readinessFeedback: string;
    ready: string;
    demo: string;
    needsIntegration: string;
    externalTitle: string;
    externalLead: string;
    externalAfc: string;
    externalRealtime: string;
    externalPayments: string;
    notConnected: string;
    requestQueueTitle: string;
    requestQueueEmpty: string;
    mapPageTitle: string;
    mapPageLead: string;
    mapSearchPlaceholder: string;
    mapStationPanel: string;
    coordinates: string;
    openStationAdmin: string;
  };

  /** Страница аналитики. */
  analytics: {
    title: string;
    lead: string;
    stationsPerLine: string;
    totalStations: string;
    lineLengths: string;
    totalLength: string;
    km: string;
    lengthFootnote: string;
    accessibilityTitle: string;
    alertsBySeverity: string;
    totalAlerts: string;
    stationStatuses: string;
    newsByMonth: string;
  };

  /** Обзорная страница. */
  overviewTitle: string;
  overviewLead: string;
  countLines: string;
  countStations: string;
  countAlerts: string;
  countNews: string;
  countAgents: string;

  /** Состояния таблиц/данных. */
  loading: string;
  loadError: string;
  loadErrorHint: string;
  empty: string;
  total: string;

  /** Заголовки/подписи таблиц. */
  linesTitle: string;
  stationsTitle: string;
  alertsTitle: string;
  newsTitle: string;
  auditTitle: string;

  /** Операционные экраны: импорт, календарь и feature flags. */
  operations: {
    importsTitle: string;
    importsLead: string;
    uploadTitle: string;
    sourceName: string;
    sourceHint: string;
    file: string;
    chooseFile: string;
    runImport: string;
    importing: string;
    jobsTitle: string;
    status: string;
    source: string;
    counts: string;
    created: string;
    updated: string;
    failed: string;
    started: string;
    finished: string;
    showErrors: string;
    hideErrors: string;
    errorsTitle: string;
    noErrors: string;
    invalidFile: string;
    importDone: string;
    calendarTitle: string;
    calendarLead: string;
    newException: string;
    editException: string;
    date: string;
    dayType: string;
    weekday: string;
    weekend: string;
    holiday: string;
    descriptionTg: string;
    descriptionRu: string;
    descriptionEn: string;
    recurring: string;
    featuresTitle: string;
    featuresLead: string;
    flag: string;
    description: string;
    state: string;
    enabled: string;
    disabled: string;
    enable: string;
    disable: string;
    updatedAt: string;
    updatedBy: string;
    toggle: string;
    requestsTitle: string;
    requestsLead: string;
    filterAll: string;
    requestTypes: Record<CitizenRequestType, string>;
    requestStatuses: Record<CitizenRequestStatus, string>;
    requestPriorities: Record<CitizenRequestPriority, string>;
    subject: string;
    message: string;
    contact: string;
    assignedTo: string;
    unassigned: string;
    response: string;
    lineStation: string;
    responseDue: string;
    resolutionDue: string;
    slaBreached: string;
    openRequest: string;
    requestDetails: string;
    faresTitle: string;
    faresLead: string;
    newFare: string;
    editFare: string;
    price: string;
    currency: string;
    riderCategory: string;
    fareCategories: Record<FareRiderCategory, string>;
    validityMinutes: string;
    active: string;
    inactive: string;
  };

  colCode: string;
  colName: string;
  colColor: string;
  colStatus: string;
  colOrder: string;
  colLines: string;
  colTransfer: string;
  colAccessibility: string;
  colSeverity: string;
  colTargets: string;
  colStartsAt: string;
  colEndsAt: string;
  colTitle: string;
  colPublishedAt: string;
  colCover: string;
  colActionType: string;
  colEntityType: string;
  colEntityCode: string;
  colPerformedBy: string;
  colCreatedAt: string;

  yes: string;
  no: string;
  none: string;
  hasCover: string;
  noCover: string;
  openLink: string;

  colActions: string;

  /** Кнопки действий (тулбар, строки, формы). */
  actions: {
    create: string;
    edit: string;
    delete: string;
    publish: string;
    save: string;
    cancel: string;
    close: string;
    saving: string;
  };

  /** Единичные названия сущностей (для заголовков форм). */
  entity: {
    line: string;
    station: string;
    alert: string;
    news: string;
  };

  /** Строки форм создания/редактирования. */
  form: {
    createTitle: string;
    editTitle: string;
    required: string;
    optional: string;
    langTg: string;
    langRu: string;
    langEn: string;
    fieldBody: string;
    fieldSlug: string;
    fieldCoverUrl: string;
    fieldDescription: string;
    fieldCoordinates: string;
    fieldLon: string;
    fieldLat: string;
    fieldPath: string;
    hintPath: string;
    hintCoordinates: string;
    hintCodeImmutable: string;
    fieldTargets: string;
    targetType: string;
    targetCode: string;
    targetLine: string;
    targetStation: string;
    addTarget: string;
    removeTarget: string;
    draftHint: string;
    // Сообщения валидации
    errRequired: string;
    errI18nIncomplete: string;
    errColorHex: string;
    errCoordinates: string;
    errNumber: string;
    errDateRange: string;
    errUrl: string;
    fixErrors: string;
  };

  /** Подтверждение удаления. */
  confirmDelete: {
    title: string;
    text: string;
    hint: string;
  };

  /** Тосты (уведомления об исходе операции). */
  toast: {
    created: string;
    updated: string;
    deleted: string;
    published: string;
    error: string;
  };

  severity: Record<AlertSeverity, string>;
  accessibility: Record<AccessibilityFeature, string>;
  status: Record<LineStatus | StationStatus, string>;
};

const tg: Dict = {
  appTitle: "Консоли идоракунӣ",
  appSubtitle: "Метрои Душанбе",
  skipToContent: "Гузаштан ба мундариҷа",
  languageSwitcher: "Забон",
  themeLabel: "Намуди зоҳирӣ",
  themeAuto: "Худкор",
  themeLight: "Равшан",
  themeDark: "Торик",
  readOnlyBadge: "Танҳо хониш",
  readOnlyHint: "Танҳо намоиш — тағйирот дар итератсияи оянда",
  nav: {
    overview: "Шарҳи умумӣ",
    map: "Харитаи метро",
    analytics: "Таҳлил",
    lines: "Хатҳо",
    stations: "Истгоҳҳо",
    alerts: "Огоҳиҳо",
    news: "Хабарҳо",
    requests: "Муроҷиатҳо",
    fares: "Тарофаҳо",
    imports: "Воридот",
    calendar: "Тақвим",
    features: "Функсияҳо",
    agents: "AI-агентҳо",
    audit: "Аудит",
  },
  navGroups: {
    network: "Шабака",
    content: "Мундариҷа",
    system: "Система",
  },
  sidebarFootnote: "Контури демо · v0.1 · Вазорати нақлиёти ҶТ",
  topbar: {
    searchLabel: "Гузариши зуд",
    searchPlaceholder: "Бахши консол…",
    demoBadge: "Контури демо",
    demoHint: "Маълумоти шабака намунавӣ аст; воридот тавассути конвейери импорт (ТЗ, боби 13)",
    userName: "Маъмур",
    userRole: "Оператори платформа",
    logout: "Баромадан",
  },
  login: {
    welcome1: "Хуш омадед",
    welcome2Prefix: "ба ",
    welcome2Accent: "Метрои Душанбе",
    heroLead:
      "Платформаи ягонаи рақамӣ барои идоракунии метрополитени Душанбе ва пешниҳоди хидматҳо ба мусофирон.",
    badgeSecure: "Бехатар",
    badgeReliable: "Боэътимод",
    badgeConvenient: "Қулай",
    copyright: "© 2026 Метрои Душанбе. Ҳамаи ҳуқуқҳо ҳифз шудаанд.",
    title: "Вуруд ба система",
    subtitle: "Маълумоти худро барои вуруд ворид кунед",
    usernameLabel: "Номи корбар ё email",
    usernamePlaceholder: "Email ё логини худро ворид кунед",
    passwordLabel: "Рамз",
    passwordPlaceholder: "Рамзи худро ворид кунед",
    remember: "Маро дар ёд дор",
    forgot: "Рамзро фаромӯш кардед?",
    forgotNote:
      "Рамзро маъмури система барқарор мекунад — ба хадамоти дастгирӣ муроҷиат кунед.",
    submit: "Ворид шудан",
    submitting: "Ворид шуда истодааст…",
    or: "ё",
    ssoButton: "Вуруд тавассути SSO (системаҳои давлатӣ)",
    ssoNote:
      "SSO-и давлатӣ дар марҳилаи оянда пайваст мешавад (Keycloak, ТЗ §9.2). Ба маъмури система муроҷиат кунед.",
    noAccount: "Ҳисоб надоред?",
    contactAdmin: "Бо маъмур тамос гиред",
    footerPrivacy: "Сиёсати махфият",
    footerTerms: "Шартҳои истифода",
    footerSupport: "Дастгирӣ",
    errInvalid: "Логин ё рамз нодуруст аст",
    errRequired: "Логин ва рамзро ворид кунед",
    showPassword: "Нишон додани рамз",
    hidePassword: "Пинҳон кардани рамз",
  },
  dash: {
    greetingMorning: "Субҳ ба хайр!",
    greetingDay: "Рӯз ба хайр!",
    greetingEvening: "Шом ба хайр!",
    greetingNight: "Шаби хуш!",
    lead: "Хулосаи оперативии платформаи миллии «Метрои Душанбе».",
    kpiLinesSub: "{active} фаъол · {planned} дар нақша",
    kpiStationsSub: "гузаришӣ: {transfer}",
    kpiAlertsSub: "ҳассос: {critical} · огоҳӣ: {warning}",
    kpiNewsSub: "охирин:",
    kpiAgentsSub: "ҳолат:",
    networkTitle: "Нақшаи шабака",
    sourceApi: "маълумоти зинда",
    sourceDemo: "маълумоти демо",
    legendStations: "истгоҳ",
    legendTransfer: "Гузаришҳо",
    viewAll: "Ҳама",
    alertsEmpty: "Огоҳии фаъоли нашршуда нест",
    quickTitle: "Амалҳои зуд",
    quickLine: "Иловаи хат",
    quickStation: "Иловаи истгоҳ",
    quickAlert: "Эҷоди огоҳӣ",
    quickNews: "Навиштани хабар",
    aiTitle: "Брифинги AI",
    aiPosture: "Омодагӣ",
    auditTitle: "Тағйироти охирин",
    auditEmpty: "Ҳоло сабтҳои аудит нест",
    systemTitle: "Ҳолати системаҳо",
    healthBackend: "Backend API",
    healthUp: "фаъол",
    healthDown: "дастнорас",
    healthData: "Маълумоти шабака",
    healthAlerts: "Вазъи рӯйдодҳо",
    healthCalm: "ором",
      systemFootnote:
        "Ҳолатҳо ҳангоми боркунии саҳифа аз ҷавобҳои backend (actuator/health) ва API-и оммавӣ ҳисоб карда мешаванд.",
      phaseBadge: "Шабакаи тарҳрезишаванда · контури намоишӣ",
      heroTitle: "Маркази рақамии лоиҳаи Метрои Душанбе",
      heroLead: "Ҷуғрофия, маълумоти шабака, муроҷиатҳои шаҳрвандон ва омодагии ҳамгироиҳо дар як экран.",
      openPublicPortal: "Кушодани портали оммавӣ",
      openCityMap: "Кушодани харитаи Душанбе",
      countryTitle: "Харитаи Тоҷикистон",
      countrySubtitle: "Ҷойгиршавии лоиҳа дар миқёси кишвар",
      cityTitle: "Харитаи шаҳри Душанбе",
      citySubtitle: "Хатҳои намунавии шабака дар координатҳои ҷуғрофӣ",
      countrySource: "Контур: Natural Earth · WGS84",
      capitalLabel: "Душанбе · пойтахт",
      kpiDataReady: "Омодагии геомаълумот",
      kpiDataReadySub: "координата ва пайвастагӣ пур шудааст",
      kpiRequests: "Навбати муроҷиатҳо",
      kpiRequestsSub: "кушода ва дар кор",
      kpiImports: "Воридоти маълумот",
      kpiImportsSub: "кори охирин: {status}",
      readinessTitle: "Омодагии платформа",
      readinessLead: "Танҳо ҳолатҳое, ки аз маълумоти воқеии контури ҷорӣ ҳисоб шудаанд.",
      readinessCatalog: "Феҳристи хатҳо ва истгоҳҳо",
      readinessGeo: "GeoJSON ва координатаҳо",
      readinessContent: "Хабарҳо ва огоҳиҳои оммавӣ",
      readinessFeedback: "Муроҷиатҳои шаҳрвандон",
      ready: "Омода",
      demo: "Демо",
      needsIntegration: "Ҳамгироӣ лозим",
      externalTitle: "Системаҳои беруна",
      externalLead: "Ин модулҳо баъд аз пайдо шудани манбаъҳои саноатӣ фаъол мешаванд.",
      externalAfc: "AFC / мусофиршуморӣ",
      externalRealtime: "Диспетчерӣ / ҳаракати қаторҳо",
      externalPayments: "Эквайринг ва билетҳо",
      notConnected: "пайваст нест",
      requestQueueTitle: "Муроҷиатҳои охирин",
      requestQueueEmpty: "Муроҷиати кушода нест",
      mapPageTitle: "Харитаи метрои Душанбе",
      mapPageLead: "Схемаи интерактивии шабакаи намунавӣ бо ҷустуҷӯи истгоҳ, координата ва ҳолати объект.",
      mapSearchPlaceholder: "Ҷустуҷӯи истгоҳ…",
      mapStationPanel: "Шиносномаи истгоҳ",
      coordinates: "Координатаҳо",
      openStationAdmin: "Кушодани идоракунии истгоҳҳо",
    },
  analytics: {
    title: "Таҳлил",
    lead: "Нишондиҳандаҳо аз маълумоти ҷории платформа ҳисоб карда мешаванд — хатҳо, истгоҳҳо, огоҳиҳо ва хабарҳо.",
    stationsPerLine: "Истгоҳҳо аз рӯи хатҳо",
    totalStations: "истгоҳ",
    lineLengths: "Дарозии хатҳо",
    totalLength: "Дарозии умумӣ",
    km: "км",
    lengthFootnote:
      "Дарозӣ аз рӯи геометрияи трассаҳо ҳисоб шудааст (гаверсинус); то тасдиқи трассаҳои воқеӣ маълумот намунавӣ аст.",
    accessibilityTitle: "Муҳити бемонеа",
    alertsBySeverity: "Огоҳиҳо аз рӯи дараҷа",
    totalAlerts: "фаъол",
    stationStatuses: "Ҳолати истгоҳҳо",
    newsByMonth: "Нашри хабарҳо аз рӯи моҳҳо",
  },
  overviewTitle: "Шарҳи умумӣ",
  overviewLead: "Ҳолати ҷории шабака аз рӯи API-и оммавӣ.",
  countLines: "Хатҳо",
  countStations: "Истгоҳҳо",
  countAlerts: "Огоҳиҳои фаъол",
  countNews: "Хабарҳо",
  countAgents: "Агентҳои AI",
  loading: "Бор шуда истодааст…",
  loadError: "Маълумот бор нашуд",
  loadErrorHint: "Санҷед, ки backend дар http://localhost:8080 фаъол аст.",
  empty: "Сабт нест",
  total: "Ҳамагӣ",
  linesTitle: "Хатҳо",
  stationsTitle: "Истгоҳҳо",
  alertsTitle: "Огоҳиҳои фаъол",
  newsTitle: "Хабарҳо",
  auditTitle: "Журнали аудит",
  operations: {
    importsTitle: "Воридоти маълумот",
    importsLead: "Шабакаи GeoJSON-ро бор кунед ва натиҷаи ҳар як воридотро назорат намоед.",
    uploadTitle: "Боркунии GeoJSON",
    sourceName: "Номи манбаъ",
    sourceHint: "Номи файл ё системаи манбаъ",
    file: "Файли GeoJSON",
    chooseFile: "Файли GeoJSON-ро интихоб кунед",
    runImport: "Оғози воридот",
    importing: "Ворид шуда истодааст…",
    jobsTitle: "Таърихи воридот",
    status: "Ҳолат",
    source: "Манбаъ",
    counts: "Натиҷа",
    created: "Эҷод",
    updated: "Навсозӣ",
    failed: "Хато",
    started: "Оғоз",
    finished: "Анҷом",
    showErrors: "Нишон додани хатоҳо",
    hideErrors: "Пинҳон кардани хатоҳо",
    errorsTitle: "Хатоҳои воридот",
    noErrors: "Барои ин воридот хато сабт нашудааст",
    invalidFile: "Файли дурусти JSON/GeoJSON-ро интихоб кунед",
    importDone: "Воридот қабул карда шуд",
    calendarTitle: "Тақвими ҳаракат",
    calendarLead: "Рӯзҳои ид ва истисноҳои навъи рӯзи ҷадвалро идора кунед.",
    newException: "Иловаи истисно",
    editException: "Тағйири истисно",
    date: "Сана",
    dayType: "Навъи рӯз",
    weekday: "Рӯзи корӣ",
    weekend: "Рӯзи истироҳат",
    holiday: "Рӯзи ид",
    descriptionTg: "Тавсиф (тоҷикӣ)",
    descriptionRu: "Тавсиф (русӣ)",
    descriptionEn: "Тавсиф (англисӣ)",
    recurring: "Ҳар сол такрор шавад",
    featuresTitle: "Парчамҳои функсионалӣ",
    featuresLead: "Функсияҳои таҷрибавиро бе бознашр идора кунед.",
    flag: "Парчам",
    description: "Тавсиф",
    state: "Ҳолат",
    enabled: "Фаъол",
    disabled: "Хомӯш",
    enable: "Фаъол кардан",
    disable: "Хомӯш кардан",
    updatedAt: "Навсозӣ",
    updatedBy: "Навсозанда",
    toggle: "Иваз кардани ҳолат",
    requestsTitle: "Муроҷиатҳои шаҳрвандон",
    requestsLead: "Навбати муроҷиатҳоро бинед, иҷрокунанда таъин кунед ва ҷавобро нашр намоед.",
    filterAll: "Ҳамаи ҳолатҳо",
    requestTypes: {
      complaint: "Шикоят",
      suggestion: "Пешниҳод",
      incident: "Ҳодиса",
      question: "Савол",
      lost_item: "Ашёи гумшуда",
    },
    requestStatuses: {
      new: "Нав",
      in_progress: "Дар кор",
      awaiting_info: "Интизори маълумот",
      resolved: "Ҳал шуд",
      closed: "Пӯшида",
      reopened: "Аз нав кушода",
    },
    requestPriorities: { low: "Паст", normal: "Миёна", high: "Баланд" },
    subject: "Мавзуъ",
    message: "Матни муроҷиат",
    contact: "Тамос",
    assignedTo: "Иҷрокунанда",
    unassigned: "Таъин нашудааст",
    response: "Ҷавоб ба шаҳрванд",
    lineStation: "Хат / истгоҳ",
    responseDue: "Муҳлати ҷавоб",
    resolutionDue: "Муҳлати ҳал",
    slaBreached: "Муҳлат гузаштааст",
    openRequest: "Кушодани муроҷиат",
    requestDetails: "Тафсилоти муроҷиат",
    faresTitle: "Тарофаҳо ва роҳхатҳо",
    faresLead: "Нархҳо, категорияҳои мусофирон ва муҳлати амали маҳсулоти тарофавиро идора кунед.",
    newFare: "Иловаи тарофа",
    editFare: "Тағйири тарофа",
    price: "Нарх",
    currency: "Асъор",
    riderCategory: "Категорияи мусофир",
    fareCategories: { all: "Ҳама", adult: "Калонсол", child: "Кӯдак", student: "Донишҷӯ", senior: "Солхӯрда" },
    validityMinutes: "Муҳлати амал (дақиқа)",
    active: "Фаъол",
    inactive: "Хомӯш",
  },
  colCode: "Рамз",
  colName: "Ном",
  colColor: "Ранг",
  colStatus: "Ҳолат",
  colOrder: "Тартиб",
  colLines: "Хатҳо",
  colTransfer: "Гузариш",
  colAccessibility: "Дастрасӣ",
  colSeverity: "Дараҷа",
  colTargets: "Ҳадафҳо",
  colStartsAt: "Оғоз",
  colEndsAt: "Анҷом",
  colTitle: "Сарлавҳа",
  colPublishedAt: "Санаи нашр",
  colCover: "Муқова",
  colActionType: "Амал",
  colEntityType: "Навъи объект",
  colEntityCode: "Рамзи объект",
  colPerformedBy: "Иҷрокунанда",
  colCreatedAt: "Сана",
  yes: "Ҳа",
  no: "Не",
  none: "—",
  hasCover: "Ҳаст",
  noCover: "Нест",
  openLink: "Кушодан",
  colActions: "Амалҳо",
  actions: {
    create: "Илова кардан",
    edit: "Таҳрир",
    delete: "Нест кардан",
    publish: "Нашр",
    save: "Захира",
    cancel: "Бекор",
    close: "Пӯшидан",
    saving: "Захира шуда истодааст…",
  },
  entity: {
    line: "Хат",
    station: "Истгоҳ",
    alert: "Огоҳӣ",
    news: "Хабар",
  },
  form: {
    createTitle: "Илова кардан",
    editTitle: "Таҳрир",
    required: "ҳатмӣ",
    optional: "ихтиёрӣ",
    langTg: "Тоҷикӣ (tg)",
    langRu: "Русӣ (ru)",
    langEn: "Англисӣ (en)",
    fieldBody: "Матн",
    fieldSlug: "Slug",
    fieldCoverUrl: "URL-и муқова",
    fieldDescription: "Тавсиф",
    fieldCoordinates: "Координатаҳо",
    fieldLon: "Дарозӣ (lon)",
    fieldLat: "Арз (lat)",
    fieldPath: "Трасса (нуқтаҳо)",
    hintPath: "Ҳар сатр — «lon, lat». Ихтиёрӣ.",
    hintCoordinates: "EPSG:4326, тартиб [lon, lat].",
    hintCodeImmutable: "Рамз пас аз сохтан тағйир намеёбад.",
    fieldTargets: "Ҳадафҳо",
    targetType: "Навъ",
    targetCode: "Рамз",
    targetLine: "Хат",
    targetStation: "Истгоҳ",
    addTarget: "Илова кардани ҳадаф",
    removeTarget: "Нест кардан",
    draftHint: "Ҳамчун лоиҳа (draft) сохта мешавад; пас аз нашр дар рӯйхат пайдо мешавад.",
    errRequired: "Майдони ҳатмӣ",
    errI18nIncomplete: "Ҳар се забон (tg/ru/en) пур карда шаванд",
    errColorHex: "Формат бояд #RRGGBB бошад",
    errCoordinates: "Ду адади дуруст ворид кунед",
    errNumber: "Адади дуруст ворид кунед",
    errDateRange: "Анҷом бояд пас аз оғоз бошад",
    errUrl: "URL-и дуруст ворид кунед",
    fixErrors: "Хатоҳоро ислоҳ кунед",
  },
  confirmDelete: {
    title: "Нест кардан",
    text: "Дар ҳақиқат нест кардан мехоҳед",
    hint: "Ин амал сабтро аз намоиши оммавӣ пинҳон мекунад (soft-delete).",
  },
  toast: {
    created: "Сохта шуд",
    updated: "Навсозӣ шуд",
    deleted: "Нест карда шуд",
    published: "Нашр шуд",
    error: "Хатогӣ",
  },
  severity: {
    info: "Маълумот",
    warning: "Огоҳӣ",
    critical: "Ҳассос",
  },
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
  appTitle: "Консоль управления",
  appSubtitle: "Метро Душанбе",
  skipToContent: "Перейти к содержимому",
  languageSwitcher: "Язык",
  themeLabel: "Тема оформления",
  themeAuto: "Авто",
  themeLight: "Светлая",
  themeDark: "Тёмная",
  readOnlyBadge: "Только чтение",
  readOnlyHint: "Режим просмотра — редактирование в следующей итерации",
  nav: {
    overview: "Обзор",
    map: "Карта метро",
    analytics: "Аналитика",
    lines: "Линии",
    stations: "Станции",
    alerts: "Уведомления",
    news: "Новости",
    requests: "Обращения",
    fares: "Тарифы",
    imports: "Импорты",
    calendar: "Календарь",
    features: "Функции",
    agents: "AI-агенты",
    audit: "Аудит",
  },
  navGroups: {
    network: "Сеть",
    content: "Контент",
    system: "Система",
  },
  sidebarFootnote: "Демо-контур · v0.1 · Министерство транспорта РТ",
  topbar: {
    searchLabel: "Быстрый переход",
    searchPlaceholder: "Раздел консоли…",
    demoBadge: "Демо-контур",
    demoHint: "Данные сети демонстрационные; реальные загружаются конвейером импорта (ТЗ, раздел 13)",
    userName: "Администратор",
    userRole: "Оператор платформы",
    logout: "Выйти",
  },
  login: {
    welcome1: "Добро пожаловать",
    welcome2Prefix: "в ",
    welcome2Accent: "Метро Душанбе",
    heroLead:
      "Единая цифровая платформа для управления метрополитеном Душанбе и предоставления сервисов для пассажиров.",
    badgeSecure: "Безопасно",
    badgeReliable: "Надёжно",
    badgeConvenient: "Удобно",
    copyright: "© 2026 Метро Душанбе. Все права защищены.",
    title: "Вход в систему",
    subtitle: "Введите свои данные для входа",
    usernameLabel: "Имя пользователя или email",
    usernamePlaceholder: "Введите ваш email или логин",
    passwordLabel: "Пароль",
    passwordPlaceholder: "Введите ваш пароль",
    remember: "Запомнить меня",
    forgot: "Забыли пароль?",
    forgotNote:
      "Пароль восстанавливает администратор системы — обратитесь в службу поддержки.",
    submit: "Войти",
    submitting: "Вход…",
    or: "или",
    ssoButton: "Войти через SSO (Государственные системы)",
    ssoNote:
      "Государственный SSO подключается на следующей фазе (Keycloak, ТЗ §9.2). Обратитесь к администратору системы.",
    noAccount: "Нет аккаунта?",
    contactAdmin: "Свяжитесь с администратором",
    footerPrivacy: "Политика конфиденциальности",
    footerTerms: "Условия использования",
    footerSupport: "Поддержка",
    errInvalid: "Неверный логин или пароль",
    errRequired: "Введите логин и пароль",
    showPassword: "Показать пароль",
    hidePassword: "Скрыть пароль",
  },
  dash: {
    greetingMorning: "Доброе утро!",
    greetingDay: "Добрый день!",
    greetingEvening: "Добрый вечер!",
    greetingNight: "Доброй ночи!",
    lead: "Оперативная сводка национальной платформы «Метро Душанбе».",
    kpiLinesSub: "{active} действует · {planned} в проекте",
    kpiStationsSub: "пересадочных: {transfer}",
    kpiAlertsSub: "критичных: {critical} · предупреждений: {warning}",
    kpiNewsSub: "последняя:",
    kpiAgentsSub: "статус:",
    networkTitle: "Схема сети",
    sourceApi: "живые данные",
    sourceDemo: "демо-данные",
    legendStations: "ст.",
    legendTransfer: "Пересадки",
    viewAll: "Все",
    alertsEmpty: "Активных опубликованных уведомлений нет",
    quickTitle: "Быстрые действия",
    quickLine: "Добавить линию",
    quickStation: "Добавить станцию",
    quickAlert: "Создать уведомление",
    quickNews: "Написать новость",
    aiTitle: "AI-брифинг",
    aiPosture: "Готовность",
    auditTitle: "Последние изменения",
    auditEmpty: "Записей аудита пока нет",
    systemTitle: "Состояние систем",
    healthBackend: "Backend API",
    healthUp: "работает",
    healthDown: "недоступен",
    healthData: "Данные сети",
    healthAlerts: "Событийный фон",
    healthCalm: "спокойно",
      systemFootnote:
        "Статусы рассчитываются при загрузке страницы по ответам backend (actuator/health) и публичного API.",
      phaseBadge: "Проектируемая сеть · презентационный контур",
      heroTitle: "Цифровой центр проекта метро Душанбе",
      heroLead: "География, данные сети, обращения жителей и готовность интеграций — на одном доказуемом экране.",
      openPublicPortal: "Открыть публичный портал",
      openCityMap: "Открыть карту Душанбе",
      countryTitle: "Карта Таджикистана",
      countrySubtitle: "Положение проекта в масштабе страны",
      cityTitle: "Карта города Душанбе",
      citySubtitle: "Демонстрационные линии сети в географических координатах",
      countrySource: "Контур: Natural Earth · WGS84",
      capitalLabel: "Душанбе · столица",
      kpiDataReady: "Готовность геоданных",
      kpiDataReadySub: "координаты и связи заполнены",
      kpiRequests: "Очередь обращений",
      kpiRequestsSub: "открытых и в работе",
      kpiImports: "Импорты данных",
      kpiImportsSub: "последнее задание: {status}",
      readinessTitle: "Готовность платформы",
      readinessLead: "Только статусы, рассчитанные из данных текущего контура.",
      readinessCatalog: "Каталог линий и станций",
      readinessGeo: "GeoJSON и координаты",
      readinessContent: "Новости и публичные уведомления",
      readinessFeedback: "Обращения жителей",
      ready: "Готово",
      demo: "Демо",
      needsIntegration: "Нужна интеграция",
      externalTitle: "Внешние системы",
      externalLead: "Эти модули включаются после появления промышленных источников.",
      externalAfc: "AFC / пассажиропоток",
      externalRealtime: "Диспетчеризация / движение поездов",
      externalPayments: "Эквайринг и билеты",
      notConnected: "не подключено",
      requestQueueTitle: "Последние обращения",
      requestQueueEmpty: "Открытых обращений нет",
      mapPageTitle: "Карта метро Душанбе",
      mapPageLead: "Интерактивная схема демонстрационной сети с поиском станции, координатами и статусом объекта.",
      mapSearchPlaceholder: "Найти станцию…",
      mapStationPanel: "Паспорт станции",
      coordinates: "Координаты",
      openStationAdmin: "Открыть управление станциями",
    },
  analytics: {
    title: "Аналитика",
    lead: "Показатели считаются из текущих данных платформы — линий, станций, уведомлений и новостей.",
    stationsPerLine: "Станции по линиям",
    totalStations: "станций",
    lineLengths: "Протяжённость линий",
    totalLength: "Общая протяжённость",
    km: "км",
    lengthFootnote:
      "Длина вычислена по геометрии трасс (гаверсинус); до утверждения реальных трасс данные демонстрационные.",
    accessibilityTitle: "Безбарьерная среда",
    alertsBySeverity: "Уведомления по уровню",
    totalAlerts: "активных",
    stationStatuses: "Статусы станций",
    newsByMonth: "Публикации новостей по месяцам",
  },
  overviewTitle: "Обзор",
  overviewLead: "Текущее состояние сети по данным публичного API.",
  countLines: "Линии",
  countStations: "Станции",
  countAlerts: "Активные уведомления",
  countNews: "Новости",
  countAgents: "AI-агенты",
  loading: "Загрузка…",
  loadError: "Не удалось загрузить данные",
  loadErrorHint: "Проверьте, что backend запущен на http://localhost:8080.",
  empty: "Нет записей",
  total: "Всего",
  linesTitle: "Линии",
  stationsTitle: "Станции",
  alertsTitle: "Активные уведомления",
  newsTitle: "Новости",
  auditTitle: "Журнал аудита",
  operations: {
    importsTitle: "Импорт данных",
    importsLead: "Загрузите GeoJSON сети и контролируйте результат каждого задания импорта.",
    uploadTitle: "Загрузка GeoJSON",
    sourceName: "Название источника",
    sourceHint: "Имя файла или исходной системы",
    file: "Файл GeoJSON",
    chooseFile: "Выберите файл GeoJSON",
    runImport: "Запустить импорт",
    importing: "Выполняется импорт…",
    jobsTitle: "История импортов",
    status: "Статус",
    source: "Источник",
    counts: "Результат",
    created: "Создано",
    updated: "Обновлено",
    failed: "Ошибки",
    started: "Начало",
    finished: "Завершение",
    showErrors: "Показать ошибки",
    hideErrors: "Скрыть ошибки",
    errorsTitle: "Ошибки импорта",
    noErrors: "Для этого импорта ошибки не зарегистрированы",
    invalidFile: "Выберите корректный файл JSON/GeoJSON",
    importDone: "Импорт принят",
    calendarTitle: "Календарь движения",
    calendarLead: "Управляйте праздниками и исключениями типа дня расписания.",
    newException: "Добавить исключение",
    editException: "Изменить исключение",
    date: "Дата",
    dayType: "Тип дня",
    weekday: "Рабочий день",
    weekend: "Выходной",
    holiday: "Праздник",
    descriptionTg: "Описание (таджикский)",
    descriptionRu: "Описание (русский)",
    descriptionEn: "Описание (английский)",
    recurring: "Повторять ежегодно",
    featuresTitle: "Функциональные флаги",
    featuresLead: "Управляйте экспериментальными функциями без повторного развёртывания.",
    flag: "Флаг",
    description: "Описание",
    state: "Состояние",
    enabled: "Включён",
    disabled: "Выключен",
    enable: "Включить",
    disable: "Выключить",
    updatedAt: "Обновлён",
    updatedBy: "Кем обновлён",
    toggle: "Переключить состояние",
    requestsTitle: "Обращения граждан",
    requestsLead: "Обрабатывайте очередь обращений, назначайте исполнителей и публикуйте ответы.",
    filterAll: "Все статусы",
    requestTypes: {
      complaint: "Жалоба",
      suggestion: "Предложение",
      incident: "Инцидент",
      question: "Вопрос",
      lost_item: "Потерянная вещь",
    },
    requestStatuses: {
      new: "Новое",
      in_progress: "В работе",
      awaiting_info: "Ожидает информации",
      resolved: "Решено",
      closed: "Закрыто",
      reopened: "Открыто повторно",
    },
    requestPriorities: { low: "Низкий", normal: "Обычный", high: "Высокий" },
    subject: "Тема",
    message: "Текст обращения",
    contact: "Контакт",
    assignedTo: "Исполнитель",
    unassigned: "Не назначен",
    response: "Ответ гражданину",
    lineStation: "Линия / станция",
    responseDue: "Срок ответа",
    resolutionDue: "Срок решения",
    slaBreached: "Срок нарушен",
    openRequest: "Открыть обращение",
    requestDetails: "Детали обращения",
    faresTitle: "Тарифы и проездные",
    faresLead: "Управляйте ценами, категориями пассажиров и сроками действия тарифных продуктов.",
    newFare: "Добавить тариф",
    editFare: "Изменить тариф",
    price: "Цена",
    currency: "Валюта",
    riderCategory: "Категория пассажира",
    fareCategories: { all: "Все", adult: "Взрослый", child: "Ребёнок", student: "Студент", senior: "Пенсионер" },
    validityMinutes: "Срок действия (минуты)",
    active: "Активен",
    inactive: "Неактивен",
  },
  colCode: "Код",
  colName: "Название",
  colColor: "Цвет",
  colStatus: "Статус",
  colOrder: "Порядок",
  colLines: "Линии",
  colTransfer: "Пересадка",
  colAccessibility: "Доступность",
  colSeverity: "Уровень",
  colTargets: "Цели",
  colStartsAt: "Начало",
  colEndsAt: "Окончание",
  colTitle: "Заголовок",
  colPublishedAt: "Дата публикации",
  colCover: "Обложка",
  colActionType: "Действие",
  colEntityType: "Тип объекта",
  colEntityCode: "Код объекта",
  colPerformedBy: "Исполнитель",
  colCreatedAt: "Дата",
  yes: "Да",
  no: "Нет",
  none: "—",
  hasCover: "Есть",
  noCover: "Нет",
  openLink: "Открыть",
  colActions: "Действия",
  actions: {
    create: "Добавить",
    edit: "Изменить",
    delete: "Удалить",
    publish: "Опубликовать",
    save: "Сохранить",
    cancel: "Отмена",
    close: "Закрыть",
    saving: "Сохранение…",
  },
  entity: {
    line: "Линия",
    station: "Станция",
    alert: "Уведомление",
    news: "Новость",
  },
  form: {
    createTitle: "Добавить",
    editTitle: "Изменить",
    required: "обязательно",
    optional: "необязательно",
    langTg: "Таджикский (tg)",
    langRu: "Русский (ru)",
    langEn: "Английский (en)",
    fieldBody: "Текст",
    fieldSlug: "Slug",
    fieldCoverUrl: "URL обложки",
    fieldDescription: "Описание",
    fieldCoordinates: "Координаты",
    fieldLon: "Долгота (lon)",
    fieldLat: "Широта (lat)",
    fieldPath: "Трасса (точки)",
    hintPath: "По строке на точку в формате «lon, lat». Необязательно.",
    hintCoordinates: "EPSG:4326, порядок [lon, lat].",
    hintCodeImmutable: "Код неизменен после создания.",
    fieldTargets: "Цели",
    targetType: "Тип",
    targetCode: "Код",
    targetLine: "Линия",
    targetStation: "Станция",
    addTarget: "Добавить цель",
    removeTarget: "Удалить",
    draftHint: "Создаётся как черновик (draft); появится в списке после публикации.",
    errRequired: "Обязательное поле",
    errI18nIncomplete: "Заполните все три языка (tg/ru/en)",
    errColorHex: "Формат должен быть #RRGGBB",
    errCoordinates: "Введите два корректных числа",
    errNumber: "Введите корректное число",
    errDateRange: "Окончание должно быть позже начала",
    errUrl: "Введите корректный URL",
    fixErrors: "Исправьте ошибки в форме",
  },
  confirmDelete: {
    title: "Удаление",
    text: "Действительно удалить",
    hint: "Действие скрывает запись из публичной выдачи (soft-delete).",
  },
  toast: {
    created: "Создано",
    updated: "Обновлено",
    deleted: "Удалено",
    published: "Опубликовано",
    error: "Ошибка",
  },
  severity: {
    info: "Информация",
    warning: "Предупреждение",
    critical: "Критично",
  },
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
  appTitle: "Operations console",
  appSubtitle: "Dushanbe Metro",
  skipToContent: "Skip to content",
  languageSwitcher: "Language",
  themeLabel: "Theme",
  themeAuto: "Auto",
  themeLight: "Light",
  themeDark: "Dark",
  readOnlyBadge: "Read-only",
  readOnlyHint: "View mode — editing arrives in the next iteration",
  nav: {
    overview: "Overview",
    map: "Metro map",
    analytics: "Analytics",
    lines: "Lines",
    stations: "Stations",
    alerts: "Alerts",
    news: "News",
    requests: "Requests",
    fares: "Fares",
    imports: "Imports",
    calendar: "Calendar",
    features: "Features",
    agents: "AI agents",
    audit: "Audit",
  },
  navGroups: {
    network: "Network",
    content: "Content",
    system: "System",
  },
  sidebarFootnote: "Demo environment · v0.1 · Ministry of Transport of Tajikistan",
  topbar: {
    searchLabel: "Quick navigation",
    searchPlaceholder: "Console section…",
    demoBadge: "Demo mode",
    demoHint: "Network data is illustrative; real data arrives via the import pipeline (ToR, section 13)",
    userName: "Administrator",
    userRole: "Platform operator",
    logout: "Sign out",
  },
  login: {
    welcome1: "Welcome",
    welcome2Prefix: "to ",
    welcome2Accent: "Dushanbe Metro",
    heroLead:
      "A single digital platform for operating the Dushanbe metro and serving passengers.",
    badgeSecure: "Secure",
    badgeReliable: "Reliable",
    badgeConvenient: "Convenient",
    copyright: "© 2026 Dushanbe Metro. All rights reserved.",
    title: "Sign in",
    subtitle: "Enter your credentials to continue",
    usernameLabel: "Username or email",
    usernamePlaceholder: "Enter your email or username",
    passwordLabel: "Password",
    passwordPlaceholder: "Enter your password",
    remember: "Remember me",
    forgot: "Forgot password?",
    forgotNote:
      "Passwords are reset by the system administrator — contact support.",
    submit: "Sign in",
    submitting: "Signing in…",
    or: "or",
    ssoButton: "Sign in with SSO (Government systems)",
    ssoNote:
      "Government SSO arrives in the next phase (Keycloak, ToR §9.2). Contact your system administrator.",
    noAccount: "No account?",
    contactAdmin: "Contact the administrator",
    footerPrivacy: "Privacy policy",
    footerTerms: "Terms of use",
    footerSupport: "Support",
    errInvalid: "Invalid username or password",
    errRequired: "Enter username and password",
    showPassword: "Show password",
    hidePassword: "Hide password",
  },
  dash: {
    greetingMorning: "Good morning!",
    greetingDay: "Good afternoon!",
    greetingEvening: "Good evening!",
    greetingNight: "Good night!",
    lead: "Operational snapshot of the Dushanbe Metro national platform.",
    kpiLinesSub: "{active} active · {planned} planned",
    kpiStationsSub: "transfer hubs: {transfer}",
    kpiAlertsSub: "critical: {critical} · warnings: {warning}",
    kpiNewsSub: "latest:",
    kpiAgentsSub: "posture:",
    networkTitle: "Network diagram",
    sourceApi: "live data",
    sourceDemo: "demo data",
    legendStations: "st.",
    legendTransfer: "Transfers",
    viewAll: "View all",
    alertsEmpty: "No published alerts are currently active",
    quickTitle: "Quick actions",
    quickLine: "Add line",
    quickStation: "Add station",
    quickAlert: "Create alert",
    quickNews: "Write news",
    aiTitle: "AI briefing",
    aiPosture: "Posture",
    auditTitle: "Recent changes",
    auditEmpty: "No audit records yet",
    systemTitle: "System status",
    healthBackend: "Backend API",
    healthUp: "operational",
    healthDown: "unavailable",
    healthData: "Network data",
    healthAlerts: "Alert level",
    healthCalm: "calm",
      systemFootnote:
        "Statuses are computed at page load from backend responses (actuator/health) and the public API.",
      phaseBadge: "Planned network · showcase environment",
      heroTitle: "Dushanbe Metro project digital command centre",
      heroLead: "Geography, network data, citizen requests and integration readiness on one evidence-based screen.",
      openPublicPortal: "Open public portal",
      openCityMap: "Open Dushanbe map",
      countryTitle: "Map of Tajikistan",
      countrySubtitle: "Project location at national scale",
      cityTitle: "Map of Dushanbe",
      citySubtitle: "Illustrative network lines in geographic coordinates",
      countrySource: "Outline: Natural Earth · WGS84",
      capitalLabel: "Dushanbe · capital",
      kpiDataReady: "Geodata readiness",
      kpiDataReadySub: "coordinates and relations complete",
      kpiRequests: "Request queue",
      kpiRequestsSub: "open and in progress",
      kpiImports: "Data imports",
      kpiImportsSub: "latest job: {status}",
      readinessTitle: "Platform readiness",
      readinessLead: "Only statuses computed from data in the current environment.",
      readinessCatalog: "Lines and stations catalogue",
      readinessGeo: "GeoJSON and coordinates",
      readinessContent: "News and public alerts",
      readinessFeedback: "Citizen requests",
      ready: "Ready",
      demo: "Demo",
      needsIntegration: "Integration required",
      externalTitle: "External systems",
      externalLead: "These modules are enabled when production data sources become available.",
      externalAfc: "AFC / ridership",
      externalRealtime: "Dispatch / train movement",
      externalPayments: "Acquiring and tickets",
      notConnected: "not connected",
      requestQueueTitle: "Latest requests",
      requestQueueEmpty: "No open requests",
      mapPageTitle: "Dushanbe metro map",
      mapPageLead: "Interactive view of the illustrative network with station search, coordinates and object status.",
      mapSearchPlaceholder: "Find a station…",
      mapStationPanel: "Station profile",
      coordinates: "Coordinates",
      openStationAdmin: "Open station management",
    },
  analytics: {
    title: "Analytics",
    lead: "Metrics are computed from current platform data — lines, stations, alerts and news.",
    stationsPerLine: "Stations by line",
    totalStations: "stations",
    lineLengths: "Line lengths",
    totalLength: "Total length",
    km: "km",
    lengthFootnote:
      "Length is computed from route geometry (haversine); data is illustrative until real routes are approved.",
    accessibilityTitle: "Accessibility",
    alertsBySeverity: "Alerts by severity",
    totalAlerts: "active",
    stationStatuses: "Station statuses",
    newsByMonth: "News published by month",
  },
  overviewTitle: "Overview",
  overviewLead: "Current network status from the public API.",
  countLines: "Lines",
  countStations: "Stations",
  countAlerts: "Active alerts",
  countNews: "News",
  countAgents: "AI agents",
  loading: "Loading…",
  loadError: "Failed to load data",
  loadErrorHint: "Make sure the backend is running at http://localhost:8080.",
  empty: "No records",
  total: "Total",
  linesTitle: "Lines",
  stationsTitle: "Stations",
  alertsTitle: "Active alerts",
  newsTitle: "News",
  auditTitle: "Audit log",
  operations: {
    importsTitle: "Data imports",
    importsLead: "Upload network GeoJSON and monitor the outcome of every import job.",
    uploadTitle: "Upload GeoJSON",
    sourceName: "Source name",
    sourceHint: "File name or source system",
    file: "GeoJSON file",
    chooseFile: "Choose a GeoJSON file",
    runImport: "Run import",
    importing: "Importing…",
    jobsTitle: "Import history",
    status: "Status",
    source: "Source",
    counts: "Result",
    created: "Created",
    updated: "Updated",
    failed: "Failed",
    started: "Started",
    finished: "Finished",
    showErrors: "Show errors",
    hideErrors: "Hide errors",
    errorsTitle: "Import errors",
    noErrors: "No errors were recorded for this import",
    invalidFile: "Choose a valid JSON/GeoJSON file",
    importDone: "Import accepted",
    calendarTitle: "Service calendar",
    calendarLead: "Manage holidays and schedule day-type exceptions.",
    newException: "Add exception",
    editException: "Edit exception",
    date: "Date",
    dayType: "Day type",
    weekday: "Weekday",
    weekend: "Weekend",
    holiday: "Holiday",
    descriptionTg: "Description (Tajik)",
    descriptionRu: "Description (Russian)",
    descriptionEn: "Description (English)",
    recurring: "Repeat every year",
    featuresTitle: "Feature flags",
    featuresLead: "Control experimental capabilities without redeploying.",
    flag: "Flag",
    description: "Description",
    state: "State",
    enabled: "Enabled",
    disabled: "Disabled",
    enable: "Enable",
    disable: "Disable",
    updatedAt: "Updated",
    updatedBy: "Updated by",
    toggle: "Toggle state",
    requestsTitle: "Citizen requests",
    requestsLead: "Process the request queue, assign operators, and publish responses.",
    filterAll: "All statuses",
    requestTypes: {
      complaint: "Complaint",
      suggestion: "Suggestion",
      incident: "Incident",
      question: "Question",
      lost_item: "Lost item",
    },
    requestStatuses: {
      new: "New",
      in_progress: "In progress",
      awaiting_info: "Awaiting information",
      resolved: "Resolved",
      closed: "Closed",
      reopened: "Reopened",
    },
    requestPriorities: { low: "Low", normal: "Normal", high: "High" },
    subject: "Subject",
    message: "Request message",
    contact: "Contact",
    assignedTo: "Assigned to",
    unassigned: "Unassigned",
    response: "Response to citizen",
    lineStation: "Line / station",
    responseDue: "Response due",
    resolutionDue: "Resolution due",
    slaBreached: "SLA breached",
    openRequest: "Open request",
    requestDetails: "Request details",
    faresTitle: "Fares and passes",
    faresLead: "Manage prices, rider categories, and fare product validity.",
    newFare: "Add fare",
    editFare: "Edit fare",
    price: "Price",
    currency: "Currency",
    riderCategory: "Rider category",
    fareCategories: { all: "All riders", adult: "Adult", child: "Child", student: "Student", senior: "Senior" },
    validityMinutes: "Validity (minutes)",
    active: "Active",
    inactive: "Inactive",
  },
  colCode: "Code",
  colName: "Name",
  colColor: "Color",
  colStatus: "Status",
  colOrder: "Order",
  colLines: "Lines",
  colTransfer: "Transfer",
  colAccessibility: "Accessibility",
  colSeverity: "Severity",
  colTargets: "Targets",
  colStartsAt: "Starts",
  colEndsAt: "Ends",
  colTitle: "Title",
  colPublishedAt: "Published",
  colCover: "Cover",
  colActionType: "Action",
  colEntityType: "Entity type",
  colEntityCode: "Entity code",
  colPerformedBy: "Performed by",
  colCreatedAt: "Date",
  yes: "Yes",
  no: "No",
  none: "—",
  hasCover: "Yes",
  noCover: "No",
  openLink: "Open",
  colActions: "Actions",
  actions: {
    create: "Add",
    edit: "Edit",
    delete: "Delete",
    publish: "Publish",
    save: "Save",
    cancel: "Cancel",
    close: "Close",
    saving: "Saving…",
  },
  entity: {
    line: "Line",
    station: "Station",
    alert: "Alert",
    news: "News item",
  },
  form: {
    createTitle: "Add",
    editTitle: "Edit",
    required: "required",
    optional: "optional",
    langTg: "Tajik (tg)",
    langRu: "Russian (ru)",
    langEn: "English (en)",
    fieldBody: "Body",
    fieldSlug: "Slug",
    fieldCoverUrl: "Cover URL",
    fieldDescription: "Description",
    fieldCoordinates: "Coordinates",
    fieldLon: "Longitude (lon)",
    fieldLat: "Latitude (lat)",
    fieldPath: "Path (points)",
    hintPath: "One \"lon, lat\" per line. Optional.",
    hintCoordinates: "EPSG:4326, order [lon, lat].",
    hintCodeImmutable: "Code cannot change after creation.",
    fieldTargets: "Targets",
    targetType: "Type",
    targetCode: "Code",
    targetLine: "Line",
    targetStation: "Station",
    addTarget: "Add target",
    removeTarget: "Remove",
    draftHint: "Created as a draft; it appears in the list once published.",
    errRequired: "Required field",
    errI18nIncomplete: "Fill all three languages (tg/ru/en)",
    errColorHex: "Format must be #RRGGBB",
    errCoordinates: "Enter two valid numbers",
    errNumber: "Enter a valid number",
    errDateRange: "End must be after start",
    errUrl: "Enter a valid URL",
    fixErrors: "Fix the errors in the form",
  },
  confirmDelete: {
    title: "Delete",
    text: "Really delete",
    hint: "This hides the record from public output (soft-delete).",
  },
  toast: {
    created: "Created",
    updated: "Updated",
    deleted: "Deleted",
    published: "Published",
    error: "Error",
  },
  severity: {
    info: "Info",
    warning: "Warning",
    critical: "Critical",
  },
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

/**
 * Подпись бейджа линии по текущему языку: «Л1»/«Л2» (tg, ru) или «L1» (en).
 * Код линии стабилен и всегда начинается с латинской L (dev-conventions.md, §4).
 */
export function lineBadgeLabel(code: string, lang: Lang): string {
  const prefix = lang === "en" ? "L" : "Л";
  return code.replace(/^L/i, prefix);
}

/**
 * Форматирование ISO-даты под выбранный язык (Intl, локальный часовой пояс).
 * Возвращает пустую строку для null/невалидной даты.
 */
const DUSHANBE_TIME_ZONE = "Asia/Dushanbe";

function dushanbeDateParts(date: Date): Record<string, string> {
  return Object.fromEntries(
    new Intl.DateTimeFormat("en-GB", {
      timeZone: DUSHANBE_TIME_ZONE,
      weekday: "short",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      hourCycle: "h23",
    })
      .formatToParts(date)
      .filter((part) => part.type !== "literal")
      .map((part) => [part.type, part.value]),
  );
}

export function formatDateTime(iso: string | null | undefined, lang: Lang): string {
  if (!iso) {
    return "";
  }
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) {
    return "";
  }
  const parts = dushanbeDateParts(d);
  const separator = lang === "en" ? "/" : ".";
  return `${parts.day}${separator}${parts.month}${separator}${parts.year}, ${parts.hour}:${parts.minute}`;
}

const WEEKDAY_LABELS: Record<Lang, Record<string, string>> = {
  tg: {
    Mon: "душанбе",
    Tue: "сешанбе",
    Wed: "чоршанбе",
    Thu: "панҷшанбе",
    Fri: "ҷумъа",
    Sat: "шанбе",
    Sun: "якшанбе",
  },
  ru: {
    Mon: "понедельник",
    Tue: "вторник",
    Wed: "среда",
    Thu: "четверг",
    Fri: "пятница",
    Sat: "суббота",
    Sun: "воскресенье",
  },
  en: {
    Mon: "Monday",
    Tue: "Tuesday",
    Wed: "Wednesday",
    Thu: "Thursday",
    Fri: "Friday",
    Sat: "Saturday",
    Sun: "Sunday",
  },
};

export function formatConsoleDate(date: Date, lang: Lang): string {
  const parts = dushanbeDateParts(date);
  const separator = lang === "en" ? "/" : ".";
  const weekday = WEEKDAY_LABELS[lang][parts.weekday] ?? parts.weekday;
  return `${weekday}, ${parts.day}${separator}${parts.month}${separator}${parts.year}`;
}
