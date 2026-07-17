/**
 * ЕДИНЫЙ ИСТОЧНИК ДИЗАЙН-ТОКЕНОВ платформы «Метро Душанбе».
 *
 * Почему генерация, а не npm-workspaces / общий каталог:
 * web/Dockerfile и admin/Dockerfile собираются с контекстом ../web и ../admin
 * (см. infra/docker-compose.full.yml). Из контекста web/ каталог packages/ или
 * admin/ НЕ ВИДЕН — `COPY . .` его не заберёт, а `npm ci` в контейнере не найдёт
 * workspace-пакет. Поэтому единственный способ, переживающий `docker build`
 * каждого приложения по отдельности, — генерация: этот файл → sync.mjs →
 * закоммиченные артефакты внутри web/ и admin/. Артефакты в git ⇒ Docker их
 * видит; Dockerfile'ы и контексты сборки менять не нужно.
 *
 * Правки токенов делаются ТОЛЬКО здесь, затем `node packages/design/sync.mjs`.
 * Проверка синхронности в CI: `node packages/design/sync.mjs --check`.
 *
 * СОГЛАШЕНИЕ ОБ ИМЕНАХ (важно, легко сломать):
 * «Сырые» токены и ключи Tailwind-темы НИКОГДА не совпадают по имени, иначе
 * в `@theme inline` получится самоссылка (`--radius-control: var(--radius-control)`).
 * Поэтому сырые токены живут в своих префиксах, а @theme на них ссылается:
 *   --corner-*    → --radius-*        (rounded-control, rounded-panel…)
 *   --type-*      → --text-*          (text-title-l, text-body…)
 *   --elevation-* → --shadow-*        (shadow-raised, shadow-overlay)
 *   --font-brand  → --font-sans
 * Это ровно тот приём, что уже применён к цветам: --brand-navy → --color-brand-navy.
 */

/** Брендовые константы (docs/dev-conventions.md §5) — одинаковы в обеих темах. */
const brand = `
  /* --- Бренд (docs/dev-conventions.md §5); регистр hex — всегда нижний --- */
  --brand-navy: #082742;
  --brand-red: #e21b2d;
  --brand-green: #138a3d;
  --surface-light: #ffffff;
  --surface-muted: #f2f5f8;
  --surface-dark: #0b1622;
  --warning: #e08600;
  --info: #0e5a8a;
`;

/**
 * Шкала отступов — 4px-сетка.
 * Обоснование: GOV.UK использует 5px-базу по историческим причинам, но здесь уже
 * стоит Tailwind, чья встроенная шкала (p-1=4px … p-24=96px) — 4px-based. Ломать
 * её ради 5px значило бы переписать каждый p-* / gap-* в ~60 файлах и потерять
 * весь встроенный tooling. Берём 4px и ограничиваем набор ступеней (нелинейный
 * ряд 4/8/12/16/24/32/48/64/96 — как у GOV.UK), чтобы изобретать шкалу было негде.
 */
const spacing = `
  /* --- Отступы: 4px-сетка, ограниченный нелинейный ряд --- */
  --space-0: 0;
  --space-1: 0.25rem;  /* 4px  — зазор иконки и подписи */
  --space-2: 0.5rem;   /* 8px  — внутренний зазор контрола */
  --space-3: 0.75rem;  /* 12px — паддинг компактного контрола */
  --space-4: 1rem;     /* 16px — базовый паддинг карточки */
  --space-5: 1.5rem;   /* 24px — между блоками */
  --space-6: 2rem;     /* 32px — между секциями */
  --space-7: 3rem;     /* 48px — крупные разделители */
  --space-8: 4rem;     /* 64px — вертикальный ритм страницы */
  --space-9: 6rem;     /* 96px — герой-блоки */
`;

/**
 * Радиусы — институциональная сдержанность.
 * Почему не rounded-2xl (16px) и не rounded-[2rem] (32px): крупное скругление —
 * язык потребительских приложений и маркетинга; оно читается как «продукт», а не
 * как «государственный реестр». GOV.UK Design System и U.S. Web Design System
 * держатся в диапазоне 0–4px: скругление там — функциональный признак
 * кликабельной поверхности, а не декор. Оставляем 4 ступени + pill ТОЛЬКО для
 * настоящих окружностей (точки-индикаторы, аватары, пульс станции).
 */
