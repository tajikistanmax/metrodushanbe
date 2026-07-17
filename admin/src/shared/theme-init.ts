// СГЕНЕРИРОВАНО: packages/design/sync.mjs — НЕ РЕДАКТИРОВАТЬ.
// Источник: packages/design/shared/theme-init.ts
// Изменения вносите в источник и запускайте: node packages/design/sync.mjs

/**
 * Анти-FOUC: инлайн-скрипт, выставляющий data-theme на <html> ДО гидратации,
 * чтобы тёмная тема не «мигала» светлой.
 *
 * Подключение в layout.tsx (server component):
 *   <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
 * а на <html> — suppressHydrationWarning (атрибут ставится вне React).
 *
 * Ключ хранилища обязан совпадать с THEME_STORAGE_KEY (./ThemeProvider.tsx).
 * Строка намеренно минифицирована вручную: она инлайнится в HTML на каждый
 * запрос, и её размер — часть TTFB.
 */
export const THEME_INIT_SCRIPT = `(function(){try{var m=localStorage.getItem("metro-dushanbe.theme");var d=m==="dark"||(m!=="light"&&window.matchMedia("(prefers-color-scheme: dark)").matches);document.documentElement.dataset.theme=d?"dark":"light";}catch(e){document.documentElement.dataset.theme="light";}})();`;
