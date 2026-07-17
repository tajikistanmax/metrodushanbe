/**
 * Реэкспорт общего ThemeProvider (источник: packages/design/shared/ThemeProvider.tsx,
 * разложен в @/shared/ThemeProvider скриптом packages/design/sync.mjs).
 *
 * Файл оставлен ради обратной совместимости: на «@/components/ThemeProvider»
 * ссылается уже написанный код. Новый код импортирует из «@/shared/ThemeProvider».
 */

export {
  ThemeProvider,
  useTheme,
  THEME_STORAGE_KEY,
  type ThemeMode,
  type ResolvedTheme,
} from "@/shared/ThemeProvider";