const radius = `
  /* --- Радиусы: 0/2/4/8 + pill только для окружностей --- */
  --corner-flat: 0;        /* полосы, таблицы, врезки во всю ширину */
  --corner-chip: 2px;      /* бейджи, теги, индикатор фокуса */
  --corner-control: 4px;   /* кнопки, поля ввода, селекты */
  --corner-panel: 8px;     /* карточки, модалки, поповеры */
  --corner-pill: 9999px;   /* ТОЛЬКО круги: точки, аватары, .station-pulse */
`;

/**
 * Типографика — модульная шкала.
 * Было: 65 произвольных пиксельных размеров, максимум портала 24px, <h1> 15px.
 * Стало: 9 ступеней. Низ шкалы плотный (12/14/16 — операционные данные и body),
 * верх — с шагом ≈1.25 (major third): 24 → 32 → 40 → 48. Это даёт порталу
 * настоящие заголовки: title-xl 40px и display 48px вместо прежних 24px.
 * Line-height подобран по 4px-сетке (16/20/24/28/32/40/48/56) — вертикальный
 * ритм совпадает со шкалой отступов.
 *
 * Веса — ТОЛЬКО загруженные @fontsource/montserrat: 400/600/700/800.
 * Вес 500 (font-medium) НЕ загружен: браузер синтезирует его из 400 либо просто
 * отдаёт 400 — то есть font-medium сегодня не даёт ничего, кроме иллюзии.
 * Дефолтный вес текста — 400 (см. body ниже), а не 800.
 */
const typography = `
  /* --- Типографика: 9 ступеней, line-height по 4px-сетке --- */
  --type-caption: 0.75rem;      /* 12px — надписи, подписи колонок */
  --type-small: 0.875rem;       /* 14px — вторичный текст, ячейки таблиц */
  --type-body: 1rem;            /* 16px — базовый текст */
  --type-lead: 1.1875rem;       /* 19px — вводный абзац */
  --type-title-s: 1.25rem;      /* 20px — заголовок карточки */
  --type-title-m: 1.5rem;       /* 24px — заголовок секции */
  --type-title-l: 2rem;         /* 32px — заголовок страницы */
  --type-title-xl: 2.5rem;      /* 40px — главный заголовок раздела */
  --type-display: 3rem;         /* 48px — герой-блок портала */

  --type-caption-lh: 1rem;      /* 16px */
  --type-small-lh: 1.25rem;     /* 20px */
  --type-body-lh: 1.5rem;       /* 24px */
  --type-lead-lh: 1.75rem;      /* 28px */
  --type-title-s-lh: 1.75rem;   /* 28px */
  --type-title-m-lh: 2rem;      /* 32px */
  --type-title-l-lh: 2.5rem;    /* 40px */
  --type-title-xl-lh: 3rem;     /* 48px */
  --type-display-lh: 3.5rem;    /* 56px */

  /* Веса — только загруженные начертания Montserrat (400/600/700/800).
     Соответствуют встроенным утилитам Tailwind: font-normal / font-semibold /
     font-bold / font-extrabold. font-medium (500) НЕ использовать — вес не загружен. */
  --weight-regular: 400;   /* body; ДЕФОЛТ текста */
  --weight-semibold: 600;  /* подписи, навигация, подзаголовки */
  --weight-bold: 700;      /* заголовки, кнопки */
  --weight-black: 800;     /* ТОЛЬКО display и словесный знак */

  /* Montserrat self-host; таджикские буквы (ӣ ӯ қ ғ ҳ ҷ) — субсет
     cyrillic-ext, fallback — Segoe UI (dev-conventions.md, §5) */
  --font-brand: "Montserrat", "Segoe UI", system-ui, sans-serif;

  /* Моноширинный стек для операционных данных (ID, координаты, время):
     системные шрифты, без загрузки — офлайн-принцип §8 */
  --font-data: ui-monospace, "Cascadia Mono", Consolas, "Liberation Mono", monospace;
`;

