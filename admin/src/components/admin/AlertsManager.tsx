"use client";

/**
 * Управляющий раздел «Уведомления»: создание/редактирование (draft) и публикация
 * (draft/review/approved → published, §6.2.6). Удаление контрактом не предусмотрено.
 * Публикация может быть отклонена backend (гейт языков / 4 глаза для critical) —
 * ошибка envelope показывается тостом.
 */

import { useState } from "react";
import { useRouter } from "next/navigation";
import type { Alert } from "@/lib/types";
import { publishAlert } from "@/lib/admin-actions";
import { useI18n } from "../I18nProvider";
import AlertsTable from "../AlertsTable";
import Toolbar from "./Toolbar";
import RowActions from "./RowActions";
import Modal from "./Modal";
import AlertForm from "./AlertForm";
import { useToast } from "./ToastProvider";

type Props = { data: Alert[] | null; error: string | null };

type FormState = Alert | null | undefined;

export default function AlertsManager({ data, error }: Props) {
  const { dict } = useI18n();
  const router = useRouter();
  const toast = useToast();

  const [form, setForm] = useState<FormState>(undefined);
  const [busy, setBusy] = useState(false);

  const formOpen = form !== undefined;

  function handleSuccess(verb: "created" | "updated") {
    setForm(undefined);
    toast.success(verb === "created" ? dict.toast.created : dict.toast.updated);
    router.refresh();
  }

  async function publish(code: string) {
    setBusy(true);
    const r = await publishAlert(code);
    setBusy(false);
    if (r.ok) {
      toast.success(dict.toast.published);
      router.refresh();
    } else {
      toast.error(`${r.error.message} (${r.error.code})`);
    }
  }

  return (
    <>
      <Toolbar onCreate={() => setForm(null)} />
      <AlertsTable
        data={data}
        error={error}
        actions={(row) => (
          <RowActions
            entityLabel={row.code}
            busy={busy}
            onEdit={() => setForm(row)}
            onPublish={() => publish(row.code)}
          />
        )}
      />

      <Modal
        open={formOpen}
        onClose={() => setForm(undefined)}
        title={`${form ? dict.form.editTitle : dict.form.createTitle}: ${dict.entity.alert}`}
      >
        {formOpen ? (
          <AlertForm
            row={form ?? null}
            onSuccess={handleSuccess}
            onCancel={() => setForm(undefined)}
          />
        ) : null}
      </Modal>
    </>
  );
}
