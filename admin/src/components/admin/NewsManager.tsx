"use client";

/**
 * Управляющий раздел «Новости»: создание/редактирование (draft) и публикация
 * (гейт полноты языков BR-CMS-1, §6.2.7). Удаление контрактом не предусмотрено
 * (архивирование — BR-CMS-3, отдельная фаза).
 */

import { useState } from "react";
import { useRouter } from "next/navigation";
import type { News } from "@/lib/types";
import { publishNews } from "@/lib/admin-actions";
import { useI18n } from "../I18nProvider";
import NewsTable from "../NewsTable";
import Toolbar from "./Toolbar";
import RowActions from "./RowActions";
import Modal from "./Modal";
import NewsForm from "./NewsForm";
import { useToast } from "./ToastProvider";

type Props = { data: News[] | null; error: string | null };

type FormState = News | null | undefined;

export default function NewsManager({ data, error }: Props) {
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

  async function publish(slug: string) {
    setBusy(true);
    const r = await publishNews(slug);
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
      <NewsTable
        data={data}
        error={error}
        actions={(row) => (
          <RowActions
            entityLabel={row.slug}
            busy={busy}
            onEdit={() => setForm(row)}
            onPublish={() => publish(row.slug)}
          />
        )}
      />

      <Modal
        open={formOpen}
        onClose={() => setForm(undefined)}
        title={`${form ? dict.form.editTitle : dict.form.createTitle}: ${dict.entity.news}`}
      >
        {formOpen ? (
          <NewsForm
            row={form ?? null}
            onSuccess={handleSuccess}
            onCancel={() => setForm(undefined)}
          />
        ) : null}
      </Modal>
    </>
  );
}