/**
 * Тени — 2 ступени вместо 5+ одноразовых.
 * Обоснование: институциональный интерфейс разделяет плоскости ГРАНИЦЕЙ, а не
 * размытием. Тень уместна только там, где элемент физически висит над контентом
 * (popup, модалка, тост) — это подсказка о слое, а не украшение. Всё остальное
 * (карточки, панели, таблицы) держится на 1px --border-subtle.
 * Прежние `0 8px 32px rgba(8,39,66,.18)` (web) и `0 6px 24px rgba(8,39,66,.1)`
 * (admin) — один и тот же смысл в двух разных значениях; сведены в --elevation-raised.
 */
const shadowLight = `
  /* --- Тени: 2 ступени; плоскости разделяет граница, а не размытие --- */
  --elevation-none: none;
  --elevation-raised: 0 1px 2px rgba(8, 39, 66, 0.08);   /* липкие панели, шапки */
  --elevation-overlay: 0 8px 24px rgba(8, 39, 66, 0.16); /* popup/модалка/тост */
`;

const shadowDark = `
  --elevation-raised: 0 1px 2px rgba(0, 0, 0, 0.4);
  --elevation-overlay: 0 8px 24px rgba(0, 0, 0, 0.5);
`;

/**
 * Единый словарь поверхностей — устраняет расхождение panel/* (web) и card/* (admin).
 * Прежние имена сохранены АЛИАСАМИ ниже, поэтому ~60 существующих файлов не ломаются.
 */
const surfacesLight = `
  /* --- Поверхности (единый словарь для web и admin) --- */
  --text-primary: var(--brand-navy);
  --text-secondary: #3a4a5a;
  --surface-page: #edf1f5;      /* фон страницы */
  --surface-raised: #ffffff;    /* карточка, панель, поле ввода */
  --surface-sunken: #f2f5f8;    /* шапка таблицы, утопленная зона */
  --surface-overlay: #ffffff;   /* popup, модалка, дропдаун */
  --surface-glass: rgba(255, 255, 255, 0.88); /* полупрозрачные панели ПОВЕРХ карты */
  --surface-chip: rgba(8, 39, 66, 0.06);      /* фон бейджа/чипа */
  --surface-hover: rgba(8, 39, 66, 0.07);        /* ховер контрола */
  --surface-hover-subtle: rgba(8, 39, 66, 0.04); /* ховер строки таблицы */
  --border-subtle: rgba(8, 39, 66, 0.12);  /* граница карточки/таблицы */
  --border-strong: rgba(8, 39, 66, 0.28);  /* граница secondary-кнопки */
`;

const surfacesDark = `
  --text-primary: #f2f5f8;
  --text-secondary: #aebfce;
  --surface-page: #0b1622;
  --surface-raised: #0f1d2e;
  --surface-sunken: rgba(242, 245, 248, 0.05);
  --surface-overlay: #0f1d2e;
  --surface-glass: rgba(15, 29, 46, 0.85);
  --surface-chip: rgba(242, 245, 248, 0.09);
  --surface-hover: rgba(242, 245, 248, 0.1);
  --surface-hover-subtle: rgba(242, 245, 248, 0.06);
  --border-subtle: rgba(242, 245, 248, 0.14);
  --border-strong: rgba(242, 245, 248, 0.32);
`;

/**
 * Статусные точки и тинты.
 * Точки — ВСПОМОГАТЕЛЬНЫЙ сигнал: цвет никогда не единственный носитель смысла
 * (WCAG 2.2 SC 1.4.1), рядом всегда текстовая подпись.
 * Тинты подобраны так, чтобы var(--text-primary) поверх них давал ≥4.5:1 в обеих
 * темах: в светлой подложка остаётся почти белой, в тёмной — почти navy.
 */
const statusLight = `
  /* --- Статусы: цвет — вспомогательный сигнал, не единственный носитель --- */
  --status-ok: #4ade80;       /* индикатор на navy-шапке (≥3:1 к navy) */
  --status-idle: #9fb3c8;     /* «загружается» на navy-шапке */
  --status-neutral: #8aa1b4;  /* planned */
  --status-retired: #6b7785;  /* decommissioned */

  /* Тинты подложек (бейдж, alert, KPI) */
  --tint-neutral: rgba(8, 39, 66, 0.06);
  --tint-info: rgba(14, 90, 138, 0.1);
  --tint-success: rgba(19, 138, 61, 0.12);
  --tint-warning: rgba(224, 134, 0, 0.12);
  --tint-critical: rgba(226, 27, 45, 0.1);
`;

