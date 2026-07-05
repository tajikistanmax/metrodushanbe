"use client";

/**
 * Доступное модальное окно (WCAG 2.2) на нативном <dialog>: браузер сам держит
 * фокус-ловушку, закрытие по Esc и inert-фон. Заголовок связан через
 * aria-labelledby; крестик имеет текстовую aria-label. Backdrop кликом закрывает.
 */

import { useEffect, useId, useRef, type ReactNode } from "react";
import { useI18n } from "../I18nProvider";

type ModalProps = {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  /** Заблокировать закрытие (во время сохранения). */
  busy?: boolean;
};

export default function Modal({
  open,
  onClose,
  title,
  children,
  busy = false,
}: ModalProps) {
  const { dict } = useI18n();
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    if (open && !el.open) {
      el.showModal();
    } else if (!open && el.open) {
      el.close();
    }
  }, [open]);

  if (!open) return null;

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      className="m-auto w-[min(40rem,92vw)] rounded-2xl border border-[var(--card-border)] bg-[var(--card-bg)] p-0 text-[var(--text-primary)] shadow-[var(--shadow-card)] backdrop:bg-black/50"
      onCancel={(e) => {
        e.preventDefault();
        if (!busy) onClose();
      }}
      onClick={(e) => {
        // Клик по backdrop (сам <dialog>, не по контенту) — закрыть.
        if (e.target === ref.current && !busy) onClose();
      }}
    >
      <div className="flex items-center justify-between gap-3 border-b border-[var(--card-border)] px-5 py-4">
        <h2 id={titleId} className="text-lg font-extrabold tracking-tight">
          {title}
        </h2>
        <button
          type="button"
          onClick={onClose}
          disabled={busy}
          aria-label={dict.actions.close}
          className="rounded-lg px-2 py-1 text-xl leading-none text-text-secondary transition-colors hover:bg-[var(--table-row-hover)] disabled:opacity-50"
        >
          <span aria-hidden="true">×</span>
        </button>
      </div>
      <div className="max-h-[75vh] overflow-y-auto px-5 py-4">{children}</div>
    </dialog>
  );
}
