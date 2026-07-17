"use client";

/**
 * Кнопки действий в строке таблицы: Изменить / Опубликовать (опц.) / Удалить.
 * Каждая кнопка — с видимой подписью (иконки не единственный сигнал, SC 1.4.1)
 * и доступным aria-label «действие + сущность» для скринридеров.
 */

import { Button } from "@/shared/ui";
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
  // Иерархия действий строки: Изменить — рядовое (secondary), Опубликовать —
  // основное (primary), Удалить — разрушительное (danger).
  // Зелёного варианта у Button нет намеренно: белый на #138a3d ≈4.4:1 (ниже
  // AA), а прежняя пара text-brand-green на bg-brand-green/15 в тёмной теме
  // давала ≈1.7:1. Смысл «Опубликовать» несёт глагол, а не цвет (SC 1.4.1).
  return (
    <div className="flex flex-wrap justify-end gap-1.5">
      <Button
        size="sm"
        onClick={onEdit}
        disabled={busy}
        aria-label={`${dict.actions.edit}: ${entityLabel}`}
      >
        {dict.actions.edit}
      </Button>
      {onPublish ? (
        <Button
          size="sm"
          variant="primary"
          onClick={onPublish}
          disabled={busy}
          aria-label={`${dict.actions.publish}: ${entityLabel}`}
        >
          {dict.actions.publish}
        </Button>
      ) : null}
      {onDelete ? (
        <Button
          size="sm"
          variant="danger"
          onClick={onDelete}
          disabled={busy}
          aria-label={`${dict.actions.delete}: ${entityLabel}`}
        >
          {dict.actions.delete}
        </Button>
      ) : null}
    </div>
  );
}
