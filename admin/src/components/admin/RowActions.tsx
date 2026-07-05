"use client";

/**
 * Кнопки действий в строке таблицы: Изменить / Опубликовать (опц.) / Удалить.
 * Каждая кнопка — с видимой подписью (иконки не единственный сигнал, SC 1.4.1)
 * и доступным aria-label «действие + сущность» для скринридеров.
 */

import { useI18n } from "../I18nProvider";

type RowActionsProps = {
  entityLabel: string;
  onEdit: () => void;
  onDelete?: () => void;
  onPublish?: () => void;
  busy?: boolean;
};

export default function RowActions({
  entityLabel,
  onEdit,
  onDelete,
  onPublish,
  busy = false,
}: RowActionsProps) {
  const { dict } = useI18n();
  const btn =
    "rounded-lg px-2.5 py-1 text-xs font-semibold transition-colors disabled:opacity-50";
  return (
    <div className="flex flex-wrap justify-end gap-1.5">
      <button
        type="button"
        onClick={onEdit}
        disabled={busy}
        aria-label={`${dict.actions.edit}: ${entityLabel}`}
        className={`${btn} border border-[var(--card-border)] hover:bg-[var(--table-row-hover)]`}
      >
        {dict.actions.edit}
      </button>
      {onPublish ? (
        <button
          type="button"
          onClick={onPublish}
          disabled={busy}
          aria-label={`${dict.actions.publish}: ${entityLabel}`}
          className={`${btn} bg-brand-green/15 text-brand-green hover:bg-brand-green/25`}
        >
          {dict.actions.publish}
        </button>
      ) : null}
      {onDelete ? (
        <button
          type="button"
          onClick={onDelete}
          disabled={busy}
          aria-label={`${dict.actions.delete}: ${entityLabel}`}
          className={`${btn} text-brand-red hover:bg-brand-red/10`}
        >
          {dict.actions.delete}
        </button>
      ) : null}
    </div>
  );
}