const statusDark = `
  --status-ok: #4ade80;
  --status-idle: #9fb3c8;
  --status-neutral: #8aa1b4;
  --status-retired: #94a2b0;
  --tint-neutral: rgba(242, 245, 248, 0.09);
  --tint-info: rgba(64, 148, 200, 0.2);
  --tint-success: rgba(19, 138, 61, 0.24);
  --tint-warning: rgba(224, 134, 0, 0.22);
  --tint-critical: rgba(226, 27, 45, 0.22);
`;

/**
 * Индикатор фокуса. Значения не меняются — только выносятся в токены, чтобы
 * посчитанные контрасты (SC 1.4.11) не разъезжались между приложениями.
 */
const focusLight = `
  /* --- Фокус (WCAG 2.2 SC 1.4.11); контрасты посчитаны, не менять вслепую --- */
  --focus-ring: var(--info);                  /* на светлом фоне */
  --focus-ring-on-dark: var(--surface-light); /* на navy: белый ≈15:1 */
  --focus-ring-on-amber: var(--brand-navy);   /* на #e08600: navy ≈5.5:1 */
  --focus-width: 3px;
  --focus-offset: 2px;
`;

const focusDark = `
  --focus-ring: #8fc3ea; /* --info на тёмном даёт <3:1 — индикатор светлее */
`;

// --- Специфика приложений -------------------------------------------------

/** Токены только портала (карта, демо-баннер, пульс станции). */
const webExtraLight = `
  /* --- Только портал --- */
  --station-fill: #ffffff;
  --diagram-ring: var(--brand-navy);
  --pulse-color: var(--brand-red);
  --demo-bg: #fff6dc;
  --demo-text: #634500;
  --demo-border: rgba(224, 134, 0, 0.28);
`;

const webExtraDark = `
  --station-fill: #0f1d2e;
  --diagram-ring: #f2f5f8;
  --pulse-color: #ff6b7a;
  --demo-bg: #382b0c;
  --demo-text: #ffe6a2;
  --demo-border: rgba(255, 210, 92, 0.24);
`;

