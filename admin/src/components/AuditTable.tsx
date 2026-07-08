"use client";

import type { AuditEvent } from "@/lib/types";
import { useI18n } from "./I18nProvider";
import DataTable, { type Column } from "./DataTable";
import StateNotice from "./StateNotice";
import { formatDateTime } from "@/lib/i18n";

type AuditTableProps = {
  data: AuditEvent[] | null;
  error: string | null;
};

export default function AuditTable({ data, error }: AuditTableProps) {
  const { lang, dict } = useI18n();

  if (error) {
    return <StateNotice kind="error" detail={error} />;
  }
  if (!data || data.length === 0) {
    return <StateNotice kind="empty" />;
  }

  const columns: Column<AuditEvent>[] = [
    {
      key: "id",
      header: "ID",
      rowHeader: true,
      cell: (e) => (
        <span className="font-mono text-xs">{e.id.slice(0, 8)}…</span>
      ),
    },
    {
      key: "action",
      header: dict.colActionType,
      cell: (e) => e.action,
    },
    {
      key: "entityType",
      header: dict.colEntityType,
      cell: (e) => e.entityType,
    },
    {
      key: "entityId",
      header: dict.colEntityCode,
      cell: (e) => e.entityId,
    },
    {
      key: "actor",
      header: dict.colPerformedBy,
      cell: (e) => e.actor,
    },
    {
      key: "at",
      header: dict.colCreatedAt,
      cell: (e) => formatDateTime(e.at, lang),
    },
  ];

  return (
    <DataTable<AuditEvent>
      caption={dict.auditTitle}
      columns={columns}
      rows={data}
      rowKey={(e) => e.id}
      totalLabel={`${dict.total}: ${data.length}`}
    />
  );
}
