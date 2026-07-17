"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import {
  createAdminUser,
  deleteAdminUser,
  updateAdminUser,
} from "@/lib/admin-actions";
import {
  ADMIN_PASSWORD_MIN_LENGTH,
  ADMIN_USER_ROLES,
  type ActionError,
  type AdminUserCreateBody,
  type AdminUserRole,
} from "@/lib/admin-forms";
import { formatDateTime } from "@/lib/i18n";
import type { AdminUserAccount } from "@/lib/types";
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
  SelectField,
  ServerError,
  TextField,
} from "../admin/fields";
import { useToast } from "../admin/ToastProvider";

type Editor = {
  /** null — создание; иначе логин редактируемого оператора. */
  originalUsername: string | null;
  body: AdminUserCreateBody;
};

type UsersManagerProps = {
  data: AdminUserAccount[] | null;
  error: string | null;
  /** Логин текущего оператора — им помечается своя строка. */
  currentUsername: string;
};

function emptyBody(): AdminUserCreateBody {
  return {
    username: "",
    displayName: "",
    password: "",
    role: "viewer",
    active: true,
  };
}

function bodyFromRow(row: AdminUserAccount): AdminUserCreateBody {
  return {
    username: row.username,
    displayName: row.displayName,
    // Пароль не приходит с backend и не показывается: пустое поле означает
    // «оставить текущий», а не «пароль пуст».
    password: "",
    role: row.role,
    active: row.active,
  };
}

