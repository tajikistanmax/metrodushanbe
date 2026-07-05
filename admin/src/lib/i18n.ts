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
    lines: string;
    stations: string;
    alerts: string;
    news: string;
  };

  /** Обзорная страница. */
  overviewTitle: string;
  overviewLead: string;
  countLines: string;
  countStations: string;
  countAlerts: string;
  countNews: string;

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
    lines: "Хатҳо",
    stations: "Истгоҳҳо",
    alerts: "Огоҳиҳо",
    news: "Хабарҳо",
  },
  overviewTitle: "Шарҳи умумӣ",
  overviewLead: "Ҳолати ҷории шабака аз рӯи API-и оммавӣ.",
  countLines: "Хатҳо",
  countStations: "Истгоҳҳо",
  countAlerts: "Огоҳиҳои фаъол",
  countNews: "Хабарҳо",
  loading: "Бор шуда истодааст…",
  loadError: "Маълумот бор нашуд",
  loadErrorHint: "Санҷед, ки backend дар http://localhost:8080 фаъол аст.",
  empty: "Сабт нест",
  total: "Ҳамагӣ",
  linesTitle: "Хатҳо",
  stationsTitle: "Истгоҳҳо",
  alertsTitle: "Огоҳиҳои фаъол",
  newsTitle: "Хабарҳо",
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
    lines: "Линии",
    stations: "Станции",
    alerts: "Уведомления",
    news: "Новости",
  },
  overviewTitle: "Обзор",
  overviewLead: "Текущее состояние сети по данным публичного API.",
  countLines: "Линии",
  countStations: "Станции",
  countAlerts: "Активные уведомления",
  countNews: "Новости",
  loading: "Загрузка…",
  loadError: "Не удалось загрузить данные",
  loadErrorHint: "Проверьте, что backend запущен на http://localhost:8080.",
  empty: "Нет записей",
  total: "Всего",
  linesTitle: "Линии",
  stationsTitle: "Станции",
  alertsTitle: "Активные уведомления",
  newsTitle: "Новости",
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
    lines: "Lines",
    stations: "Stations",
    alerts: "Alerts",
    news: "News",
  },
  overviewTitle: "Overview",
  overviewLead: "Current network status from the public API.",
  countLines: "Lines",
  countStations: "Stations",
  countAlerts: "Active alerts",
  countNews: "News",
  loading: "Loading…",
  loadError: "Failed to load data",
  loadErrorHint: "Make sure the backend is running at http://localhost:8080.",
  empty: "No records",
  total: "Total",
  linesTitle: "Lines",
  stationsTitle: "Stations",
  alertsTitle: "Active alerts",
  newsTitle: "News",
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
export function formatDateTime(iso: string | null | undefined, lang: Lang): string {
  if (!iso) {
    return "";
  }
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) {
    return "";
  }
  const locale = lang === "tg" ? "tg-TJ" : lang === "ru" ? "ru-RU" : "en-GB";
  return new Intl.DateTimeFormat(locale, {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(d);
}
