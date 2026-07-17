"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import {
  createFareProduct,
  deleteFareProduct,
  updateFareProduct,
} from "@/lib/admin-actions";
import {
  EMPTY_I18N,
  FARE_RIDER_CATEGORIES,
  type ActionError,
  type FareCreateBody,
  type FareRiderCategory,
} from "@/lib/admin-forms";
import { formatDateTime, pickName } from "@/lib/i18n";
import type { FareProduct } from "@/lib/types";
import DataTable, { type Column } from "../DataTable";
import { useI18n } from "../I18nProvider";
import StateNotice from "../StateNotice";
import ConfirmDialog from "../admin/ConfirmDialog";
import Modal from "../admin/Modal";
import RowActions from "../admin/RowActions";
import Toolbar from "../admin/Toolbar";
import {
  CheckboxField,
  FormActions,
  I18nField,
  SelectField,
  ServerError,
  TextField,
} from "../admin/fields";
import { useToast } from "../admin/ToastProvider";

type Editor = { originalCode: string | null; body: FareCreateBody };

function emptyBody(): FareCreateBody {
  return {
    code: "",
    name: { ...EMPTY_I18N },
    description: { ...EMPTY_I18N },
    amount: 0,
    currency: "TJS",
    riderCategory: "all",
    active: false,
  };
}

function bodyFromRow(row: FareProduct): FareCreateBody {
  return {
    code: row.code,
    name: row.name,
    description: row.description,
    amount: row.amount,
    currency: row.currency,
    riderCategory: row.riderCategory,
    validityMinutes: row.validityMinutes ?? undefined,
    active: row.active,
  };
}

