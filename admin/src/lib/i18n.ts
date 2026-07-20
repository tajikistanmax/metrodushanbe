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
  BlocklistSubjectType,
  CitizenRequestPriority,
  CitizenRequestStatus,
  CitizenRequestType,
  FareRiderCategory,
  I18nName,
  ImportErrorSeverity,
  ImportFormat,
  ImportJobType,
  ImportKind,
  IncidentCategory,
  IncidentSeverity,
  IncidentStatus,
  LineStatus,
  NotificationChannel,
  NotificationDeliveryStatus,
  NotificationStatus,
  NotificationTargetType,
  NotificationType,
  PaymentKind,
  PaymentStatus,
  StationStatus,
  TicketKind,
  TicketStatus,
  WebhookDeliveryStatus,
  WebhookEventType,
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
    incidents: string;
    fares: string;
    notifications: string;
    tickets: string;
    webhooks: string;
    imports: string;
    calendar: string;
    features: string;
    agents: string;
    users: string;
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
    logout: string;
  };

  /** Названия ролей операторов (RBAC). Ключи совпадают с кодами backend. */
  roles: {
    viewer: string;
    operator: string;
    editor: string;
    superadmin: string;
  };

  /** Раздел операционного учёта инцидентов. */
  incidents: {
    title: string;
    lead: string;
    createTitle: string;
    editTitle: string;
    colCode: string;
    colTitle: string;
    colSeverity: string;
    colStatus: string;
    colWhere: string;
    colAssignee: string;
    colOccurred: string;
    fieldCategory: string;
    fieldSeverity: string;
    fieldTitle: string;
    fieldDescription: string;
    fieldLine: string;
    fieldStation: string;
    fieldAssignee: string;
    fieldAssigneeHint: string;
    fieldOccurredAt: string;
    fieldResolution: string;
    fieldResolutionHint: string;
    unassigned: string;
    networkWide: string;
    filterAll: string;
    transitionTitle: string;
    reportedBy: string;
    statTodayLabel: string;
    statOpenLabel: string;
    categories: Record<IncidentCategory, string>;
    severities: Record<IncidentSeverity, string>;
    statuses: Record<IncidentStatus, string>;
  };

  /** Раздел рассылок и шаблонов (NTF-01…06). */
  notifications: {
    title: string;
    lead: string;
    tabMessages: string;
    tabProblems: string;
    tabTemplates: string;
    /** Пометка про имитацию внешних каналов — обязана быть видна. */
    simulatedNotice: string;
    simulatedBadge: string;
    simulatedHint: string;
    colCode: string;
    colTitle: string;
    colType: string;
    colChannels: string;
    colStatus: string;
    colTargets: string;
    colScheduled: string;
    colSent: string;
    filterAll: string;
    createTitle: string;
    editTitle: string;
    fieldCode: string;
    fieldCodeHint: string;
    fieldTemplate: string;
    fieldTemplateHint: string;
    templateNone: string;
    fieldType: string;
    fieldTitleText: string;
    fieldBody: string;
    fieldChannels: string;
    fieldChannelsHint: string;
    fieldTargets: string;
    fieldTargetsHint: string;
    fieldTargetType: string;
    fieldTargetCode: string;
    addTarget: string;
    removeTarget: string;
    networkWide: string;
    fieldScheduledAt: string;
    fieldScheduledAtHint: string;
    send: string;
    sendTitle: string;
    sendWarning: string;
    sendDone: string;
    statusTitle: string;
    changeStatus: string;
    frozenTitle: string;
    frozenText: string;
    deliveriesTitle: string;
    showDeliveries: string;
    hideDeliveries: string;
    problemsTitle: string;
    problemsLead: string;
    problemsEmpty: string;
    colChannel: string;
    colRecipient: string;
    colAttempts: string;
    colError: string;
    colDeliveredAt: string;
    retry: string;
    retryHint: string;
    retryDone: string;
    noDeliveries: string;
    templatesTitle: string;
    templatesLead: string;
    templateCreate: string;
    templateEdit: string;
    colName: string;
    fieldName: string;
    fieldNameHint: string;
    fieldActive: string;
    templatesReadOnly: string;
    types: Record<NotificationType, string>;
    statuses: Record<NotificationStatus, string>;
    channels: Record<NotificationChannel, string>;
    targetTypes: Record<NotificationTargetType, string>;
    deliveryStatuses: Record<NotificationDeliveryStatus, string>;
  };

  /** Раздел билетов, платежей и чёрного списка (TKT-01…06). */
  tickets: {
    title: string;
    lead: string;
    /** Пометка про имитацию платежей — обязана быть видна. */
    demoNotice: string;
    demoBadge: string;
    demoHint: string;
    tabTickets: string;
    tabPayments: string;
    tabBlocklist: string;
    filterAll: string;
    colCode: string;
    colFare: string;
    colKind: string;
    colRider: string;
    colStatus: string;
    colPrice: string;
    colValidity: string;
    colBalance: string;
    validityOpen: string;
    noToken: string;
    refund: string;
    refundTitle: string;
    refundLead: string;
    fieldRefundReason: string;
    fieldRefundReasonHint: string;
    refundDone: string;
    paymentsTitle: string;
    paymentsLead: string;
    colPaymentKind: string;
    colAmount: string;
    colProvider: string;
    colTicket: string;
    colFailure: string;
    colCreated: string;
    noTicket: string;
    blocklistTitle: string;
    blocklistLead: string;
    blocklistAdd: string;
    blocklistRemove: string;
    blocklistRemoveHint: string;
    colSubjectType: string;
    colSubject: string;
    colReason: string;
    colBlockedBy: string;
    fieldSubjectType: string;
    fieldSubjectValue: string;
    fieldSubjectValueHint: string;
    fieldBlockReason: string;
    fieldBlockReasonHint: string;
    tokenHashNotice: string;
    blockDone: string;
    unblockDone: string;
    kinds: Record<TicketKind, string>;
    statuses: Record<TicketStatus, string>;
    paymentKinds: Record<PaymentKind, string>;
    paymentStatuses: Record<PaymentStatus, string>;
    subjectTypes: Record<BlocklistSubjectType, string>;
  };

  /** Раздел интеграций: подписчики вебхуков и очередь доставок (ADM-06, U-OPS-04). */
  webhooks: {
    title: string;
    lead: string;
    tabSubscribers: string;
    tabDeliveries: string;
    subscribersTitle: string;
    subscribersLead: string;
    colName: string;
    colUrl: string;
    colEvents: string;
    colState: string;
    colRateLimit: string;
    colFingerprint: string;
    perMinute: string;
    createTitle: string;
    editTitle: string;
    fieldCode: string;
    fieldCodeHint: string;
    fieldName: string;
    fieldUrl: string;
    fieldUrlHint: string;
    fieldEvents: string;
    fieldEventsHint: string;
    fieldActive: string;
    fieldRateLimit: string;
    fieldRateLimitHint: string;
    rotate: string;
    rotateTitle: string;
    rotateWarning: string;
    rotateConfirm: string;
    /** Экран «секрет виден один раз» — ключевое место раздела. */
    secretTitle: string;
    secretOnceWarning: string;
    secretLead: string;
    secretLabel: string;
    secretCopy: string;
    secretCopied: string;
    secretCopyFailed: string;
    secretAck: string;
    fingerprintHint: string;
    superadminOnly: string;
    deliveriesTitle: string;
    deliveriesLead: string;
    dlqTitle: string;
    dlqLead: string;
    dlqEmpty: string;
    filterAttention: string;
    colEvent: string;
    colSubject: string;
    colSubscriber: string;
    colAttempts: string;
    colResponse: string;
    colError: string;
    colNextAttempt: string;
    colTrace: string;
    retryDue: string;
    retry: string;
    retryDone: string;
    notRetryable: string;
    eventTypes: Record<WebhookEventType, string>;
    deliveryStatuses: Record<WebhookDeliveryStatus, string>;
  };

  /**
   * Раздел AI-агентов: реестр моделей и сводка готовности контура.
   *
   * Локализуется только обвязка UI и коды состояний. Роль, сигналы,
   * возможности, рекомендации и nextAction приходят из backend готовым
   * текстом (AiAgentReadinessService) и в словаре не дублируются.
   */
  agents: {
    title: string;
    lead: string;
    /** Итоговая готовность контура целиком (AiBriefingDto.posture). */
    postureTitle: string;
    fieldProvider: string;
    fieldModelClass: string;
    fieldCapabilities: string;
    errorTitle: string;
    empty: string;
    chatOpen: string;
    chatClose: string;
    chatPlaceholder: string;
    chatInputLabel: string;
    chatSend: string;
    chatSending: string;
    chatSources: string;
    /** Коды из backend; ключ не найден — показываем сырой код. */
    postures: Record<string, string>;
    statuses: Record<string, string>;
  };

  /** Раздел управления операторами консоли (только для суперадмина). */
  users: {
    title: string;
    lead: string;
    colUsername: string;
    colDisplayName: string;
    colRole: string;
    colStatus: string;
    colLastLogin: string;
    statusActive: string;
    statusInactive: string;
    neverLoggedIn: string;
    youBadge: string;
    createTitle: string;
    editTitle: string;
    fieldUsername: string;
    fieldUsernameHint: string;
    fieldDisplayName: string;
    fieldPassword: string;
    fieldPasswordHintCreate: string;
    fieldPasswordHintEdit: string;
    fieldRole: string;
    fieldActive: string;
    roleHintViewer: string;
    roleHintOperator: string;
    roleHintEditor: string;
    roleHintSuperadmin: string;
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
    errUnavailable: string;
    errLocked: string;
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

  /**
   * Карта и редактор геометрии по карте (общие строки для /map и форм
   * линии/станции).
   */
  geo: {
    /** Доступные имена регионов карты и состояние загрузки. */
    regionNetwork: string;
    regionLineEditor: string;
    regionStationEditor: string;
    mapLoading: string;
    zoomIn: string;
    zoomOut: string;
    /** Редактор трассы линии. */
    drawPathTitle: string;
    drawPathHint: string;
    undoPoint: string;
    clearPath: string;
    deletePoint: string;
    pointsCount: string;
    noPointSelected: string;
    selectedPoint: string;
    /** Редактор точки станции. */
    pickPointTitle: string;
    pickPointHint: string;
    clearPoint: string;
    /** Сообщения aria-live о результате правки. */
    livePointAdded: string;
    livePointMoved: string;
    livePointRemoved: string;
    livePointInserted: string;
    livePathCleared: string;
    livePointSet: string;
    /** Ручной ввод как запасной/клавиатурный путь. */
    manualTitle: string;
    manualHint: string;
    /** Многосегментная трасса: правка по карте запрещена. */
    multiSegmentTitle: string;
    multiSegmentText: string;
    multiSegmentSegments: string;
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

  /** Постраничная навигация — одна на все ленты консоли. */
  pager: {
    /** aria-label группы кнопок. */
    label: string;
    prev: string;
    next: string;
    /** Составляется как «{page} 2 / 5». */
    page: string;
    /** Размер страницы: «{pageSize}: 50». */
    pageSize: string;
  };

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
    fieldFormat: string;
    fieldFormatHint: string;
    /** Бейдж формата источника в ленте (ImportFormat). */
    formats: Record<ImportFormat, string>;
    /** Пункты выбора в форме — вид импорта, а не формат (ImportKind). */
    kinds: Record<ImportKind, string>;
    kindHints: Record<ImportKind, string>;
    /** Подпись поля файла зависит от вида: GeoJSON, ZIP сети, ZIP тарифов и CSV — разное. */
    fileByFormat: Record<ImportKind, string>;
    chooseFileByFormat: Record<ImportKind, string>;
    /** Что импортировали (ImportJob.TYPE_*): сеть или тарифы. */
    types: Record<ImportJobType, string>;
    colType: string;
    fieldFeedLang: string;
    fieldFeedLangHint: string;
    feedLangAuto: string;
    fieldTargetStatus: string;
    fieldTargetStatusHint: string;
    /** Соответствие rider_categories.txt категориям справочника. */
    fieldRiderCategories: string;
    fieldRiderCategoriesHint: string;
    fieldFareActive: string;
    fieldFareActiveHint: string;
    fareActiveAuto: string;
    fareActiveYes: string;
    fareActiveNo: string;
    /** Пояснение про поля, которых в GTFS Fares v2 нет. */
    faresGapsLead: string;
    colFormat: string;
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
    /** Предупреждения GTFS — рабочий список «что перевести», а не сбой. */
    warningsTitle: string;
    warningsLead: string;
    noWarnings: string;
    errorsOnlyTitle: string;
    noHardErrors: string;
    severities: Record<ImportErrorSeverity, string>;
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
    incidents: "Ҳодисаҳо",
    fares: "Тарофаҳо",
    notifications: "Паёмрасонӣ",
    tickets: "Билетҳо",
    webhooks: "Ҳамгироиҳо",
    imports: "Воридот",
    calendar: "Тақвим",
    features: "Функсияҳо",
    agents: "AI-агентҳо",
    users: "Операторон",
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
    logout: "Баромадан",
  },
  roles: {
    viewer: "Нозир",
    operator: "Оператор",
    editor: "Муҳаррир",
    superadmin: "Супермаъмур",
  },
  incidents: {
    title: "Ҳодисаҳо",
    lead: "Бақайдгирӣ ва баррасии ҳодисаҳои амалиётӣ.",
    createTitle: "Ҳодисаи нав",
    editTitle: "Тағйири ҳодиса",
    colCode: "Рамз",
    colTitle: "Ҳодиса",
    colSeverity: "Дараҷа",
    colStatus: "Ҳолат",
    colWhere: "Ҷой",
    colAssignee: "Масъул",
    colOccurred: "Вақти рӯйдод",
    fieldCategory: "Категория",
    fieldSeverity: "Дараҷаи муҳиммӣ",
    fieldTitle: "Сарлавҳа",
    fieldDescription: "Тавсиф",
    fieldLine: "Рамзи хат",
    fieldStation: "Рамзи истгоҳ",
    fieldAssignee: "Масъул",
    fieldAssigneeHint: "Логини оператор; холӣ — таъин нашудааст.",
    fieldOccurredAt: "Вақти рӯйдод",
    fieldResolution: "Натиҷаи баррасӣ",
    fieldResolutionHint: "Барои «Ҳал шуд» ҳатмист.",
    unassigned: "Таъин нашуда",
    networkWide: "Тамоми шабака",
    filterAll: "Ҳама",
    transitionTitle: "Тағйири ҳолат",
    reportedBy: "Қайд кард",
    statTodayLabel: "Имрӯз",
    statOpenLabel: "Кушода",
    categories: {
      safety: "Бехатарӣ",
      technical: "Техникӣ",
      passenger: "Мусофир",
      infrastructure: "Инфрасохтор",
      other: "Дигар",
    },
    severities: {
      low: "Паст",
      medium: "Миёна",
      high: "Баланд",
      critical: "Критикӣ",
    },
    statuses: {
      open: "Кушода",
      acknowledged: "Қабул шуд",
      in_progress: "Дар кор",
      resolved: "Ҳал шуд",
      closed: "Пӯшида",
    },
  },
  notifications: {
    title: "Паёмрасонӣ",
    lead: "Паёмҳо ба мусофирон, шаблонҳо ва навбати расонидан.",
    tabMessages: "Паёмҳо",
    tabProblems: "Навбати расонидан",
    tabTemplates: "Шаблонҳо",
    simulatedNotice:
      "Дар контури демо танҳо канали «Дар барнома» воқеан кор мекунад. Push, Email ва SMS тақлид карда мешаванд: ҳеҷ чиз ба мусофир намеравад, сабти «фиристода шуд» шартӣ аст.",
    simulatedBadge: "Тақлид",
    simulatedHint: "Канал провайдери воқеӣ надорад — расонидан тақлид шудааст.",
    colCode: "Рамз",
    colTitle: "Сарлавҳа",
    colType: "Навъ",
    colChannels: "Каналҳо",
    colStatus: "Ҳолат",
    colTargets: "Ҳадаф",
    colScheduled: "Ба нақша гирифта",
    colSent: "Фиристода шуд",
    filterAll: "Ҳама",
    createTitle: "Паёми нав",
    editTitle: "Тағйири паём",
    fieldCode: "Рамз",
    fieldCodeHint: "Ҳарфҳои лотинӣ; баъдан тағйир намеёбад.",
    fieldTemplate: "Шаблон",
    fieldTemplateHint: "Матнҳо нусхабардорӣ мешаванд ва аз шаблон вобаста намемонанд.",
    templateNone: "Бе шаблон",
    fieldType: "Навъи паём",
    fieldTitleText: "Сарлавҳа",
    fieldBody: "Матн",
    fieldChannels: "Каналҳои расонидан",
    fieldChannelsHint: "Ҳадди ақал як канал.",
    fieldTargets: "Ҳадафгирӣ",
    fieldTargetsHint: "Рӯйхати холӣ — тамоми шабака.",
    fieldTargetType: "Навъ",
    fieldTargetCode: "Рамз",
    addTarget: "Илова кардани ҳадаф",
    removeTarget: "Нест кардан",
    networkWide: "Тамоми шабака",
    fieldScheduledAt: "Нашри мӯҳлатдор",
    fieldScheduledAtHint: "Пур кунед — паём ба ҳолати «Ба нақша гирифта» мегузарад.",
    send: "Фиристодан",
    sendTitle: "Фиристодани паём",
    sendWarning:
      "Паём фавран ба фиристодан меравад ва пас аз он таҳрир намешавад.",
    sendDone: "Паём ба фиристодан гузашт",
    statusTitle: "Тағйири ҳолат",
    changeStatus: "Ҳолат",
    frozenTitle: "Таҳрир имконнопазир",
    frozenText:
      "Расонидан аллакай оғоз шудааст (фиристода истодааст / фиристода шуд / бекор шуд) — таърихи он чизе, ки ба мусофирон рафт, тағйир намеёбад.",
    deliveriesTitle: "Расонидани ин паём",
    showDeliveries: "Нишон додани расонидан",
    hideDeliveries: "Пинҳон кардан",
    problemsTitle: "Расониданҳои мушкилдор",
    problemsLead:
      "Ҳар чизе, ки нарасидааст: дар навбат ва нокоммуваффақ. Такрор онро ба навбат бармегардонад.",
    problemsEmpty: "Ҳамаи расониданҳо иҷро шудаанд",
    colChannel: "Канал",
    colRecipient: "Гиранда",
    colAttempts: "Кӯшишҳо",
    colError: "Сабаби нокомӣ",
    colDeliveredAt: "Расонида шуд",
    retry: "Такрор",
    retryHint: "Такрор танҳо барои расониданҳои нокоммуваффақ дастрас аст.",
    retryDone: "Расонидан ба навбат баргардонида шуд",
    noDeliveries: "Барои ин паём расонидан сабт нашудааст",
    templatesTitle: "Шаблонҳои паём",
    templatesLead:
      "Заготовкаҳои матн. Таҳрири шаблон паёмҳои аллакай сохташударо тағйир намедиҳад.",
    templateCreate: "Шаблони нав",
    templateEdit: "Тағйири шаблон",
    colName: "Номи хизматӣ",
    fieldName: "Номи хизматӣ",
    fieldNameHint: "Танҳо барои консол; ба мусофир намоиш дода намешавад.",
    fieldActive: "Шаблон фаъол аст",
    templatesReadOnly:
      "Шаблонҳо маълумотномаи таҳририянд: барои тағйир нақши «Муҳаррир» лозим аст.",
    types: {
      info: "Маълумот",
      warning: "Огоҳӣ",
      incident: "Ҳодиса",
      maintenance: "Хизматрасонӣ",
      promo: "Таблиғ",
    },
    statuses: {
      draft: "Лоиҳа",
      scheduled: "Ба нақша гирифта",
      sending: "Фиристода истодааст",
      sent: "Фиристода шуд",
      cancelled: "Бекор шуд",
    },
    channels: {
      in_app: "Дар барнома",
      push: "Push",
      email: "Email",
      sms: "SMS",
    },
    targetTypes: {
      line: "Хат",
      station: "Истгоҳ",
      segment: "Сегмент",
      role: "Нақш",
    },
    deliveryStatuses: {
      pending: "Дар навбат",
      sent: "Фиристода шуд",
      delivered: "Расонида шуд",
      failed: "Нокоммуваффақ",
    },
  },
  tickets: {
    title: "Билетҳо ва пардохтҳо",
    lead: "Билетҳо, пардохтҳо, баргардонидани дастӣ ва рӯйхати сиёҳ.",
    demoNotice:
      "Контури демо: пардохтҳоро DemoPaymentGateway тақлид мекунад, пули воқеӣ ҳаракат намекунад. Билетҳои дорои нишонаи «Демо» пардохти воқеӣ надоранд.",
    demoBadge: "Демо",
    demoHint: "Дар паси ин сабт пардохти воқеӣ нест.",
    tabTickets: "Билетҳо",
    tabPayments: "Пардохтҳо",
    tabBlocklist: "Рӯйхати сиёҳ",
    filterAll: "Ҳама",
    colCode: "Рамзи билет",
    colFare: "Тарофа",
    colKind: "Навъ",
    colRider: "Категория",
    colStatus: "Ҳолат",
    colPrice: "Нарх",
    colValidity: "Мӯҳлат",
    colBalance: "Бақия",
    validityOpen: "Бемӯҳлат",
    noToken: "Токени QR дар система нигоҳ дошта намешавад — танҳо хеши он.",
    refund: "Баргардонидан",
    refundTitle: "Баргардонидани дастӣ",
    refundLead:
      "Баргардонидани операторӣ ҳатто билети истифодашударо иҷозат медиҳад. Амал номӣ аст ва ба аудит меафтад.",
    fieldRefundReason: "Асоси баргардонидан",
    fieldRefundReasonHint: "Ҳатмист: баргардонидани беасос баҳснопазир аст.",
    refundDone: "Баргардонидан сабт шуд",
    paymentsTitle: "Лентаи пардохтҳо",
    paymentsLead:
      "Харид ва пуркуниҳо. Маълумоти корт на дар ин ҷо ҳаст, на дар модел.",
    colPaymentKind: "Таъинот",
    colAmount: "Маблағ",
    colProvider: "Провайдер",
    colTicket: "Билет",
    colFailure: "Сабаби рад",
    colCreated: "Сана",
    noTicket: "Билет барорида нашуд",
    blocklistTitle: "Рӯйхати сиёҳ",
    blocklistLead:
      "Баста шудан фавран амал мекунад: билетҳои субъект ба ҳолати «Баста» мегузаранд.",
    blocklistAdd: "Бастани субъект",
    blocklistRemove: "Кушодан",
    blocklistRemoveHint:
      "Кушодан хариди билетҳои навро иҷозат медиҳад, вале билетҳои аллакай бастаро барқарор намекунад: токенҳои онҳо ошкор шудаанд.",
    colSubjectType: "Навъи субъект",
    colSubject: "Субъект",
    colReason: "Асос",
    colBlockedBy: "Баст",
    fieldSubjectType: "Навъи субъект",
    fieldSubjectValue: "Қимати субъект",
    fieldSubjectValueHint: "Рамзи билет, токен ё шиносаи харидор.",
    fieldBlockReason: "Асоси бастан",
    fieldBlockReasonHint: "Ҳатмист: бастани беасос барҳам дода намешавад.",
    tokenHashNotice:
      "Барои навъи «Токен» худи токен ворид карда мешавад — хидмат танҳо SHA-256-и онро нигоҳ медорад; токени кушода на ба БД, на ба аудит намеафтад.",
    blockDone: "Субъект баста шуд",
    unblockDone: "Баста шудан бардошта шуд",
    kinds: { single: "Яквақта", pass: "Абонемент" },
    statuses: {
      issued: "Барорида шуд",
      active: "Фаъол",
      used: "Истифода шуд",
      expired: "Мӯҳлат гузашт",
      refunded: "Баргардонида шуд",
      blocked: "Баста",
    },
    paymentKinds: { purchase: "Харид", topup: "Пуркунӣ" },
    paymentStatuses: {
      pending: "Дар интизор",
      authorized: "Тасдиқ шуд",
      captured: "Гирифта шуд",
      failed: "Рад шуд",
      refunded: "Баргардонида шуд",
    },
    subjectTypes: { ticket: "Билет", token: "Токен", rider: "Харидор" },
  },
  webhooks: {
    title: "Ҳамгироиҳо",
    lead: "Обуначиёни вебхук, секретҳо, лимитҳо ва навбати расонидан.",
    tabSubscribers: "Обуначиён",
    tabDeliveries: "Навбати расонидан",
    subscribersTitle: "Обуначиёни вебхук",
    subscribersLead:
      "Суроғаҳои беруна, ки маълумоти шабака ба онҳо меравад. Секрет ҳеҷ гоҳ дар рӯйхат нишон дода намешавад.",
    colName: "Ном",
    colUrl: "Суроға",
    colEvents: "Рӯйдодҳо",
    colState: "Ҳолат",
    colRateLimit: "Лимит",
    colFingerprint: "Изи секрет",
    perMinute: "дар дақиқа",
    createTitle: "Обуначии нав",
    editTitle: "Тағйири обуначӣ",
    fieldCode: "Рамз",
    fieldCodeHint: "A-Z, a-z, 0-9, _ ва -; баъдан тағйир намеёбад.",
    fieldName: "Ном",
    fieldUrl: "Суроғаи расонидан",
    fieldUrlHint: "http:// ё https://",
    fieldEvents: "Навъҳои рӯйдод",
    fieldEventsHint: "Ҳадди ақал як навъ.",
    fieldActive: "Обуна фаъол аст",
    fieldRateLimit: "Лимити дархостҳо (дар дақиқа)",
    fieldRateLimitHint: "Холӣ — қимати пешфарзи хидмат.",
    rotate: "Ротатсияи секрет",
    rotateTitle: "Ротатсияи секрет",
    rotateWarning:
      "Секрети кӯҳна фавран аз кор мемонад: обуначӣ расониданҳоро рад мекунад, то калидро дар назди худ нав накунад.",
    rotateConfirm: "Секрети навро сохтан",
    secretTitle: "Секрет — фақат ҳозир",
    secretOnceWarning:
      "Ҳозир нусхабардорӣ кунед: ин ягона маротибаест, ки секрет нишон дода мешавад. Онро дубора гирифтан мумкин нест — танҳо ротатсия кардан.",
    secretLead: "Ба интегратор диҳед ва дар назди худ нигоҳ надоред.",
    secretLabel: "Секрети имзо",
    secretCopy: "Нусхабардорӣ",
    secretCopied: "Нусхабардорӣ шуд",
    secretCopyFailed: "Нусхабардорӣ нашуд — дастӣ интихоб кунед",
    secretAck: "Секретро нигоҳ доштам",
    fingerprintHint:
      "Изи ангушт — 8 аломати аввали хеш: барои санҷиши «оё калиди дуруст аст» баъд аз ротатсия.",
    superadminOnly:
      "Идораи обуначиён танҳо ба супермаъмур дастрас аст; навбати расонидан — ба навбатдор.",
    deliveriesTitle: "Навбати расонидан",
    deliveriesLead:
      "Расониданҳои вебхук: сабаб, шумораи кӯшишҳо ва вақти кӯшиши навбатӣ.",
    dlqTitle: "DLQ — дахолат лозим",
    dlqLead:
      "Ҳолати «Мурда»: кӯшишҳои худкор тамом шуданд. Ин расониданҳо худ аз худ намераванд.",
    dlqEmpty: "Дар DLQ чизе нест",
    filterAttention: "Диққат талаб мекунанд",
    colEvent: "Рӯйдод",
    colSubject: "Объект",
    colSubscriber: "Обуначӣ",
    colAttempts: "Кӯшишҳо",
    colResponse: "Ҷавоб",
    colError: "Сабаби нокомӣ",
    colNextAttempt: "Кӯшиши навбатӣ",
    colTrace: "Trace",
    retryDue: "ҳозир",
    retry: "Такрор",
    retryDone: "Расонидан ба навбат баргардонида шуд",
    notRetryable: "Такрор дастрас нест",
    eventTypes: {
      alert_published: "Огоҳӣ нашр шуд",
      alert_cleared: "Огоҳӣ бекор шуд",
      incident_opened: "Ҳодиса кушода шуд",
      incident_resolved: "Ҳодиса ҳал шуд",
      station_status_changed: "Ҳолати истгоҳ иваз шуд",
      schedule_changed: "Ҷадвал иваз шуд",
      train_delayed: "Дер мондани қатор",
    },
    deliveryStatuses: {
      pending: "Дар навбат",
      sent: "Расонида шуд",
      failed: "Нокоммуваффақ",
      dead: "Мурда (DLQ)",
    },
  },
  agents: {
    title: "Агентҳои AI",
    lead: "Феҳристи моделҳо ва хулосаи омодагии контури AI-и платформа.",
    postureTitle: "Омодагии контур",
    fieldProvider: "Провайдер",
    fieldModelClass: "Синфи модел",
    fieldCapabilities: "Имкониятҳо",
    errorTitle: "Хулосаи AI дастрас нест",
    empty: "Маълумоти хулосаи AI нест.",
    chatOpen: "Чат",
    chatClose: "Пӯшидани чат",
    chatPlaceholder: "Аз агент пурсед…",
    chatInputLabel: "Аз агент пурсед",
    chatSend: "Фиристодан",
    chatSending: "Фиристода истодааст…",
    chatSources: "Манбаъҳо",
    postures: {
      ready: "Омода",
      pilot_ready_with_security_gap: "Барои озмоиш омода, камбудии амният ҳаст",
    },
    statuses: {
      ready: "Омода",
      watching: "Дар назорат",
      monitoring: "Мониторинг",
      needs_data: "Маълумот лозим",
      needs_schedule: "Ҷадвал лозим",
      needs_content: "Мундариҷа лозим",
      needs_hardening: "Ҳифзи иловагӣ лозим",
    },
  },
  users: {
    title: "Операторони консол",
    lead: "Ҳисобҳо ва нақшҳои дастрасӣ ба консоли идоракунӣ.",
    colUsername: "Логин",
    colDisplayName: "Ном",
    colRole: "Нақш",
    colStatus: "Ҳолат",
    colLastLogin: "Вуруди охирин",
    statusActive: "Фаъол",
    statusInactive: "Ғайрифаъол",
    neverLoggedIn: "Ҳеҷ гоҳ",
    youBadge: "шумо",
    createTitle: "Оператори нав",
    editTitle: "Тағйири оператор",
    fieldUsername: "Логин",
    fieldUsernameHint: "Ҳарфҳои лотинӣ, рақамҳо, . _ - ; баъдан тағйир намеёбад.",
    fieldDisplayName: "Номи намоишӣ",
    fieldPassword: "Рамз",
    fieldPasswordHintCreate: "На камтар аз 12 аломат.",
    fieldPasswordHintEdit: "Холӣ монед — рамзи ҷорӣ нигоҳ дошта мешавад.",
    fieldRole: "Нақш",
    fieldActive: "Ҳисоб фаъол аст",
    roleHintViewer: "Танҳо хониш: кортҳо, таҳлил, аудит.",
    roleHintOperator: "Муроҷиатҳо, ҳодисаҳо, огоҳиномаҳои хидматӣ.",
    roleHintEditor: "Мундариҷа ва маълумотномаҳо: хабарҳо, тарофаҳо, истгоҳҳо, хатҳо.",
    roleHintSuperadmin: "Дастрасии пурра, аз ҷумла идоракунии операторон.",
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
    errUnavailable: "Сервер дастрас нест. Каме баъдтар кӯшиш кунед.",
    errLocked: "Кӯшишҳои зиёди воридшавӣ. Ҳисоб муваққатан баста шуд, каме баъдтар кӯшиш кунед.",
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
  geo: {
    regionNetwork: "Харитаи шабакаи метро",
    regionLineEditor: "Харита барои кашидани трассаи хат",
    regionStationEditor: "Харита барои гузоштани нуқтаи истгоҳ",
    mapLoading: "Харита бор мешавад…",
    zoomIn: "Наздик кардан",
    zoomOut: "Дур кардан",
    drawPathTitle: "Кашидан аз рӯи харита",
    drawPathHint:
      "Клик — нуқтаи нав. Нуқтаро кашола кунед, то ҷойивазаш кунед; нуқтаи хокистарӣ дар байн — нуқтаи нав дар мобайн.",
    undoPoint: "Бекор кардани нуқтаи охирин",
    clearPath: "Тоза кардани трасса",
    deletePoint: "Нест кардани нуқтаи интихобшуда",
    pointsCount: "Нуқтаҳо",
    noPointSelected: "Нуқта интихоб нашудааст",
    selectedPoint: "Нуқтаи интихобшуда",
    pickPointTitle: "Гузоштан аз рӯи харита",
    pickPointHint: "Клик — нуқтаи истгоҳ. Нуқтаро кашола кунед, то дақиқ кунед.",
    clearPoint: "Тоза кардани нуқта",
    livePointAdded: "Нуқта илова шуд",
    livePointMoved: "Нуқта ҷойиваз шуд",
    livePointRemoved: "Нуқта нест шуд",
    livePointInserted: "Нуқта дар мобайн илова шуд",
    livePathCleared: "Трасса тоза шуд",
    livePointSet: "Координатаҳо гузошта шуданд",
    manualTitle: "Ворид кардани дастӣ",
    manualHint:
      "Роҳи клавиатурӣ ва эҳтиётӣ: харита бе шабака ё муш дастрас нест.",
    multiSegmentTitle: "Трассаи бисёрқисма — таҳрир аз рӯи харита баста аст",
    multiSegmentText:
      "Дар пойгоҳ ин хат аз якчанд қисм (шоха) иборат аст, вале формат «path» танҳо як хатро мефиристад — нигоҳ доштан қисмҳоро ба як хат мепечонад. Геометрия бетағйир мемонад; барои тағйир импорти GeoJSON-ро истифода баред.",
    multiSegmentSegments: "Қисмҳо",
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
  pager: {
    label: "Гузариш аз рӯи саҳифаҳо",
    prev: "Қаблӣ",
    next: "Навбатӣ",
    page: "Саҳифа",
    pageSize: "Дар саҳифа",
  },
  linesTitle: "Хатҳо",
  stationsTitle: "Истгоҳҳо",
  alertsTitle: "Огоҳиҳои фаъол",
  newsTitle: "Хабарҳо",
  auditTitle: "Журнали аудит",
  operations: {
    importsTitle: "Воридоти маълумот",
    importsLead:
      "Шабакаро дар формати GeoJSON, GTFS ё CSV бор кунед ва натиҷаи ҳар як воридотро назорат намоед.",
    uploadTitle: "Боркунии маълумоти шабака",
    sourceName: "Номи манбаъ",
    sourceHint: "Номи файл ё системаи манбаъ",
    fieldFormat: "Навъи воридот",
    fieldFormatHint: "Навъ роҳи воридот ва он чиро, ки тағйир меёбад, муайян мекунад.",
    formats: { geojson: "GeoJSON", gtfs: "GTFS", csv: "CSV" },
    kinds: {
      geojson: "GeoJSON — шабака",
      gtfs: "GTFS — шабака",
      "gtfs-fares": "GTFS Fares v2 — тарофаҳо",
      csv: "CSV — шабака",
    },
    kindHints: {
      geojson: "FeatureCollection бо хатҳо ва истгоҳҳо.",
      gtfs: "ZIP-архиви фид; танҳо маршрутҳои метро (route_type=1).",
      "gtfs-fares":
        "ZIP-архиви фид бо fare_products.txt; ба маълумотномаи тарофаҳо ворид мешавад, шабака тағйир намеёбад.",
      csv: "UTF-8: сарлавҳа + сатрҳо; сутуни entity — line ё station.",
    },
    fileByFormat: {
      geojson: "Файли GeoJSON",
      gtfs: "GTFS-фид (ZIP)",
      "gtfs-fares": "Фиди GTFS Fares (ZIP)",
      csv: "Файли CSV",
    },
    chooseFileByFormat: {
      geojson: "Файли GeoJSON-ро интихоб кунед",
      gtfs: "ZIP-архиви GTFS-ро интихоб кунед",
      "gtfs-fares": "ZIP-архиви тарофаҳоро интихоб кунед",
      csv: "Файли CSV-ро интихоб кунед",
    },
    types: {
      network_geojson: "Шабака (GeoJSON)",
      network_gtfs: "Шабака (GTFS)",
      network_csv: "Шабака (CSV)",
      fare_gtfs: "Тарофаҳо (GTFS Fares)",
    },
    colType: "Чӣ ворид шуд",
    fieldFeedLang: "Забони фид",
    fieldFeedLangHint: "GTFS якзабона аст; забонҳои боқимонда бо огоҳӣ пур мешаванд.",
    feedLangAuto: "Худкор (agency.txt)",
    fieldTargetStatus: "Ҳолати объектҳо",
    fieldTargetStatusHint: "Дар GTFS ҳолати давраи ҳаёт нест — дар ин ҷо дода мешавад.",
    fieldRiderCategories: "Мутобиқати категорияҳои мусофирон",
    fieldRiderCategoriesHint:
      "Формат: RC_ADULT:adult,RC_KID:child. Категорияи номаълум хатои сатр медиҳад, на иваз кардани хомӯшона.",
    fieldFareActive: "Нашри тарофаҳо",
    fieldFareActiveHint:
      "Дар GTFS аломати нашр нест. Холӣ — маҳсулоти нав ғайрифаъол эҷод мешавад, мавҷуда аломати худро нигоҳ медорад.",
    fareActiveAuto: "Тағйир надодан (пешфарз)",
    fareActiveYes: "Фаъол кардан",
    fareActiveNo: "Ғайрифаъол кардан",
    faresGapsLead:
      "Мӯҳлати эътибор (validity_minutes) ва интиқолдиҳанда (fare_media) дар GTFS Fares v2 нестанд: онҳо ворид намешаванд ва ҳар як ҳолат дар ҳисобот ҳамчун огоҳӣ нишон дода мешавад.",
    colFormat: "Формат",
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
    errorsTitle: "Ҳисоботи воридот",
    noErrors: "Барои ин воридот сабт нест",
    warningsTitle: "Тарҷумаҳои намерасида",
    warningsLead:
      "Ин сабтҳо сабт нестанд: GTFS якзабона аст, аз ин рӯ забонҳои намерасида бо забони фид пур шудаанд. Рӯйхати он чизе, ки тарҷума талаб мекунад.",
    noWarnings: "Огоҳӣ нест",
    errorsOnlyTitle: "Хатоҳо",
    noHardErrors: "Хато нест",
    severities: { error: "Хато", warning: "Огоҳӣ" },
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
    incidents: "Инциденты",
    fares: "Тарифы",
    notifications: "Рассылки",
    tickets: "Билеты",
    webhooks: "Интеграции",
    imports: "Импорты",
    calendar: "Календарь",
    features: "Функции",
    agents: "AI-агенты",
    users: "Операторы",
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
    logout: "Выйти",
  },
  roles: {
    viewer: "Наблюдатель",
    operator: "Оператор",
    editor: "Редактор",
    superadmin: "Суперадминистратор",
  },
  incidents: {
    title: "Инциденты",
    lead: "Регистрация и разбор операционных происшествий.",
    createTitle: "Новый инцидент",
    editTitle: "Изменение инцидента",
    colCode: "Код",
    colTitle: "Инцидент",
    colSeverity: "Критичность",
    colStatus: "Состояние",
    colWhere: "Место",
    colAssignee: "Ответственный",
    colOccurred: "Произошёл",
    fieldCategory: "Категория",
    fieldSeverity: "Критичность",
    fieldTitle: "Заголовок",
    fieldDescription: "Описание",
    fieldLine: "Код линии",
    fieldStation: "Код станции",
    fieldAssignee: "Ответственный",
    fieldAssigneeHint: "Логин оператора; пусто — не назначен.",
    fieldOccurredAt: "Время возникновения",
    fieldResolution: "Разбор",
    fieldResolutionHint: "Обязателен для перевода в «Устранён».",
    unassigned: "Не назначен",
    networkWide: "Вся сеть",
    filterAll: "Все",
    transitionTitle: "Смена состояния",
    reportedBy: "Зарегистрировал",
    statTodayLabel: "Сегодня",
    statOpenLabel: "В работе",
    categories: {
      safety: "Безопасность",
      technical: "Технические",
      passenger: "Пассажир",
      infrastructure: "Инфраструктура",
      other: "Другое",
    },
    severities: {
      low: "Низкая",
      medium: "Средняя",
      high: "Высокая",
      critical: "Критическая",
    },
    statuses: {
      open: "Открыт",
      acknowledged: "Принят",
      in_progress: "В работе",
      resolved: "Устранён",
      closed: "Закрыт",
    },
  },
  notifications: {
    title: "Рассылки",
    lead: "Сообщения пассажирам, шаблоны и очередь доставки.",
    tabMessages: "Рассылки",
    tabProblems: "Очередь доставки",
    tabTemplates: "Шаблоны",
    simulatedNotice:
      "На демо-контуре реально работает только канал «В приложении». Push, Email и SMS имитируются: пассажиру ничего не уходит, отметка «отправлено» условна.",
    simulatedBadge: "Имитация",
    simulatedHint: "У канала нет реального провайдера — доставка имитируется.",
    colCode: "Код",
    colTitle: "Заголовок",
    colType: "Тип",
    colChannels: "Каналы",
    colStatus: "Статус",
    colTargets: "Адресация",
    colScheduled: "Запланирована",
    colSent: "Отправлена",
    filterAll: "Все",
    createTitle: "Новая рассылка",
    editTitle: "Изменение рассылки",
    fieldCode: "Код",
    fieldCodeHint: "Латиница; после создания не меняется.",
    fieldTemplate: "Шаблон",
    fieldTemplateHint: "Тексты копируются и дальше от шаблона не зависят.",
    templateNone: "Без шаблона",
    fieldType: "Тип рассылки",
    fieldTitleText: "Заголовок",
    fieldBody: "Текст",
    fieldChannels: "Каналы доставки",
    fieldChannelsHint: "Минимум один канал.",
    fieldTargets: "Адресация",
    fieldTargetsHint: "Пустой список — вся сеть.",
    fieldTargetType: "Тип",
    fieldTargetCode: "Код",
    addTarget: "Добавить цель",
    removeTarget: "Удалить",
    networkWide: "Вся сеть",
    fieldScheduledAt: "Отложенная публикация",
    fieldScheduledAtHint: "Заполните — рассылка сразу перейдёт в «Запланирована».",
    send: "Отправить",
    sendTitle: "Отправка рассылки",
    sendWarning:
      "Рассылка немедленно уйдёт в отправку и после этого не редактируется.",
    sendDone: "Рассылка отправлена",
    statusTitle: "Смена состояния",
    changeStatus: "Состояние",
    frozenTitle: "Редактирование запрещено",
    frozenText:
      "Доставка уже начата (отправляется / отправлена / отменена) — история того, что ушло пассажирам, задним числом не меняется.",
    deliveriesTitle: "Доставки этой рассылки",
    showDeliveries: "Показать доставки",
    hideDeliveries: "Скрыть",
    problemsTitle: "Проблемные доставки",
    problemsLead:
      "Всё, что не доставлено: в очереди и с ошибкой. Повтор возвращает доставку в очередь, а не объявляет отправленной.",
    problemsEmpty: "Все доставки выполнены",
    colChannel: "Канал",
    colRecipient: "Получатель",
    colAttempts: "Попытки",
    colError: "Причина провала",
    colDeliveredAt: "Доставлено",
    retry: "Повторить",
    retryHint: "Повтор доступен только для проваленных доставок.",
    retryDone: "Доставка возвращена в очередь",
    noDeliveries: "Для этой рассылки доставки не зарегистрированы",
    templatesTitle: "Шаблоны рассылок",
    templatesLead:
      "Заготовки текста. Правка шаблона не затрагивает уже созданные из него рассылки.",
    templateCreate: "Новый шаблон",
    templateEdit: "Изменение шаблона",
    colName: "Служебное имя",
    fieldName: "Служебное имя",
    fieldNameHint: "Только для консоли; пассажиру не показывается.",
    fieldActive: "Шаблон активен",
    templatesReadOnly:
      "Шаблоны — редакционный справочник: для правки нужна роль «Редактор».",
    types: {
      info: "Информация",
      warning: "Предупреждение",
      incident: "Инцидент",
      maintenance: "Обслуживание",
      promo: "Промо",
    },
    statuses: {
      draft: "Черновик",
      scheduled: "Запланирована",
      sending: "Отправляется",
      sent: "Отправлена",
      cancelled: "Отменена",
    },
    channels: {
      in_app: "В приложении",
      push: "Push",
      email: "Email",
      sms: "SMS",
    },
    targetTypes: {
      line: "Линия",
      station: "Станция",
      segment: "Сегмент",
      role: "Роль",
    },
    deliveryStatuses: {
      pending: "В очереди",
      sent: "Отправлена",
      delivered: "Доставлена",
      failed: "Провал",
    },
  },
  tickets: {
    title: "Билеты и платежи",
    lead: "Билеты, платежи, ручной возврат и чёрный список.",
    demoNotice:
      "Демо-контур: платежи имитирует DemoPaymentGateway, реальные деньги не движутся. Билеты с отметкой «Демо» не обеспечены настоящим платежом.",
    demoBadge: "Демо",
    demoHint: "За этой записью нет реального платежа.",
    tabTickets: "Билеты",
    tabPayments: "Платежи",
    tabBlocklist: "Чёрный список",
    filterAll: "Все",
    colCode: "Код билета",
    colFare: "Тариф",
    colKind: "Вид",
    colRider: "Категория",
    colStatus: "Статус",
    colPrice: "Цена",
    colValidity: "Срок",
    colBalance: "Баланс",
    validityOpen: "Бессрочно",
    noToken: "Токен QR в системе не хранится — только его хеш.",
    refund: "Возврат",
    refundTitle: "Ручной возврат",
    refundLead:
      "Операторский возврат допускает даже погашенный билет. Операция именная и попадает в аудит.",
    fieldRefundReason: "Основание возврата",
    fieldRefundReasonHint: "Обязательно: возврат без причины неоспорим.",
    refundDone: "Возврат зарегистрирован",
    paymentsTitle: "Лента платежей",
    paymentsLead:
      "Покупки и пополнения. Карточных данных здесь нет — их нет и в модели.",
    colPaymentKind: "Назначение",
    colAmount: "Сумма",
    colProvider: "Провайдер",
    colTicket: "Билет",
    colFailure: "Причина отказа",
    colCreated: "Дата",
    noTicket: "Билет не выпускался",
    blocklistTitle: "Чёрный список",
    blocklistLead:
      "Блокировка применяется немедленно: билеты субъекта переводятся в «Заблокирован».",
    blocklistAdd: "Заблокировать субъект",
    blocklistRemove: "Снять",
    blocklistRemoveHint:
      "Снятие открывает покупку новых билетов, но уже заблокированные билеты не восстанавливает: их токены скомпрометированы.",
    colSubjectType: "Тип субъекта",
    colSubject: "Субъект",
    colReason: "Основание",
    colBlockedBy: "Заблокировал",
    fieldSubjectType: "Тип субъекта",
    fieldSubjectValue: "Значение субъекта",
    fieldSubjectValueHint: "Код билета, токен или идентификатор покупателя.",
    fieldBlockReason: "Основание блокировки",
    fieldBlockReasonHint: "Обязательно: блокировка без причины неснимаема по существу.",
    tokenHashNotice:
      "Для типа «Токен» вводится сам токен — сервис сохранит только его SHA-256; открытый токен не попадёт ни в БД, ни в аудит.",
    blockDone: "Субъект заблокирован",
    unblockDone: "Блокировка снята",
    kinds: { single: "Разовый", pass: "Проездной" },
    statuses: {
      issued: "Выпущен",
      active: "Активен",
      used: "Использован",
      expired: "Истёк",
      refunded: "Возвращён",
      blocked: "Заблокирован",
    },
    paymentKinds: { purchase: "Покупка", topup: "Пополнение" },
    paymentStatuses: {
      pending: "Ожидает",
      authorized: "Авторизован",
      captured: "Списан",
      failed: "Отклонён",
      refunded: "Возвращён",
    },
    subjectTypes: { ticket: "Билет", token: "Токен", rider: "Покупатель" },
  },
  webhooks: {
    title: "Интеграции",
    lead: "Подписчики вебхуков, секреты, лимиты и очередь доставок.",
    tabSubscribers: "Подписчики",
    tabDeliveries: "Очередь доставок",
    subscribersTitle: "Подписчики вебхуков",
    subscribersLead:
      "Внешние адреса, куда уходят данные сети. Секрет в списке не показывается никогда.",
    colName: "Название",
    colUrl: "Адрес",
    colEvents: "События",
    colState: "Состояние",
    colRateLimit: "Лимит",
    colFingerprint: "Отпечаток секрета",
    perMinute: "в минуту",
    createTitle: "Новый подписчик",
    editTitle: "Изменение подписчика",
    fieldCode: "Код",
    fieldCodeHint: "A-Z, a-z, 0-9, _ и -; после создания не меняется.",
    fieldName: "Название",
    fieldUrl: "Адрес доставки",
    fieldUrlHint: "http:// или https://",
    fieldEvents: "Типы событий",
    fieldEventsHint: "Минимум один тип.",
    fieldActive: "Подписка активна",
    fieldRateLimit: "Лимит запросов (в минуту)",
    fieldRateLimitHint: "Пусто — значение сервиса по умолчанию.",
    rotate: "Ротировать секрет",
    rotateTitle: "Ротация секрета",
    rotateWarning:
      "Старый секрет перестанет действовать немедленно: подписчик будет отвергать доставки, пока не обновит ключ у себя.",
    rotateConfirm: "Сгенерировать новый секрет",
    secretTitle: "Секрет — только сейчас",
    secretOnceWarning:
      "Скопируйте сейчас: это единственный показ секрета. Получить его повторно нельзя — только ротировать.",
    secretLead: "Передайте интегратору и не сохраняйте у себя.",
    secretLabel: "Секрет подписи",
    secretCopy: "Скопировать",
    secretCopied: "Скопировано",
    secretCopyFailed: "Не удалось скопировать — выделите вручную",
    secretAck: "Я сохранил секрет",
    fingerprintHint:
      "Отпечаток — первые 8 символов хеша: чтобы сверить «тот ли ключ» после ротации, не получая ключа.",
    superadminOnly:
      "Управление подписчиками доступно только суперадмину; очередь доставок — дежурному оператору.",
    deliveriesTitle: "Очередь доставок",
    deliveriesLead:
      "Доставки вебхуков: причина, число попыток и время следующей попытки.",
    dlqTitle: "DLQ — требует вмешательства",
    dlqLead:
      "Состояние «Мёртвая»: автоматические попытки исчерпаны. Эти доставки сами не уедут.",
    dlqEmpty: "В DLQ пусто",
    filterAttention: "Требуют внимания",
    colEvent: "Событие",
    colSubject: "Объект",
    colSubscriber: "Подписчик",
    colAttempts: "Попытки",
    colResponse: "Ответ",
    colError: "Причина провала",
    colNextAttempt: "Следующая попытка",
    colTrace: "Trace",
    retryDue: "сейчас",
    retry: "Повторить",
    retryDone: "Доставка возвращена в очередь",
    notRetryable: "Повтор недоступен",
    eventTypes: {
      alert_published: "Уведомление опубликовано",
      alert_cleared: "Уведомление снято",
      incident_opened: "Инцидент открыт",
      incident_resolved: "Инцидент устранён",
      station_status_changed: "Статус станции изменён",
      schedule_changed: "Расписание изменено",
      train_delayed: "Задержка поезда",
    },
    deliveryStatuses: {
      pending: "В очереди",
      sent: "Доставлена",
      failed: "Провал",
      dead: "Мёртвая (DLQ)",
    },
  },
  agents: {
    title: "AI-агенты",
    lead: "Реестр моделей и сводка готовности AI-контура платформы.",
    postureTitle: "Готовность контура",
    fieldProvider: "Провайдер",
    fieldModelClass: "Класс модели",
    fieldCapabilities: "Возможности",
    errorTitle: "Сводка AI недоступна",
    empty: "Нет данных сводки AI.",
    chatOpen: "Чат",
    chatClose: "Закрыть чат",
    chatPlaceholder: "Спросить агента…",
    chatInputLabel: "Спросить агента",
    chatSend: "Отправить",
    chatSending: "Отправка…",
    chatSources: "Источники",
    postures: {
      ready: "Готов",
      pilot_ready_with_security_gap: "Готов к пилоту, есть пробел в безопасности",
    },
    statuses: {
      ready: "Готов",
      watching: "Следит",
      monitoring: "Мониторинг",
      needs_data: "Нужны данные",
      needs_schedule: "Нужно расписание",
      needs_content: "Нужен контент",
      needs_hardening: "Требует усиления защиты",
    },
  },
  users: {
    title: "Операторы консоли",
    lead: "Учётные записи и роли доступа к операционной консоли.",
    colUsername: "Логин",
    colDisplayName: "Имя",
    colRole: "Роль",
    colStatus: "Состояние",
    colLastLogin: "Последний вход",
    statusActive: "Активен",
    statusInactive: "Отключён",
    neverLoggedIn: "Ни разу",
    youBadge: "вы",
    createTitle: "Новый оператор",
    editTitle: "Изменение оператора",
    fieldUsername: "Логин",
    fieldUsernameHint: "Латиница, цифры, . _ - ; после создания не меняется.",
    fieldDisplayName: "Отображаемое имя",
    fieldPassword: "Пароль",
    fieldPasswordHintCreate: "Не менее 12 символов.",
    fieldPasswordHintEdit: "Оставьте пустым — текущий пароль сохранится.",
    fieldRole: "Роль",
    fieldActive: "Учётная запись активна",
    roleHintViewer: "Только чтение: карточки, аналитика, аудит.",
    roleHintOperator: "Обращения, инциденты, сервисные уведомления.",
    roleHintEditor: "Контент и справочники: новости, тарифы, станции, линии.",
    roleHintSuperadmin: "Полный доступ, включая управление операторами.",
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
    errUnavailable: "Сервер недоступен. Попробуйте позже.",
    errLocked: "Слишком много попыток входа. Учётная запись временно заблокирована, повторите позже.",
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
  geo: {
    regionNetwork: "Карта сети метро",
    regionLineEditor: "Карта для рисования трассы линии",
    regionStationEditor: "Карта для установки точки станции",
    mapLoading: "Карта загружается…",
    zoomIn: "Приблизить",
    zoomOut: "Отдалить",
    drawPathTitle: "Рисовать по карте",
    drawPathHint:
      "Клик — новая точка. Точку можно перетащить; серая точка между вершинами вставляет точку в середину.",
    undoPoint: "Отменить последнюю точку",
    clearPath: "Очистить трассу",
    deletePoint: "Удалить выбранную точку",
    pointsCount: "Точек",
    noPointSelected: "Точка не выбрана",
    selectedPoint: "Выбранная точка",
    pickPointTitle: "Поставить по карте",
    pickPointHint: "Клик — точка станции. Перетащите точку, чтобы уточнить.",
    clearPoint: "Очистить точку",
    livePointAdded: "Точка добавлена",
    livePointMoved: "Точка перемещена",
    livePointRemoved: "Точка удалена",
    livePointInserted: "Точка вставлена в середину",
    livePathCleared: "Трасса очищена",
    livePointSet: "Координаты установлены",
    manualTitle: "Ручной ввод",
    manualHint:
      "Клавиатурный и запасной путь: карта недоступна без сети или мыши.",
    multiSegmentTitle: "Многосегментная трасса — правка по карте заблокирована",
    multiSegmentText:
      "В базе у этой линии несколько сегментов (ветки), а формат «path» передаёт только одну линию — сохранение схлопнуло бы сегменты в один. Геометрия останется без изменений; для правки используйте импорт GeoJSON.",
    multiSegmentSegments: "Сегментов",
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
  pager: {
    label: "Постраничная навигация",
    prev: "Назад",
    next: "Вперёд",
    page: "Страница",
    pageSize: "На странице",
  },
  linesTitle: "Линии",
  stationsTitle: "Станции",
  alertsTitle: "Активные уведомления",
  newsTitle: "Новости",
  auditTitle: "Журнал аудита",
  operations: {
    importsTitle: "Импорт данных",
    importsLead:
      "Загрузите сеть в формате GeoJSON, GTFS или CSV и контролируйте результат каждого задания импорта.",
    uploadTitle: "Загрузка данных сети",
    sourceName: "Название источника",
    sourceHint: "Имя файла или исходной системы",
    fieldFormat: "Вид импорта",
    fieldFormatHint: "Вид определяет маршрут импорта и то, что именно поменяется.",
    formats: { geojson: "GeoJSON", gtfs: "GTFS", csv: "CSV" },
    kinds: {
      geojson: "GeoJSON — сеть",
      gtfs: "GTFS — сеть",
      "gtfs-fares": "GTFS Fares v2 — тарифы",
      csv: "CSV — сеть",
    },
    kindHints: {
      geojson: "FeatureCollection с линиями и станциями.",
      gtfs: "ZIP-архив фида; импортируются только маршруты метро (route_type=1).",
      "gtfs-fares":
        "ZIP-архив фида с fare_products.txt; импортируется в справочник тарифов, сеть не затрагивается.",
      csv: "UTF-8: заголовок + строки; колонка entity — line или station.",
    },
    fileByFormat: {
      geojson: "Файл GeoJSON",
      gtfs: "GTFS-фид (ZIP)",
      "gtfs-fares": "Фид GTFS Fares (ZIP)",
      csv: "Файл CSV",
    },
    chooseFileByFormat: {
      geojson: "Выберите файл GeoJSON",
      gtfs: "Выберите ZIP-архив GTFS",
      "gtfs-fares": "Выберите ZIP-архив с тарифами",
      csv: "Выберите файл CSV",
    },
    types: {
      network_geojson: "Сеть (GeoJSON)",
      network_gtfs: "Сеть (GTFS)",
      network_csv: "Сеть (CSV)",
      fare_gtfs: "Тарифы (GTFS Fares)",
    },
    colType: "Что импортировано",
    fieldFeedLang: "Язык фида",
    fieldFeedLangHint: "GTFS одноязычен; остальные языки заполнятся с предупреждением.",
    feedLangAuto: "Автоматически (agency.txt)",
    fieldTargetStatus: "Статус объектов",
    fieldTargetStatusHint: "В GTFS статуса жизненного цикла нет — он задаётся здесь.",
    fieldRiderCategories: "Соответствие категорий пассажиров",
    fieldRiderCategoriesHint:
      "Формат: RC_ADULT:adult,RC_KID:child. Неизвестная категория даёт ошибку строки, а не тихую подстановку.",
    fieldFareActive: "Публикация тарифов",
    fieldFareActiveHint:
      "В GTFS признака публикации нет. Пусто — новый продукт создаётся неактивным, существующий сохраняет свой флаг.",
    fareActiveAuto: "Не менять (по умолчанию)",
    fareActiveYes: "Активировать",
    fareActiveNo: "Деактивировать",
    faresGapsLead:
      "Срок действия (validity_minutes) и носитель (fare_media) в GTFS Fares v2 отсутствуют: они не импортируются, и каждый такой случай попадает в отчёт предупреждением.",
    colFormat: "Формат",
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
    errorsTitle: "Отчёт об импорте",
    noErrors: "Для этого импорта записей нет",
    warningsTitle: "Недостающие переводы",
    warningsLead:
      "Это не сбой: GTFS одноязычен, поэтому недостающие языки заполнены языком фида. Список того, что требует перевода.",
    noWarnings: "Предупреждений нет",
    errorsOnlyTitle: "Ошибки",
    noHardErrors: "Ошибок нет",
    severities: { error: "Ошибка", warning: "Предупреждение" },
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
    incidents: "Incidents",
    fares: "Fares",
    notifications: "Notifications",
    tickets: "Tickets",
    webhooks: "Integrations",
    imports: "Imports",
    calendar: "Calendar",
    features: "Features",
    agents: "AI agents",
    users: "Operators",
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
    logout: "Sign out",
  },
  roles: {
    viewer: "Viewer",
    operator: "Operator",
    editor: "Editor",
    superadmin: "Superadmin",
  },
  incidents: {
    title: "Incidents",
    lead: "Logging and resolution of operational incidents.",
    createTitle: "New incident",
    editTitle: "Edit incident",
    colCode: "Code",
    colTitle: "Incident",
    colSeverity: "Severity",
    colStatus: "Status",
    colWhere: "Location",
    colAssignee: "Assignee",
    colOccurred: "Occurred",
    fieldCategory: "Category",
    fieldSeverity: "Severity",
    fieldTitle: "Title",
    fieldDescription: "Description",
    fieldLine: "Line code",
    fieldStation: "Station code",
    fieldAssignee: "Assignee",
    fieldAssigneeHint: "Operator username; empty means unassigned.",
    fieldOccurredAt: "Occurred at",
    fieldResolution: "Resolution",
    fieldResolutionHint: "Required to move to “Resolved”.",
    unassigned: "Unassigned",
    networkWide: "Network-wide",
    filterAll: "All",
    transitionTitle: "Change status",
    reportedBy: "Reported by",
    statTodayLabel: "Today",
    statOpenLabel: "Unresolved",
    categories: {
      safety: "Safety",
      technical: "Technical",
      passenger: "Passenger",
      infrastructure: "Infrastructure",
      other: "Other",
    },
    severities: {
      low: "Low",
      medium: "Medium",
      high: "High",
      critical: "Critical",
    },
    statuses: {
      open: "Open",
      acknowledged: "Acknowledged",
      in_progress: "In progress",
      resolved: "Resolved",
      closed: "Closed",
    },
  },
  notifications: {
    title: "Notifications",
    lead: "Passenger messages, templates and the delivery queue.",
    tabMessages: "Messages",
    tabProblems: "Delivery queue",
    tabTemplates: "Templates",
    simulatedNotice:
      "In the demo environment only the “In app” channel really works. Push, Email and SMS are simulated: nothing reaches the passenger, and a “sent” mark is nominal.",
    simulatedBadge: "Simulated",
    simulatedHint: "This channel has no real provider — delivery is simulated.",
    colCode: "Code",
    colTitle: "Title",
    colType: "Type",
    colChannels: "Channels",
    colStatus: "Status",
    colTargets: "Targeting",
    colScheduled: "Scheduled",
    colSent: "Sent",
    filterAll: "All",
    createTitle: "New message",
    editTitle: "Edit message",
    fieldCode: "Code",
    fieldCodeHint: "Latin letters; cannot be changed later.",
    fieldTemplate: "Template",
    fieldTemplateHint: "Texts are copied and no longer depend on the template.",
    templateNone: "No template",
    fieldType: "Message type",
    fieldTitleText: "Title",
    fieldBody: "Body",
    fieldChannels: "Delivery channels",
    fieldChannelsHint: "At least one channel.",
    fieldTargets: "Targeting",
    fieldTargetsHint: "An empty list means the whole network.",
    fieldTargetType: "Type",
    fieldTargetCode: "Code",
    addTarget: "Add target",
    removeTarget: "Remove",
    networkWide: "Network-wide",
    fieldScheduledAt: "Scheduled publication",
    fieldScheduledAtHint: "Set it and the message moves to “Scheduled” at once.",
    send: "Send",
    sendTitle: "Send message",
    sendWarning:
      "The message goes out immediately and cannot be edited afterwards.",
    sendDone: "Message sent",
    statusTitle: "Change status",
    changeStatus: "Status",
    frozenTitle: "Editing is blocked",
    frozenText:
      "Delivery has already started (sending / sent / cancelled) — the record of what reached passengers cannot change retroactively.",
    deliveriesTitle: "Deliveries of this message",
    showDeliveries: "Show deliveries",
    hideDeliveries: "Hide",
    problemsTitle: "Problem deliveries",
    problemsLead:
      "Everything undelivered: queued and failed. A retry puts the delivery back in the queue rather than declaring it sent.",
    problemsEmpty: "All deliveries completed",
    colChannel: "Channel",
    colRecipient: "Recipient",
    colAttempts: "Attempts",
    colError: "Failure reason",
    colDeliveredAt: "Delivered",
    retry: "Retry",
    retryHint: "Retry is available for failed deliveries only.",
    retryDone: "Delivery returned to the queue",
    noDeliveries: "No deliveries recorded for this message",
    templatesTitle: "Message templates",
    templatesLead:
      "Text blueprints. Editing a template does not touch messages already created from it.",
    templateCreate: "New template",
    templateEdit: "Edit template",
    colName: "Internal name",
    fieldName: "Internal name",
    fieldNameHint: "Console only; never shown to passengers.",
    fieldActive: "Template is active",
    templatesReadOnly:
      "Templates are editorial reference data: the Editor role is required to change them.",
    types: {
      info: "Info",
      warning: "Warning",
      incident: "Incident",
      maintenance: "Maintenance",
      promo: "Promo",
    },
    statuses: {
      draft: "Draft",
      scheduled: "Scheduled",
      sending: "Sending",
      sent: "Sent",
      cancelled: "Cancelled",
    },
    channels: {
      in_app: "In app",
      push: "Push",
      email: "Email",
      sms: "SMS",
    },
    targetTypes: {
      line: "Line",
      station: "Station",
      segment: "Segment",
      role: "Role",
    },
    deliveryStatuses: {
      pending: "Queued",
      sent: "Sent",
      delivered: "Delivered",
      failed: "Failed",
    },
  },
  tickets: {
    title: "Tickets and payments",
    lead: "Tickets, payments, manual refunds and the blocklist.",
    demoNotice:
      "Demo environment: payments are simulated by DemoPaymentGateway, no real money moves. Tickets marked “Demo” are not backed by a real payment.",
    demoBadge: "Demo",
    demoHint: "There is no real payment behind this record.",
    tabTickets: "Tickets",
    tabPayments: "Payments",
    tabBlocklist: "Blocklist",
    filterAll: "All",
    colCode: "Ticket code",
    colFare: "Fare",
    colKind: "Kind",
    colRider: "Category",
    colStatus: "Status",
    colPrice: "Price",
    colValidity: "Validity",
    colBalance: "Balance",
    validityOpen: "Open-ended",
    noToken: "The QR token is not stored in the system — only its hash.",
    refund: "Refund",
    refundTitle: "Manual refund",
    refundLead:
      "An operator refund accepts even an already used ticket. The action is attributed and recorded in the audit log.",
    fieldRefundReason: "Refund grounds",
    fieldRefundReasonHint: "Required: a refund without a reason cannot be contested.",
    refundDone: "Refund recorded",
    paymentsTitle: "Payment feed",
    paymentsLead:
      "Purchases and top-ups. No card data here — there is none in the model either.",
    colPaymentKind: "Purpose",
    colAmount: "Amount",
    colProvider: "Provider",
    colTicket: "Ticket",
    colFailure: "Decline reason",
    colCreated: "Date",
    noTicket: "No ticket issued",
    blocklistTitle: "Blocklist",
    blocklistLead:
      "A block applies immediately: the subject's tickets move to “Blocked”.",
    blocklistAdd: "Block a subject",
    blocklistRemove: "Unblock",
    blocklistRemoveHint:
      "Unblocking allows buying new tickets but does not restore already blocked ones: their tokens are compromised.",
    colSubjectType: "Subject type",
    colSubject: "Subject",
    colReason: "Grounds",
    colBlockedBy: "Blocked by",
    fieldSubjectType: "Subject type",
    fieldSubjectValue: "Subject value",
    fieldSubjectValueHint: "Ticket code, token, or rider identifier.",
    fieldBlockReason: "Blocking grounds",
    fieldBlockReasonHint: "Required: a block without a reason cannot be lifted on merit.",
    tokenHashNotice:
      "For the “Token” type you enter the token itself — the service stores only its SHA-256; the plaintext token reaches neither the database nor the audit log.",
    blockDone: "Subject blocked",
    unblockDone: "Block lifted",
    kinds: { single: "Single ride", pass: "Pass" },
    statuses: {
      issued: "Issued",
      active: "Active",
      used: "Used",
      expired: "Expired",
      refunded: "Refunded",
      blocked: "Blocked",
    },
    paymentKinds: { purchase: "Purchase", topup: "Top-up" },
    paymentStatuses: {
      pending: "Pending",
      authorized: "Authorized",
      captured: "Captured",
      failed: "Declined",
      refunded: "Refunded",
    },
    subjectTypes: { ticket: "Ticket", token: "Token", rider: "Rider" },
  },
  webhooks: {
    title: "Integrations",
    lead: "Webhook subscribers, secrets, limits and the delivery queue.",
    tabSubscribers: "Subscribers",
    tabDeliveries: "Delivery queue",
    subscribersTitle: "Webhook subscribers",
    subscribersLead:
      "External addresses that receive network data. The secret is never shown in the list.",
    colName: "Name",
    colUrl: "Endpoint",
    colEvents: "Events",
    colState: "State",
    colRateLimit: "Limit",
    colFingerprint: "Secret fingerprint",
    perMinute: "per minute",
    createTitle: "New subscriber",
    editTitle: "Edit subscriber",
    fieldCode: "Code",
    fieldCodeHint: "A-Z, a-z, 0-9, _ and -; cannot be changed later.",
    fieldName: "Name",
    fieldUrl: "Delivery endpoint",
    fieldUrlHint: "http:// or https://",
    fieldEvents: "Event types",
    fieldEventsHint: "At least one type.",
    fieldActive: "Subscription is active",
    fieldRateLimit: "Request limit (per minute)",
    fieldRateLimitHint: "Empty — the service default.",
    rotate: "Rotate secret",
    rotateTitle: "Secret rotation",
    rotateWarning:
      "The old secret stops working immediately: the subscriber will reject deliveries until it updates the key on its side.",
    rotateConfirm: "Generate a new secret",
    secretTitle: "Secret — this once only",
    secretOnceWarning:
      "Copy it now: this is the only time the secret is shown. It cannot be retrieved again — only rotated.",
    secretLead: "Hand it to the integrator and do not keep a copy.",
    secretLabel: "Signing secret",
    secretCopy: "Copy",
    secretCopied: "Copied",
    secretCopyFailed: "Copy failed — select it manually",
    secretAck: "I have saved the secret",
    fingerprintHint:
      "The fingerprint is the first 8 characters of the hash: it lets you verify “is this the right key” after rotation without handing out the key.",
    superadminOnly:
      "Managing subscribers is superadmin-only; the delivery queue belongs to the duty operator.",
    deliveriesTitle: "Delivery queue",
    deliveriesLead:
      "Webhook deliveries: reason, attempt count and next attempt time.",
    dlqTitle: "DLQ — needs intervention",
    dlqLead:
      "Status “Dead”: automatic retries are exhausted. These deliveries will not move on their own.",
    dlqEmpty: "The DLQ is empty",
    filterAttention: "Needs attention",
    colEvent: "Event",
    colSubject: "Object",
    colSubscriber: "Subscriber",
    colAttempts: "Attempts",
    colResponse: "Response",
    colError: "Failure reason",
    colNextAttempt: "Next attempt",
    colTrace: "Trace",
    retryDue: "now",
    retry: "Retry",
    retryDone: "Delivery returned to the queue",
    notRetryable: "Retry unavailable",
    eventTypes: {
      alert_published: "Alert published",
      alert_cleared: "Alert cleared",
      incident_opened: "Incident opened",
      incident_resolved: "Incident resolved",
      station_status_changed: "Station status changed",
      schedule_changed: "Schedule changed",
      train_delayed: "Train delayed",
    },
    deliveryStatuses: {
      pending: "Queued",
      sent: "Delivered",
      failed: "Failed",
      dead: "Dead (DLQ)",
    },
  },
  agents: {
    title: "AI agents",
    lead: "Operational model registry and readiness briefing for the metro platform.",
    postureTitle: "Readiness posture",
    fieldProvider: "Provider",
    fieldModelClass: "Model class",
    fieldCapabilities: "Capabilities",
    errorTitle: "AI briefing is unavailable",
    empty: "No AI briefing data.",
    chatOpen: "Chat",
    chatClose: "Close chat",
    chatPlaceholder: "Ask the agent…",
    chatInputLabel: "Ask the agent",
    chatSend: "Send",
    chatSending: "Sending…",
    chatSources: "Sources",
    postures: {
      ready: "Ready",
      pilot_ready_with_security_gap: "Pilot-ready with a security gap",
    },
    statuses: {
      ready: "Ready",
      watching: "Watching",
      monitoring: "Monitoring",
      needs_data: "Needs data",
      needs_schedule: "Needs schedule",
      needs_content: "Needs content",
      needs_hardening: "Needs hardening",
    },
  },
  users: {
    title: "Console operators",
    lead: "Accounts and access roles for the operations console.",
    colUsername: "Username",
    colDisplayName: "Name",
    colRole: "Role",
    colStatus: "Status",
    colLastLogin: "Last sign-in",
    statusActive: "Active",
    statusInactive: "Disabled",
    neverLoggedIn: "Never",
    youBadge: "you",
    createTitle: "New operator",
    editTitle: "Edit operator",
    fieldUsername: "Username",
    fieldUsernameHint: "Latin letters, digits, . _ - ; cannot be changed later.",
    fieldDisplayName: "Display name",
    fieldPassword: "Password",
    fieldPasswordHintCreate: "At least 12 characters.",
    fieldPasswordHintEdit: "Leave empty to keep the current password.",
    fieldRole: "Role",
    fieldActive: "Account is active",
    roleHintViewer: "Read-only: records, analytics, audit.",
    roleHintOperator: "Requests, incidents, service alerts.",
    roleHintEditor: "Content and reference data: news, fares, stations, lines.",
    roleHintSuperadmin: "Full access, including operator management.",
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
    errUnavailable: "Server unavailable. Please try again later.",
    errLocked: "Too many login attempts. The account is temporarily locked, please try again later.",
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
  geo: {
    regionNetwork: "Metro network map",
    regionLineEditor: "Map for drawing the line path",
    regionStationEditor: "Map for placing the station point",
    mapLoading: "Loading the map…",
    zoomIn: "Zoom in",
    zoomOut: "Zoom out",
    drawPathTitle: "Draw on the map",
    drawPathHint:
      "Click to add a point. Drag a point to move it; the grey point between vertices inserts a point in the middle.",
    undoPoint: "Undo last point",
    clearPath: "Clear path",
    deletePoint: "Delete selected point",
    pointsCount: "Points",
    noPointSelected: "No point selected",
    selectedPoint: "Selected point",
    pickPointTitle: "Place on the map",
    pickPointHint: "Click to place the station point. Drag it to fine-tune.",
    clearPoint: "Clear point",
    livePointAdded: "Point added",
    livePointMoved: "Point moved",
    livePointRemoved: "Point removed",
    livePointInserted: "Point inserted in the middle",
    livePathCleared: "Path cleared",
    livePointSet: "Coordinates set",
    manualTitle: "Manual entry",
    manualHint:
      "Keyboard and fallback path: the map is unavailable without network or a mouse.",
    multiSegmentTitle: "Multi-segment path — map editing is locked",
    multiSegmentText:
      "This line has several segments (branches) in the database, but the \"path\" format carries a single line — saving would collapse the segments into one. The geometry stays unchanged; use the GeoJSON import to edit it.",
    multiSegmentSegments: "Segments",
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
  pager: {
    label: "Pagination",
    prev: "Previous",
    next: "Next",
    page: "Page",
    pageSize: "Per page",
  },
  linesTitle: "Lines",
  stationsTitle: "Stations",
  alertsTitle: "Active alerts",
  newsTitle: "News",
  auditTitle: "Audit log",
  operations: {
    importsTitle: "Data imports",
    importsLead:
      "Upload the network as GeoJSON, GTFS or CSV and monitor the outcome of every import job.",
    uploadTitle: "Upload network data",
    sourceName: "Source name",
    sourceHint: "File name or source system",
    fieldFormat: "Import kind",
    fieldFormatHint: "The kind determines the import route and what actually changes.",
    formats: { geojson: "GeoJSON", gtfs: "GTFS", csv: "CSV" },
    kinds: {
      geojson: "GeoJSON — network",
      gtfs: "GTFS — network",
      "gtfs-fares": "GTFS Fares v2 — fares",
      csv: "CSV — network",
    },
    kindHints: {
      geojson: "A FeatureCollection of lines and stations.",
      gtfs: "Feed ZIP archive; only metro routes (route_type=1) are imported.",
      "gtfs-fares":
        "Feed ZIP archive with fare_products.txt; imported into the fare catalogue, the network is untouched.",
      csv: "UTF-8: header + rows; the entity column is line or station.",
    },
    fileByFormat: {
      geojson: "GeoJSON file",
      gtfs: "GTFS feed (ZIP)",
      "gtfs-fares": "GTFS Fares feed (ZIP)",
      csv: "CSV file",
    },
    chooseFileByFormat: {
      geojson: "Choose a GeoJSON file",
      gtfs: "Choose a GTFS ZIP archive",
      "gtfs-fares": "Choose a fares ZIP archive",
      csv: "Choose a CSV file",
    },
    types: {
      network_geojson: "Network (GeoJSON)",
      network_gtfs: "Network (GTFS)",
      network_csv: "Network (CSV)",
      fare_gtfs: "Fares (GTFS Fares)",
    },
    colType: "What was imported",
    fieldFeedLang: "Feed language",
    fieldFeedLangHint: "GTFS is monolingual; other languages are filled in with a warning.",
    feedLangAuto: "Automatic (agency.txt)",
    fieldTargetStatus: "Object status",
    fieldTargetStatusHint: "GTFS carries no lifecycle status — it is set here.",
    fieldRiderCategories: "Rider category mapping",
    fieldRiderCategoriesHint:
      "Format: RC_ADULT:adult,RC_KID:child. An unknown category fails that row instead of being silently coerced.",
    fieldFareActive: "Publish fares",
    fieldFareActiveHint:
      "GTFS carries no publication flag. Empty — a new product is created inactive, an existing one keeps its flag.",
    fareActiveAuto: "Leave unchanged (default)",
    fareActiveYes: "Activate",
    fareActiveNo: "Deactivate",
    faresGapsLead:
      "Validity (validity_minutes) and fare media (fare_media) do not exist in GTFS Fares v2: they are not imported, and every such case is reported as a warning.",
    colFormat: "Format",
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
    errorsTitle: "Import report",
    noErrors: "No records for this import",
    warningsTitle: "Missing translations",
    warningsLead:
      "Not a failure: GTFS is monolingual, so missing languages were filled in with the feed language. This is the list of what needs translating.",
    noWarnings: "No warnings",
    errorsOnlyTitle: "Errors",
    noHardErrors: "No errors",
    severities: { error: "Error", warning: "Warning" },
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
