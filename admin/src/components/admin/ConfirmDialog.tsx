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
        <p className="text-sm">
          {dict.confirmDelete.text}{" "}
          <span className="font-mono font-semibold">{target}</span>?
        </p>
        <p className="text-xs text-text-secondary">{dict.confirmDelete.hint}</p>
        {error ? (
          <ServerError
            code={error.code}
            message={error.message}
            details={error.details}
          />
        ) : null}
        <div className="flex justify-end gap-2 pt-1">
          <button
            type="button"
            onClick={onClose}
            disabled={busy}
            className="rounded-lg border border-[var(--card-border)] px-4 py-2 text-sm font-semibold transition-colors hover:bg-[var(--table-row-hover)] disabled:opacity-50"
          >
            {dict.actions.cancel}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={busy}
            className="rounded-lg bg-brand-red px-4 py-2 text-sm font-bold text-surface-light transition-opacity hover:opacity-90 disabled:opacity-50"
          >
            {busy ? dict.actions.saving : dict.actions.delete}
          </button>
        </div>
      </div>
    </Modal>
  );
}