export default function FaresManager({
  data,
  error,
}: {
  data: FareProduct[] | null;
  error: string | null;
}) {
  const { lang, dict } = useI18n();
  const router = useRouter();
  const toast = useToast();
  const [editor, setEditor] = useState<Editor | null>(null);
  const [busy, setBusy] = useState(false);
  const [serverError, setServerError] = useState<ActionError | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<FareProduct | null>(null);
  const [deleteError, setDeleteError] = useState<ActionError | null>(null);

  const patchBody = (patch: Partial<FareCreateBody>) => {
    setEditor((current) =>
      current ? { ...current, body: { ...current.body, ...patch } } : current,
    );
  };

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!editor) return;
    if (
      !Number.isFinite(editor.body.amount) ||
      editor.body.amount < 0 ||
      Object.values(editor.body.name).some((value) => !value.trim()) ||
      Object.values(editor.body.description).some((value) => !value.trim())
    ) {
      setServerError({ code: "form.invalid", message: dict.form.fixErrors });
      return;
    }
    setBusy(true);
    setServerError(null);
    const result =
      editor.originalCode === null
        ? await createFareProduct(editor.body)
        : await updateFareProduct(editor.originalCode, {
            name: editor.body.name,
            description: editor.body.description,
            amount: editor.body.amount,
            currency: editor.body.currency,
            riderCategory: editor.body.riderCategory,
            validityMinutes: editor.body.validityMinutes,
            active: editor.body.active,
          });
    setBusy(false);
    if (!result.ok) {
      setServerError(result.error);
      return;
    }
    toast.success(editor.originalCode === null ? dict.toast.created : dict.toast.updated);
    setEditor(null);
    router.refresh();
  };

  const confirmDelete = async () => {
    if (!deleteTarget) return;
    setBusy(true);
    setDeleteError(null);
    const result = await deleteFareProduct(deleteTarget.code);
    setBusy(false);
    if (!result.ok) {
      setDeleteError(result.error);
      return;
    }
    toast.success(dict.toast.deleted);
    setDeleteTarget(null);
    router.refresh();
  };

  const columns: Column<FareProduct>[] = [
    {
      key: "code",
      header: dict.colCode,
      rowHeader: true,
      cell: (row) => <span className="font-mono text-xs">{row.code}</span>,
    },
    {
      key: "name",
      header: dict.colName,
      cell: (row) => (
        <div className="max-w-xs">
          <p className="font-semibold">{pickName(row.name, lang)}</p>
          <p className="mt-1 line-clamp-2 text-xs text-text-secondary">
            {pickName(row.description, lang)}
          </p>
        </div>
      ),
    },
    {
      key: "price",
      header: dict.operations.price,
      cell: (row) => (
        <span className="font-bold">
          {Number(row.amount).toFixed(2)} {row.currency}
        </span>
      ),
    },
    {
      key: "category",
      header: dict.operations.riderCategory,
      cell: (row) => dict.operations.fareCategories[row.riderCategory],
    },
    {
      key: "state",
      header: dict.operations.state,
      cell: (row) => (
        <span className={row.active ? "font-bold text-brand-green" : "text-text-secondary"}>
          {row.active ? dict.operations.active : dict.operations.inactive}
        </span>
      ),
    },
    {
      key: "updated",
      header: dict.operations.updatedAt,
      cell: (row) => formatDateTime(row.updatedAt, lang),
    },
    {
      key: "actions",
      header: dict.colActions,
      align: "right",
      cell: (row) => (
        <RowActions
          entityLabel={row.code}
          busy={busy}
          onEdit={() => {
            setServerError(null);
            setEditor({ originalCode: row.code, body: bodyFromRow(row) });
          }}
          onDelete={() => {
            setDeleteError(null);
            setDeleteTarget(row);
          }}
        />
      ),
    },
  ];

  return (
    <div className="grid gap-4">
      <Toolbar
        createLabel={dict.operations.newFare}
        onCreate={() => {
          setServerError(null);
          setEditor({ originalCode: null, body: emptyBody() });
        }}
      />
      {error ? (
        <StateNotice kind="error" detail={error} />
      ) : !data || data.length === 0 ? (
        <StateNotice kind="empty" />
      ) : (
        <DataTable
          caption={dict.operations.faresTitle}
          columns={columns}
          rows={data}
          rowKey={(row) => row.code}
          totalLabel={`${dict.total}: ${data.length}`}
        />
      )}

      <Modal
        open={editor !== null}
        onClose={() => setEditor(null)}
        busy={busy}
        title={editor?.originalCode === null ? dict.operations.newFare : dict.operations.editFare}
      >
        {editor ? (
          <form onSubmit={submit} className="grid gap-4">
            <TextField
              label={dict.colCode}
              value={editor.body.code}
              onChange={(code) => patchBody({ code: code.toUpperCase() })}
              required
              mono
              disabled={busy || editor.originalCode !== null}
            />
            <I18nField
              legend={dict.colName}
              value={editor.body.name}
              onChange={(name) => patchBody({ name })}
              required
            />
            <I18nField
              legend={dict.operations.description}
              value={editor.body.description}
              onChange={(description) => patchBody({ description })}
              required
              multiline
            />
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField
                label={dict.operations.price}
                value={String(editor.body.amount)}
                onChange={(amount) => patchBody({ amount: Number(amount) })}
                required
              />
              <TextField
                label={dict.operations.currency}
                value={editor.body.currency}
                onChange={(currency) => patchBody({ currency: currency.toUpperCase() })}
                required
              />
            </div>
            <SelectField
              label={dict.operations.riderCategory}
              value={editor.body.riderCategory}
              onChange={(riderCategory) => patchBody({ riderCategory: riderCategory as FareRiderCategory })}
              options={FARE_RIDER_CATEGORIES.map((category) => ({
                value: category,
                label: dict.operations.fareCategories[category],
              }))}
              required
              disabled={busy}
            />
            <TextField
              label={dict.operations.validityMinutes}
              value={editor.body.validityMinutes === undefined ? "" : String(editor.body.validityMinutes)}
              onChange={(value) =>
                patchBody({ validityMinutes: value.trim() === "" ? undefined : Number(value) })
              }
              hint={dict.form.optional}
            />
            <CheckboxField
              label={dict.operations.active}
              checked={editor.body.active}
              onChange={(active) => patchBody({ active })}
              disabled={busy}
            />
            {serverError ? <ServerError {...serverError} /> : null}
            <FormActions onCancel={() => setEditor(null)} busy={busy} />
          </form>
        ) : null}
      </Modal>

      <ConfirmDialog
        open={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        onConfirm={confirmDelete}
        target={deleteTarget?.code ?? ""}
        busy={busy}
        error={deleteError}
      />
    </div>
  );
}