/** Токены только консоли (сайдбар, топбар, схема города). */
const adminExtraLight = `
  /* --- Только консоль --- */
  --sidebar-bg: var(--brand-navy);
  --sidebar-active-bg: #ffffff;
  --sidebar-active-text: var(--brand-navy);
  /* Топбар НЕПРОЗРАЧЕН. Было rgba(255,255,255,.82) в паре с backdrop-blur-md:
     липкая шапка отделялась от контента размытием. Институциональный приём —
     граница и плоскость, поэтому blur снят, а вместе с ним ушла и причина
     держать альфу: полупрозрачная шапка без размытия просто просвечивает. */
  --topbar-bg: var(--surface-raised);
  --map-panel-bg: #f5f8fb;
  --map-city-start: #f8fbfd;
  --map-city-end: #eaf1f5;
  --map-road-minor: rgba(71, 102, 126, 0.15);
  --map-road-major: #ffffff;
  --map-water: #bfe5f2;
  --map-water-core: #8ccde3;
  --map-park: #d9eddc;
  --map-label: #31536d;

  /* Обзорная карта страны (TajikistanOverviewMap): заливка суши, контур,
     реки, координатная сетка. Держатся отдельно от --map-* города: у города
     подложка — улицы и кварталы, у страны — рельеф. */
  --map-land-start: #e8f2f7;
  --map-land-mid: #dcebdc;
  --map-land-end: #bad7c2;
  --map-land-edge: #ffffff;   /* «берег»: широкая обводка контура */
  --map-outline: #2d6f70;     /* тонкая линия границы поверх обводки */
  --map-river: #74a995;
  --map-grid: #ffffff;        /* штриховка градусной сетки под clipPath */

  /*
   * Экран входа (/login) — утверждённый фото-макет (photo/…13_07_19.png).
   * Живёт ВНЕ общей темы намеренно: карточка лежит поверх ночной фотографии и
   * в тёмной теме не перекрашивается (иначе макет разваливается). Поэтому
   * токены заданы только в :root и НЕ переопределяются в [data-theme="dark"].
   * Синий --login-accent (#2563eb) — цвет CTA из макета, а не --brand-navy:
   * это единственное место, где макет расходится с институциональной палитрой.
   * Контраст: белый на #2563eb ≈ 5.2:1 (AA ✓); #2563eb на белом ≈ 5.2:1 (AA ✓).
   */
  --login-accent: #2563eb;
  --login-accent-strong: #1d4ed8;  /* hover CTA: ≈7.0:1 к белому */
  --login-accent-tint: #eaf1ff;    /* подложка иконки поля */
  --login-accent-soft: #f3f7ff;    /* hover SSO-кнопки */
  --login-accent-border: #bcd0f7;  /* граница SSO-кнопки */
  --login-hero-accent: #4d8dff;    /* акцент заголовка на фото ≈6.6:1 к #050d16 */
  --login-card-bg: #ffffff;
  --login-ink: #0b1b33;            /* заголовки карточки ≈16.5:1 к белому */
  --login-ink-soft: #3c4c60;       /* подписи ≈8.6:1 к белому */
  --login-muted: #5b6b7f;          /* вторичный текст ≈5.7:1 к белому */
  --login-placeholder: #6b7b8f;    /* плейсхолдер ≈4.6:1 к белому (AA ✓) */
  --login-field-border: #e2e8f0;
  --login-field-hover: #f1f5f9;
  --login-error-bg: #fdecee;
  --login-error-border: #f5b5bc;
  --login-error-text: #c31424;     /* ≈5.9:1 к #fdecee */
  --login-scrim: #050d16;          /* затемнение фотографии под текстом */
  --login-overlay-bg: #0d1c2c;     /* меню языка поверх фото */
`;

const adminExtraDark = `
  --sidebar-bg: #071a2b;
  --sidebar-active-bg: #f2f5f8;
  --sidebar-active-text: #082742;
  --topbar-bg: var(--surface-raised);
  --map-panel-bg: #0b1826;
  --map-city-start: #132536;
  --map-city-end: #0d1b29;
  --map-road-minor: rgba(213, 229, 240, 0.09);
  --map-road-major: rgba(213, 229, 240, 0.13);
  --map-water: #173f55;
  --map-water-core: #2d718f;
  --map-park: #173c2c;
  --map-label: #a9c2d6;

  /* Обзорная карта страны в тёмной теме: суша темнее подложки панели, контур и
     реки — светлее, иначе на #0b1826 карта сливается в пятно.
     --login-* НЕ переопределяются: карточка входа всегда светлая (см. :root). */
  --map-land-start: #1b3446;
  --map-land-mid: #1c3b3c;
  --map-land-end: #24493c;
  --map-land-edge: rgba(213, 229, 240, 0.22);
  --map-outline: #6fb3ab;
  --map-river: #4f8f79;
  --map-grid: rgba(213, 229, 240, 0.16);
`;

/**
 * АЛИАСЫ ОБРАТНОЙ СОВМЕСТИМОСТИ.
 * Старые имена продолжают работать, поэтому редизайн можно вести файл за файлом,
 * а не одним нерабочим коммитом. Удалять алиас можно только после того, как
 * `grep -r "<старое-имя>" src/` перестанет что-либо находить.
 */
const webAliases = `
  /* --- Алиасы обратной совместимости (см. packages/design/MIGRATION.md) --- */
  --page-bg: var(--surface-page);
  --map-bg: var(--surface-page);
  --panel-bg: var(--surface-glass);
  --panel-border: var(--border-subtle);
  --field-bg: var(--surface-raised);
  --popup-bg: var(--surface-overlay);
  --control-hover: var(--surface-hover);
  --shadow-card: var(--elevation-raised);
`;

/*
 * Алиасы консоли УДАЛЕНЫ ЦЕЛИКОМ: редизайн admin/ завершён, все экраны
 * переведены на канонические имена. Условие удаления из MIGRATION.md
 * выполнено — по admin/src не находится ни одного старого имени:
 *   --page-bg --card-bg --card-border --table-border --table-head-bg
 *   --table-row-hover --chip-bg --shadow-card --kpi-tint-*
 *
 * Алиасы портала (webAliases) НЕ ТРОНУТЫ: web/ мигрирует отдельно и своим
 * темпом — его редизайн идёт параллельно.
 */
