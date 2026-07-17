/**
 * Реэкспорт общего I18nProvider (источник: packages/design/shared/I18nProvider.tsx,
 * разложен в @/shared/I18nProvider скриптом packages/design/sync.mjs).
 *
 * Файл оставлен ради обратной совместимости: на «@/components/I18nProvider»
 * ссылается уже написанный код. Новый код импортирует из «@/shared/I18nProvider».
 */

export { I18nProvider, useI18n } from "@/shared/I18nProvider";
