"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import {
  addToBlocklist,
  refundTicket,
  removeFromBlocklist,
} from "@/lib/admin-actions";
import {
  BLOCKLIST_SUBJECT_TYPES,
  TICKET_STATUSES,
  type ActionError,
  type BlocklistSubjectTypeInput,
} from "@/lib/admin-forms";
import { formatDateTime } from "@/lib/i18n";
import type {
  BlocklistEntry,
  Payment,
  PaymentStatus,
  Ticket,
  TicketStatus,
} from "@/lib/types";
import DataTable, { type Column } from "../DataTable";
import { useI18n } from "../I18nProvider";
import StateNotice from "../StateNotice";
import Modal from "../admin/Modal";
import Toolbar from "../admin/Toolbar";
import {
  FormActions,
  SelectField,
  ServerError,
  TextField,
  TextareaField,
} from "../admin/fields";
import { useToast } from "../admin/ToastProvider";
import { Alert, Badge, Button } from "@/shared/ui";

type Tab = "tickets" | "payments" | "blocklist";

type BlockDraft = {
  subjectType: BlocklistSubjectTypeInput;
  subjectValue: string;
  reason: string;
};

type TicketingManagerProps = {
  tickets: Ticket[] | null;
  ticketsError: string | null;
  payments: Payment[] | null;
  paymentsError: string | null;
  blocklist: BlocklistEntry[] | null;
  blocklistError: string | null;
  /** Роль operator и выше: возврат и чёрный список. */
  canOperate: boolean;
};

const TICKET_FILTERS: (TicketStatus | "all")[] = ["all", ...TICKET_STATUSES];

const TICKET_TONE: Record<
  TicketStatus,
  "neutral" | "info" | "success" | "warning" | "critical"
> = {
  issued: "info",
  active: "success",
  used: "neutral",
  expired: "neutral",
  refunded: "warning",
  blocked: "critical",
};

const PAYMENT_TONE: Record<
  PaymentStatus,
  "neutral" | "info" | "success" | "warning" | "critical"
> = {
  pending: "info",
  authorized: "info",
  captured: "success",
  failed: "critical",
  refunded: "warning",
};

function formatMoney(amount: number, currency: string): string {
  return `${amount.toFixed(2)} ${currency}`;
}