const adminAliases = "";

/**
 * Проброс токенов в утилиты Tailwind v4.
 *
 * Namespace'ы v4:
 *   --color-*  → bg-* / text-* / border-*
 *   --text-*   → text-<name> (размер) + парный --text-<name>--line-height
 *   --radius-* → rounded-<name>
 *   --shadow-* → shadow-<name>
 *
 * Имена НАРОЧНО не совпадают со встроенными (sm/md/lg/xl/2xl): переопределить
 * встроенные — значит молча изменить вид ~60 существующих файлов. Новые имена
 * ролевые (rounded-control, text-title-l), поэтому старая разметка не
 * затрагивается, а новая пишется только по шкале.
 *
 * Шкалу отступов в @theme НЕ пробрасываем: встроенная шкала Tailwind уже
 * 4px-based (p-1=4px … p-24=96px) и совпадает с нашим рядом. Дублировать её
 * значило бы завести два имени для одного значения — ровно та болезнь, которую лечим.
 */
const themeCommon = `
  /* Цвета бренда */
  --color-brand-navy: var(--brand-navy);
  --color-brand-red: var(--brand-red);
  --color-brand-green: var(--brand-green);
  --color-surface-light: var(--surface-light);
  --color-surface-muted: var(--surface-muted);
  --color-surface-dark: var(--surface-dark);
  --color-text-secondary: var(--text-secondary);
  --color-warning: var(--warning);
  --color-info: var(--info);
  --color-ink: var(--text-primary);

  /* Цвета поверхностей */
  --color-surface-page: var(--surface-page);
  --color-surface-raised: var(--surface-raised);
  --color-surface-sunken: var(--surface-sunken);
  --color-surface-overlay: var(--surface-overlay);
  --color-surface-glass: var(--surface-glass);
  --color-surface-hover: var(--surface-hover);
  --color-border-subtle: var(--border-subtle);
  --color-border-strong: var(--border-strong);

  /* Статусные цвета */
  --color-status-ok: var(--status-ok);
  --color-status-idle: var(--status-idle);
  --color-status-neutral: var(--status-neutral);
  --color-status-retired: var(--status-retired);

  /* Радиусы (ролевые имена — встроенные sm/md/lg/xl не трогаем) */
  --radius-flat: var(--corner-flat);
  --radius-chip: var(--corner-chip);
  --radius-control: var(--corner-control);
  --radius-panel: var(--corner-panel);

  /* Типографическая шкала */
  --text-caption: var(--type-caption);
  --text-caption--line-height: var(--type-caption-lh);
  --text-small: var(--type-small);
  --text-small--line-height: var(--type-small-lh);
  --text-body: var(--type-body);
  --text-body--line-height: var(--type-body-lh);
  --text-lead: var(--type-lead);
  --text-lead--line-height: var(--type-lead-lh);
  --text-title-s: var(--type-title-s);
  --text-title-s--line-height: var(--type-title-s-lh);
  --text-title-m: var(--type-title-m);
  --text-title-m--line-height: var(--type-title-m-lh);
  --text-title-l: var(--type-title-l);
  --text-title-l--line-height: var(--type-title-l-lh);
  --text-title-xl: var(--type-title-xl);
  --text-title-xl--line-height: var(--type-title-xl-lh);
  --text-display: var(--type-display);
  --text-display--line-height: var(--type-display-lh);

  /* Тени */
  --shadow-raised: var(--elevation-raised);
  --shadow-overlay: var(--elevation-overlay);

  /* Шрифты */
  --font-sans: var(--font-brand);
  --font-mono: var(--font-data);
`;

const themeWeb = `
  --color-page: var(--surface-page);
`;

/*
 * У консоли своих theme-ключей не осталось: --color-page/--color-sidebar/
 * --color-card/--color-card-border порождали утилиты bg-page, bg-sidebar,
 * bg-card, border-card-border — после редизайна ни одна из них не
 * используется (поверхности берутся из общего словаря --surface-*).
 * Держать ключ ради ноля вызовов — заводить второе имя для одного значения.
 */
