"use client";

/**
 * Диалог подтверждения удаления (soft-delete). Использует доступный Modal;
 * основное действие — деструктивное (красная кнопка), фокус по умолчанию на
 * «Отмена» (нативный <dialog> ставит фокус на первый интерактивный элемент).
 */

import { useI18n } from "../I18nProvider";
import Modal from "./Modal";
import { ServerError } from "./fields";
import type { ActionError } from "@/lib/admin-forms";
import { Button } from "@/shared/ui";

type ConfirmDialogProps = {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  /** Идентификатор удаляемой записи (код/слаг) — для текста. */
  target: string;
  busy: boolean;
  error?: ActionError | null;
};

export default function ConfirmDialog({
  open,
  onClose,
  onConfirm,
  target,
  busy,
  error,
}: ConfirmDialogProps) {
  const { dict } = useI18n();
  return (
    <Modal
      open={open}
      onClose={onClose}
      title={dict.confirmDelete.title}
      busy={busy}
    >
      <div className="grid gap-3">
        <p className="text-small">
          {dict.confirmDelete.text}{" "}
          <span className="font-mono font-semibold">{target}</span>?
        </p>
        <p className="text-caption text-text-secondary">{dict.confirmDelete.hint}</p>
        {error ? (
          <ServerError
            code={error.code}
            message={error.message}
            details={error.details}
          />
        ) : null}
        <div className="flex justify-end gap-2 pt-1">
          <Button onClick={onClose} disabled={busy}>
            {dict.actions.cancel}
          </Button>
          {/* danger: смысл несёт глагол «Удалить», красный лишь дублирует его */}
          <Button variant="danger" onClick={onConfirm} disabled={busy}>
            {busy ? dict.actions.saving : dict.actions.delete}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