export default function TicketingManager({
  tickets,
  ticketsError,
  payments,
  paymentsError,
  blocklist,
  blocklistError,
  canOperate,
}: TicketingManagerProps) {
  const { lang, dict } = useI18n();
  const router = useRouter();
  const toast = useToast();
  const t = dict.tickets;

  const [tab, setTab] = useState<Tab>("tickets");
  const [filter, setFilter] = useState<TicketStatus | "all">("all");
  const [refundTarget, setRefundTarget] = useState<{
    ticket: Ticket;
    reason: string;
  } | null>(null);
  const [blockDraft, setBlockDraft] = useState<BlockDraft | null>(null);
  const [unblockTarget, setUnblockTarget] = useState<BlocklistEntry | null>(null);
  const [busy, setBusy] = useState(false);
  const [serverError, setServerError] = useState<ActionError | null>(null);

  const submitRefund = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!refundTarget) return;
    if (!refundTarget.reason.trim()) {
      setServerError({ code: "form.invalid", message: dict.form.fixErrors });
      return;
    }
    setBusy(true);
    setServerError(null);
    const result = await refundTicket(refundTarget.ticket.code, {
      reason: refundTarget.reason.trim(),
    });
    setBusy(false);
    if (!result.ok) {
      setServerError(result.error);
      return;
    }
    toast.success(t.refundDone);
    setRefundTarget(null);
    router.refresh();
  };

  const submitBlock = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!blockDraft) return;
    if (!blockDraft.subjectValue.trim() || !blockDraft.reason.trim()) {
      setServerError({ code: "form.invalid", message: dict.form.fixErrors });
      return;
    }
    setBusy(true);
    setServerError(null);
    const result = await addToBlocklist({
      subjectType: blockDraft.subjectType,
      subjectValue: blockDraft.subjectValue.trim(),
      reason: blockDraft.reason.trim(),
    });
    setBusy(false);
    if (!result.ok) {
      setServerError(result.error);
      return;
    }
    toast.success(t.blockDone);
    setBlockDraft(null);
    router.refresh();
  };

  const confirmUnblock = async () => {
    if (!unblockTarget) return;
    setBusy(true);
    setServerError(null);
    const result = await removeFromBlocklist(unblockTarget.code);
    setBusy(false);
    if (!result.ok) {
      setServerError(result.error);
      return;
    }
    toast.success(t.unblockDone);
    setUnblockTarget(null);
    router.refresh();
  };

  const ticketRows = (tickets ?? []).filter(
    (row) => filter === "all" || row.status === filter,
  );

  const ticketColumns: Column<Ticket>[] = [
    {
      key: "code",
      header: t.colCode,
      rowHeader: true,
      cell: (row) => (
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="font-mono text-caption">{row.code}</span>
          {/* Демо-признак — не украшение: без него оператор решит, что за
              билетом стоит настоящий платёж (см. PaymentGateway). */}
          {row.demo ? <Badge tone="warning">{t.demoBadge}</Badge> : null}
        </div>
      ),
    },
    {
      key: "fare",
      header: t.colFare,
      cell: (row) => <span className="font-mono text-caption">{row.fareProductCode}</span>,
    },
    { key: "kind", header: t.colKind, cell: (row) => t.kinds[row.kind] },
    {
      key: "rider",
      header: t.colRider,
      cell: (row) => dict.operations.fareCategories[row.riderCategory],
    },
    {
      key: "status",
      header: t.colStatus,
      cell: (row) => (
        <Badge tone={TICKET_TONE[row.status]} dot>
          {t.statuses[row.status]}
        </Badge>
      ),
    },
    {
      key: "price",
      header: t.colPrice,
      align: "right",
      cell: (row) => (
        <span className="tabular-nums">
          {formatMoney(row.priceAmount, row.priceCurrency)}
        </span>
      ),
    },
    {
      key: "validity",
      header: t.colValidity,
      cell: (row) => (
        <span className="grid gap-0.5 text-caption">
          <span>{formatDateTime(row.validFrom, lang)}</span>
          <span className="text-text-secondary">
            {row.validUntil ? formatDateTime(row.validUntil, lang) : t.validityOpen}
          </span>
        </span>
      ),
    },
    {
      key: "balance",
      header: t.colBalance,
      align: "right",
      cell: (row) =>
        row.balanceAmount === null ? (
          <span className="text-text-secondary">{dict.none}</span>
        ) : (
          <span className="tabular-nums">
            {formatMoney(row.balanceAmount, row.priceCurrency)}
          </span>
        ),
    },
    {
      key: "actions",
      header: dict.colActions,
      align: "right",
      cell: (row) => {
        if (!canOperate) {
          return <span className="text-text-secondary">{dict.none}</span>;
        }
        // allowedTransitions приходит с backend; возврат имеет смысл, только
        // если билет вообще может стать refunded.
        const refundable = row.allowedTransitions.includes("refunded");
        return (
          <Button
            size="sm"
            disabled={busy || !refundable}
            onClick={() => {
              setServerError(null);
              setRefundTarget({ ticket: row, reason: "" });
            }}
          >
            {t.refund}
          </Button>
        );
      },
    },
  ];

  const paymentColumns: Column<Payment>[] = [
    {
      key: "code",
      header: t.colCode,
      rowHeader: true,
      cell: (row) => (
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="font-mono text-caption">{row.code}</span>
          {row.demo ? <Badge tone="warning">{t.demoBadge}</Badge> : null}
        </div>
      ),
    },
    {
      key: "kind",
      header: t.colPaymentKind,
      cell: (row) => t.paymentKinds[row.kind],
    },
    {
      key: "amount",
      header: t.colAmount,
      align: "right",
      cell: (row) => (
        <span className="tabular-nums">{formatMoney(row.amount, row.currency)}</span>
      ),
    },
    {
      key: "status",
      header: t.colStatus,
      cell: (row) => (
        <Badge tone={PAYMENT_TONE[row.status]} dot>
          {t.paymentStatuses[row.status]}
        </Badge>
      ),
    },
    {
      key: "ticket",
      header: t.colTicket,
      cell: (row) =>
        row.ticketCode ? (
          <span className="font-mono text-caption">{row.ticketCode}</span>
        ) : (
          <span className="text-text-secondary">{t.noTicket}</span>
        ),
    },
    {
      key: "provider",
      header: t.colProvider,
      cell: (row) => (
        <span className="grid gap-0.5 text-caption">
          <span className="font-semibold">{row.provider}</span>
          {row.providerRef ? (
            <span className="font-mono text-text-secondary">{row.providerRef}</span>
          ) : null}
        </span>
      ),
    },
    {
      key: "failure",
      header: t.colFailure,
      cell: (row) =>
        row.failureReason ? (
          <span className="text-caption">{row.failureReason}</span>
        ) : (
          <span className="text-text-secondary">{dict.none}</span>
        ),
    },
    {
      key: "created",
      header: t.colCreated,
      cell: (row) => formatDateTime(row.createdAt, lang),
    },
  ];

  const blocklistColumns: Column<BlocklistEntry>[] = [
    {
      key: "subject",
      header: t.colSubject,
      rowHeader: true,
      cell: (row) => (
        <span className="break-all font-mono text-caption">{row.subjectCode}</span>
      ),
    },
    {
      key: "type",
      header: t.colSubjectType,
      cell: (row) => t.subjectTypes[row.subjectType],
    },
    { key: "reason", header: t.colReason, cell: (row) => row.reason },
    {
      key: "by",
      header: t.colBlockedBy,
      cell: (row) => <span className="font-mono text-caption">{row.createdBy}</span>,
    },
    {
      key: "at",
      header: t.colCreated,
      cell: (row) => formatDateTime(row.createdAt, lang),
    },
    {
      key: "actions",
      header: dict.colActions,
      align: "right",
      cell: (row) =>
        canOperate ? (
          <Button
            size="sm"
            variant="danger"
            disabled={busy}
            onClick={() => {
              setServerError(null);
              setUnblockTarget(row);
            }}
          >
            {t.blocklistRemove}
          </Button>
        ) : (
          <span className="text-text-secondary">{dict.none}</span>
        ),
    },
  ];

  const tabs: { key: Tab; label: string }[] = [
    { key: "tickets", label: t.tabTickets },
    { key: "payments", label: t.tabPayments },
    { key: "blocklist", label: t.tabBlocklist },
  ];

  return (
    <div className="grid gap-5">
      {/* Платежи имитирует DemoPaymentGateway; молчать об этом нельзя. */}
      <Alert tone="warning" label={dict.severity.warning} live={false}>
        {t.demoNotice}
      </Alert>

      <div role="tablist" aria-label={t.title} className="flex flex-wrap gap-1.5">
        {tabs.map(({ key, label }) => (
          <button
            key={key}
            type="button"
            role="tab"
            id={`tkt-tab-${key}`}
            aria-selected={tab === key}
            aria-controls={`tkt-panel-${key}`}
            onClick={() => setTab(key)}
            className={
              tab === key
                ? "rounded-control bg-brand-navy px-4 py-2 text-small font-bold text-surface-light"
                : "rounded-control border border-[var(--border-strong)] px-4 py-2 text-small font-semibold transition-colors hover:bg-[var(--surface-hover)]"
            }
          >
            {label}
          </button>
        ))}
      </div>

      {tab === "tickets" ? (
        <section
          id="tkt-panel-tickets"
          role="tabpanel"
          aria-labelledby="tkt-tab-tickets"
          className="grid gap-3"
        >
          <p className="text-small text-text-secondary">{t.noToken}</p>
          <div role="group" aria-label={t.colStatus} className="flex flex-wrap gap-1.5">
            {TICKET_FILTERS.map((value) => (
              <button
                key={value}
                type="button"
                aria-pressed={filter === value}
                onClick={() => setFilter(value)}
                className={
                  filter === value
                    ? "rounded-control bg-brand-navy px-3 py-1.5 text-caption font-bold text-surface-light"
                    : "rounded-control border border-[var(--border-strong)] px-3 py-1.5 text-caption font-semibold transition-colors hover:bg-[var(--surface-hover)]"
                }
              >
                {value === "all" ? t.filterAll : t.statuses[value]}
              </button>
            ))}
          </div>
          {ticketsError ? (
            <StateNotice kind="error" detail={ticketsError} />
          ) : ticketRows.length === 0 ? (
            <StateNotice kind="empty" />
          ) : (
            <DataTable
              caption={t.tabTickets}
              columns={ticketColumns}
              rows={ticketRows}
              rowKey={(row) => row.code}
              totalLabel={`${dict.total}: ${ticketRows.length}`}
            />
          )}
        </section>
      ) : null}

      {tab === "payments" ? (
        <section
          id="tkt-panel-payments"
          role="tabpanel"
          aria-labelledby="tkt-tab-payments"
          className="grid gap-3"
        >
          <p className="text-small text-text-secondary">{t.paymentsLead}</p>
          {paymentsError ? (
            <StateNotice kind="error" detail={paymentsError} />
          ) : !payments || payments.length === 0 ? (
            <StateNotice kind="empty" />
          ) : (
            <DataTable
              caption={t.paymentsTitle}
              columns={paymentColumns}
              rows={payments}
              rowKey={(row) => row.code}
              totalLabel={`${dict.total}: ${payments.length}`}
            />
          )}
        </section>
      ) : null}

      {tab === "blocklist" ? (
        <section
          id="tkt-panel-blocklist"
          role="tabpanel"
          aria-labelledby="tkt-tab-blocklist"
          className="grid gap-3"
        >
          <p className="text-small text-text-secondary">{t.blocklistLead}</p>
          {canOperate ? (
            <Toolbar
              createLabel={t.blocklistAdd}
              onCreate={() => {
                setServerError(null);
                setBlockDraft({
                  subjectType: "ticket",
                  subjectValue: "",
                  reason: "",
                });
              }}
            />
          ) : null}
          {blocklistError ? (
            <StateNotice kind="error" detail={blocklistError} />
          ) : !blocklist || blocklist.length === 0 ? (
            <StateNotice kind="empty" />
          ) : (
            <DataTable
              caption={t.blocklistTitle}
              columns={blocklistColumns}
              rows={blocklist}
              rowKey={(row) => row.code}
              totalLabel={`${dict.total}: ${blocklist.length}`}
            />
          )}
        </section>
      ) : null}

      {/* Ручной возврат */}
      <Modal
        open={refundTarget !== null}
        onClose={() => setRefundTarget(null)}
        busy={busy}
        title={t.refundTitle}
      >
        {refundTarget ? (
          <form onSubmit={submitRefund} className="grid gap-4">
            <p className="text-small text-text-secondary">
              <span className="font-mono text-caption">
                {refundTarget.ticket.code}
              </span>
              {" — "}
              {formatMoney(
                refundTarget.ticket.priceAmount,
                refundTarget.ticket.priceCurrency,
              )}
              {" · "}
              {t.statuses[refundTarget.ticket.status]}
            </p>
            <Alert tone="info" label={dict.severity.info} live={false}>
              {t.refundLead}
            </Alert>
            {refundTarget.ticket.demo ? (
              <Alert tone="warning" label={dict.severity.warning} live={false}>
                {t.demoHint}
              </Alert>
            ) : null}
            <TextareaField
              label={t.fieldRefundReason}
              hint={t.fieldRefundReasonHint}
              value={refundTarget.reason}
              onChange={(reason) =>
                setRefundTarget((current) =>
                  current ? { ...current, reason } : current,
                )
              }
              required
              disabled={busy}
            />
            {serverError ? <ServerError {...serverError} /> : null}
            <FormActions
              onCancel={() => setRefundTarget(null)}
              busy={busy}
              submitLabel={t.refund}
            />
          </form>
        ) : null}
      </Modal>

      {/* Блокировка субъекта */}
      <Modal
        open={blockDraft !== null}
        onClose={() => setBlockDraft(null)}
        busy={busy}
        title={t.blocklistAdd}
      >
        {blockDraft ? (
          <form onSubmit={submitBlock} className="grid gap-4">
            <SelectField
              label={t.fieldSubjectType}
              value={blockDraft.subjectType}
              onChange={(subjectType) =>
                setBlockDraft((current) =>
                  current
                    ? {
                        ...current,
                        subjectType: subjectType as BlocklistSubjectTypeInput,
                      }
                    : current,
                )
              }
              options={BLOCKLIST_SUBJECT_TYPES.map((type) => ({
                value: type,
                label: t.subjectTypes[type],
              }))}
              required
              disabled={busy}
            />
            {blockDraft.subjectType === "token" ? (
              <Alert tone="info" label={dict.severity.info}>
                {t.tokenHashNotice}
              </Alert>
            ) : null}
            <TextField
              label={t.fieldSubjectValue}
              hint={t.fieldSubjectValueHint}
              value={blockDraft.subjectValue}
              onChange={(subjectValue) =>
                setBlockDraft((current) =>
                  current ? { ...current, subjectValue } : current,
                )
              }
              mono
              required
              disabled={busy}
            />
            <TextareaField
              label={t.fieldBlockReason}
              hint={t.fieldBlockReasonHint}
              value={blockDraft.reason}
              onChange={(reason) =>
                setBlockDraft((current) =>
                  current ? { ...current, reason } : current,
                )
              }
              required
              disabled={busy}
            />
            {serverError ? <ServerError {...serverError} /> : null}
            <FormActions onCancel={() => setBlockDraft(null)} busy={busy} />
          </form>
        ) : null}
      </Modal>

      {/* Снятие блокировки */}
      <Modal
        open={unblockTarget !== null}
        onClose={() => setUnblockTarget(null)}
        busy={busy}
        title={t.blocklistRemove}
      >
        {unblockTarget ? (
          <div className="grid gap-4">
            <p className="text-small">
              {t.subjectTypes[unblockTarget.subjectType]}{" "}
              <span className="break-all font-mono text-caption font-semibold">
                {unblockTarget.subjectCode}
              </span>
            </p>
            <Alert tone="warning" label={dict.severity.warning}>
              {t.blocklistRemoveHint}
            </Alert>
            {serverError ? <ServerError {...serverError} /> : null}
            <div className="flex justify-end gap-2">
              <Button onClick={() => setUnblockTarget(null)} disabled={busy}>
                {dict.actions.cancel}
              </Button>
              <Button variant="primary" onClick={confirmUnblock} disabled={busy}>
                {busy ? dict.actions.saving : t.blocklistRemove}
              </Button>
            </div>
          </div>
        ) : null}
      </Modal>
    </div>
  );
}