export default function UsersManager({
  data,
  error,
  currentUsername,
}: UsersManagerProps) {
  const { lang, dict } = useI18n();
  const router = useRouter();
  const toast = useToast();
  const [editor, setEditor] = useState<Editor | null>(null);
  const [busy, setBusy] = useState(false);
  const [serverError, setServerError] = useState<ActionError | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<AdminUserAccount | null>(null);
  const [deleteError, setDeleteError] = useState<ActionError | null>(null);

  const roleHints: Record<AdminUserRole, string> = {
    viewer: dict.users.roleHintViewer,
    operator: dict.users.roleHintOperator,
    editor: dict.users.roleHintEditor,
    superadmin: dict.users.roleHintSuperadmin,
  };

  const patchBody = (patch: Partial<AdminUserCreateBody>) => {
    setEditor((current) =>
      current ? { ...current, body: { ...current.body, ...patch } } : current,
    );
  };

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!editor) return;

    const creating = editor.originalUsername === null;
    const password = editor.body.password.trim();
    // При создании пароль обязателен; при изменении — только если его задали.
    const passwordInvalid = creating
      ? password.length < ADMIN_PASSWORD_MIN_LENGTH
      : password.length > 0 && password.length < ADMIN_PASSWORD_MIN_LENGTH;

    if (!editor.body.displayName.trim() || (creating && !editor.body.username.trim())) {
      setServerError({ code: "form.invalid", message: dict.form.fixErrors });
      return;
    }
    if (passwordInvalid) {
      setServerError({
        code: "form.invalid",
        message: dict.users.fieldPasswordHintCreate,
      });
      return;
    }

    setBusy(true);
    setServerError(null);
    const result = creating
      ? await createAdminUser({ ...editor.body, password })
      : await updateAdminUser(editor.originalUsername as string, {
          displayName: editor.body.displayName,
          password: password.length > 0 ? password : undefined,
          role: editor.body.role,
          active: editor.body.active,
        });
    setBusy(false);
    if (!result.ok) {
      setServerError(result.error);
      return;
    }
    toast.success(creating ? dict.toast.created : dict.toast.updated);
    setEditor(null);
    router.refresh();
  };

  const confirmDelete = async () => {
    if (!deleteTarget) return;
    setBusy(true);
    setDeleteError(null);
    const result = await deleteAdminUser(deleteTarget.username);
    setBusy(false);
    if (!result.ok) {
      setDeleteError(result.error);
      return;
    }
    toast.success(dict.toast.deleted);
    setDeleteTarget(null);
    router.refresh();
  };

  const columns: Column<AdminUserAccount>[] = [
    {
      key: "username",
      header: dict.users.colUsername,
      rowHeader: true,
      cell: (row) => (
        <span className="flex items-center gap-2">
          <span className="font-mono text-xs">{row.username}</span>
          {row.username === currentUsername ? (
            <span className="rounded-full bg-info/15 px-2 py-0.5 text-[10px] font-bold text-info">
              {dict.users.youBadge}
            </span>
          ) : null}
        </span>
      ),
    },
    {
      key: "displayName",
      header: dict.users.colDisplayName,
      cell: (row) => <span className="font-semibold">{row.displayName}</span>,
    },
    {
      key: "role",
      header: dict.users.colRole,
      cell: (row) => dict.roles[row.role],
    },
    {
      key: "status",
      header: dict.users.colStatus,
      cell: (row) => (
        <span
          className={
            row.active ? "font-bold text-brand-green" : "text-text-secondary"
          }
        >
          {row.active ? dict.users.statusActive : dict.users.statusInactive}
        </span>
      ),
    },
    {
      key: "lastLogin",
      header: dict.users.colLastLogin,
      cell: (row) =>
        row.lastLoginAt
          ? formatDateTime(row.lastLoginAt, lang)
          : dict.users.neverLoggedIn,
    },
    {
      key: "actions",
      header: dict.colActions,
      align: "right",
      cell: (row) => (
        <RowActions
          entityLabel={row.username}
          busy={busy}
          onEdit={() => {
            setServerError(null);
            setEditor({ originalUsername: row.username, body: bodyFromRow(row) });
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
        createLabel={dict.users.createTitle}
        onCreate={() => {
          setServerError(null);
          setEditor({ originalUsername: null, body: emptyBody() });
        }}
      />
      {error ? (
        <StateNotice kind="error" detail={error} />
      ) : !data || data.length === 0 ? (
        <StateNotice kind="empty" />
      ) : (
        <DataTable
          caption={dict.users.title}
          columns={columns}
          rows={data}
          rowKey={(row) => row.username}
          totalLabel={`${dict.total}: ${data.length}`}
        />
      )}

      <Modal
        open={editor !== null}
        onClose={() => setEditor(null)}
        busy={busy}
        title={
          editor?.originalUsername === null
            ? dict.users.createTitle
            : dict.users.editTitle
        }
      >
        {editor ? (
          <form onSubmit={submit} className="grid gap-4">
            <TextField
              label={dict.users.fieldUsername}
              value={editor.body.username}
              onChange={(username) =>
                patchBody({ username: username.toLowerCase() })
              }
              hint={dict.users.fieldUsernameHint}
              required
              mono
              autoComplete="off"
              // Логин — стабильный идентификатор в аудите; менять его нельзя.
              disabled={busy || editor.originalUsername !== null}
            />
            <TextField
              label={dict.users.fieldDisplayName}
              value={editor.body.displayName}
              onChange={(displayName) => patchBody({ displayName })}
              required
              disabled={busy}
            />
            <TextField
              label={dict.users.fieldPassword}
              value={editor.body.password}
              onChange={(password) => patchBody({ password })}
              type="password"
              autoComplete="new-password"
              required={editor.originalUsername === null}
              hint={
                editor.originalUsername === null
                  ? dict.users.fieldPasswordHintCreate
                  : dict.users.fieldPasswordHintEdit
              }
              disabled={busy}
            />
            <SelectField
              label={dict.users.fieldRole}
              value={editor.body.role}
              onChange={(role) => patchBody({ role: role as AdminUserRole })}
              options={ADMIN_USER_ROLES.map((role) => ({
                value: role,
                label: dict.roles[role],
              }))}
              hint={roleHints[editor.body.role]}
              required
              disabled={busy}
            />
            <CheckboxField
              label={dict.users.fieldActive}
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
        target={deleteTarget?.username ?? ""}
        busy={busy}
        error={deleteError}
      />
    </div>
  );
}
