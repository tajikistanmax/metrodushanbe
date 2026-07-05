"use client";

/**
 * Управляющий раздел «Линии»: тулбар «Добавить», read-таблица с колонкой
 * действий, модальные форма и подтверждение удаления. Мутации — через серверные
 * действия (ключ остаётся на сервере); после успеха — router.refresh() + тост.
 */

import { useState } from "react";
import { useRouter } from "next/navigation";
import type { Line } from "@/lib/types";
import type { ActionError } from "@/lib/admin-forms";
import { deleteLine } from "@/lib/admin-actions";
import { useI18n } from "../I18nProvider";
import LinesTable from "../LinesTable";
import Toolbar from "./Toolbar";
import RowActions from "./RowActions";
import Modal from "./Modal";
import ConfirmDialog from "./ConfirmDialog";
import LineForm from "./LineForm";
import { useToast } from "./ToastProvider";

type Props = { data: Line[] | null; error: string | null };

// undefined — форма закрыта; null — создание; Line — редактирование.
type FormState = Line | null | undefined;

export default function LinesManager({ data, error }: Props) {
  const { dict } = useI18n();
  const router = useRouter();
  const toast = useToast();

  const [form, setForm] = useState<FormState>(undefined);
  const [toDelete, setToDelete] = useState<Line | null>(null);
  const [busy, setBusy] = useState(false);
  const [delError, setDelError] = useState<ActionError | null>(null);

  const formOpen = form !== undefined;

  function handleSuccess(verb: "created" | "updated") {
    setForm(undefined);
    toast.success(verb === "created" ? dict.toast.created : dict.toast.updated);
    router.refresh();
  }

  async function confirmDelete() {
    if (!toDelete) return;
    setBusy(true);
    setDelError(null);
    const r = await deleteLine(toDelete.code);
    setBusy(false);
    if (r.ok) {
      setToDelete(null);
      toast.success(dict.toast.deleted);
      router.refresh();
    } else {
      setDelError(r.error);
    }
  }

  return (
    <>
      <Toolbar onCreate={() => setForm(null)} />
      <LinesTable
        data={data}
        error={error}
        actions={(row) => (
          <RowActions
            entityLabel={row.code}
            busy={busy}
            onEdit={() => setForm(row)}
            onDelete={() => setToDelete(row)}
          />
        )}
      />

      <Modal
        open={formOpen}
        onClose={() => setForm(undefined)}
        title={`${form ? dict.form.editTitle : dict.form.createTitle}: ${dict.entity.line}`}
      >
        {formOpen ? (
          <LineForm
            row={form ?? null}
            onSuccess={handleSuccess}
            onCancel={() => setForm(undefined)}
          />
        ) : null}
      </Modal>

      <ConfirmDialog
        open={toDelete !== null}
        onClose={() => {
          setToDelete(null);
          setDelError(null);
        }}
        onConfirm={confirmDelete}
        target={toDelete?.code ?? ""}
        busy={busy}
        error={delError}
      />
    </>
  );
}
