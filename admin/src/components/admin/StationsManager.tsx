"use client";

/**
 * Управляющий раздел «Станции»: тулбар, таблица с действиями, форма и
 * подтверждение удаления. Мутации — через серверные действия.
 */

import { useState } from "react";
import { useRouter } from "next/navigation";
import type { Station } from "@/lib/types";
import type { ActionError } from "@/lib/admin-forms";
import { deleteStation } from "@/lib/admin-actions";
import { useI18n } from "../I18nProvider";
import StationsTable from "../StationsTable";
import Toolbar from "./Toolbar";
import RowActions from "./RowActions";
import Modal from "./Modal";
import ConfirmDialog from "./ConfirmDialog";
import StationForm from "./StationForm";
import { useToast } from "./ToastProvider";

type Props = {
  data: Station[] | null;
  error: string | null;
  lineColors?: Record<string, string>;
};

type FormState = Station | null | undefined;

export default function StationsManager({ data, error, lineColors }: Props) {
  const { dict } = useI18n();
  const router = useRouter();
  const toast = useToast();

  const [form, setForm] = useState<FormState>(undefined);
  const [toDelete, setToDelete] = useState<Station | null>(null);
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
    const r = await deleteStation(toDelete.code);
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
      <StationsTable
        data={data}
        error={error}
        lineColors={lineColors}
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
        title={`${form ? dict.form.editTitle : dict.form.createTitle}: ${dict.entity.station}`}
      >
        {formOpen ? (
          <StationForm
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
