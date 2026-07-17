"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { setFeatureFlag } from "@/lib/admin-actions";
import { formatDateTime } from "@/lib/i18n";
import type { FeatureFlag } from "@/lib/types";
import DataTable, { type Column } from "../DataTable";
import { useI18n } from "../I18nProvider";
import StateNotice from "../StateNotice";
import { useToast } from "../admin/ToastProvider";

type FeatureFlagsManagerProps = {
  data: FeatureFlag[] | null;
  error: string | null;
};

export default function FeatureFlagsManager({
  data,
  error,
}: FeatureFlagsManagerProps) {
  const { lang, dict } = useI18n();
  const router = useRouter();
  const toast = useToast();
  const [busyKey, setBusyKey] = useState<string | null>(null);

  async function toggle(flag: FeatureFlag) {
    setBusyKey(flag.flagKey);
    const result = await setFeatureFlag(flag.flagKey, !flag.enabled);
    setBusyKey(null);
    if (!result.ok) {
      toast.error(`${result.error.message} (${result.error.code})`);
      return;
    }
    toast.success(dict.toast.updated);
    router.refresh();
  }

  if (error) return <StateNotice kind="error" detail={error} />;
  if (!data || data.length === 0) return <StateNotice kind="empty" />;

  const rows = [...data].sort((a, b) => a.flagKey.localeCompare(b.flagKey));
  const columns: Column<FeatureFlag>[] = [
    {
      key: "flag",
      header: dict.operations.flag,
      rowHeader: true,
      cell: (flag) => <span className="font-mono text-xs">{flag.flagKey}</span>,
    },
    {
      key: "description",
      header: dict.operations.description,
      cell: (flag) => flag.description || dict.none,
    },
    {
      key: "state",
      header: dict.operations.state,
      cell: (flag) => (
        <span
          className={`rounded-full px-2.5 py-1 text-xs font-bold ${
            flag.enabled
              ? "bg-brand-green/15 text-brand-green"
              : "bg-[var(--table-head-bg)] text-text-secondary"
          }`}
        >
          {flag.enabled ? dict.operations.enabled : dict.operations.disabled}
        </span>
      ),
    },
    {
      key: "updatedAt",
      header: dict.operations.updatedAt,
      cell: (flag) => formatDateTime(flag.updatedAt, lang),
    },
    {
      key: "updatedBy",
      header: dict.operations.updatedBy,
      cell: (flag) => flag.updatedBy || dict.none,
    },
    {
      key: "actions",
      header: dict.colActions,
      align: "right",
      cell: (flag) => (
        <button
          type="button"
          onClick={() => toggle(flag)}
          disabled={busyKey !== null}
          aria-pressed={flag.enabled}
          aria-label={`${dict.operations.toggle}: ${flag.flagKey}`}
          className={`min-w-24 rounded-lg px-3 py-1.5 text-xs font-bold transition-colors disabled:opacity-50 ${
            flag.enabled
              ? "border border-brand-red/30 text-brand-red hover:bg-brand-red/10"
              : "bg-brand-green/15 text-brand-green hover:bg-brand-green/25"
          }`}
        >
          {busyKey === flag.flagKey
            ? dict.actions.saving
            : flag.enabled
              ? dict.operations.disable
              : dict.operations.enable}
        </button>
      ),
    },
  ];

  return (
    <DataTable
      caption={dict.operations.featuresTitle}
      columns={columns}
      rows={rows}
      rowKey={(flag) => flag.flagKey}
      totalLabel={`${dict.total}: ${rows.length}`}
    />
  );
}