const themeAdmin = "";

/**
 * Общие CSS-классы, ранее продублированные байт в байт в обоих globals.css:
 * .ribbon-flag, .line-badge, .data-text, базовый :focus-visible, .skip-link,
 * блок prefers-reduced-motion.
 */
const commonRules = `
/*
 * Лента флага Республики Таджикистан (красный/белый/зелёный 2:3:2) —
 * государственная сигнатура платформы: тонкая полоса над шапкой.
 */
.ribbon-flag {
  height: 4px;
  background: linear-gradient(
    to right,
    var(--brand-red) 0 28.6%,
    var(--surface-light) 28.6% 71.4%,
    var(--brand-green) 71.4% 100%
  );
}

/*
 * Бейдж линии («Л1»/«L1»): скруглённый прямоугольник цвета линии, белый
 * текст 700. Inset-тень слегка затемняет фон (ложится ПОД текст), поднимая
 * контраст белого на брендовых цветах выше 4.5:1 (WCAG 2.2 AA):
 * #138a3d → ≈5.1:1, #e21b2d → ≈5.2:1.
 */
.line-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 30px;
  padding: 1px 7px;
  border-radius: var(--corner-control);
  color: var(--surface-light);
  font-weight: var(--weight-bold);
  font-size: var(--type-caption);
  line-height: 18px;
  letter-spacing: 0.02em;
  box-shadow: inset 0 0 0 999px rgba(8, 23, 42, 0.12);
}

/* Числа и коды в операционных данных: моно, табличные цифры */
.data-text {
  font-family: var(--font-data);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.01em;
}

/* Видимый фокус для клавиатурной навигации (WCAG 2.2 AA) */
:focus-visible {
  outline: var(--focus-width) solid var(--focus-ring);
  outline-offset: var(--focus-offset);
  border-radius: var(--corner-chip);
}

/* Skip-link: скрыт, появляется при фокусе */
.skip-link {
  position: absolute;
  left: -9999px;
  top: 0;
  z-index: 100;
  padding: var(--space-2) var(--space-4);
  background: var(--brand-navy);
  color: var(--surface-light);
  font-weight: var(--weight-semibold);
  text-decoration: none;
}

.skip-link:focus-visible {
  left: var(--space-2);
  top: var(--space-2);
  outline-color: var(--focus-ring-on-dark);
}

/* Уважение к prefers-reduced-motion: минимизируем анимации CSS */
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }

  .station-pulse {
    display: none;
  }
}
`;

/** Собирает сгенерированный CSS-блок для приложения ("web" | "admin"). */
export function renderCss(app) {
  const isWeb = app === "web";
  const extraLight = isWeb ? webExtraLight : adminExtraLight;
  const extraDark = isWeb ? webExtraDark : adminExtraDark;
  const aliases = isWeb ? webAliases : adminAliases;
  const themeExtra = isWeb ? themeWeb : themeAdmin;

  return `/*
 * Дизайн-токены бренда «Метро Душанбе» (docs/dev-conventions.md, §5).
 * Шрифт — Montserrat, self-host через @fontsource (импорт весов в layout.tsx);
 * внешние CDN/Google Fonts запрещены (офлайн-принцип, §8).
 * Тёмная тема — через [data-theme="dark"] на <html> (см. ThemeProvider).
 */
:root {
${brand}${spacing}${radius}${typography}${shadowLight}${surfacesLight}${statusLight}${focusLight}${extraLight}${aliases}}

[data-theme="dark"] {
  color-scheme: dark;
${shadowDark}${surfacesDark}${statusDark}${focusDark}${extraDark}}

/* Проброс токенов в утилиты Tailwind v4 (bg-brand-navy, text-title-l и т.д.) */
@theme inline {
${themeCommon}${themeExtra}}

body {
  background: var(--surface-page);
  color: var(--text-primary);
  font-family: var(--font-brand);
  font-weight: var(--weight-regular);
}
${commonRules}`;
}

/** Приложения, в которые синхронизируются токены и общий слой. */
export const APPS = ["web", "admin"];
