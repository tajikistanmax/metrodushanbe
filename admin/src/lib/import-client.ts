import type { ActionResult } from "./admin-forms";
import type { ImportJob, ImportKind } from "./types";

export type ImportUploadOptions = {
  sourceName: string;
  lang?: string;
  status?: string;
  riderCategories?: string;
  active?: string;
};

const CONTENT_TYPES: Record<ImportKind, string> = {
  geojson: "application/json",
  gtfs: "application/octet-stream",
  "gtfs-fares": "application/octet-stream",
  csv: "text/csv; charset=utf-8",
};

/** Uploads the original browser File to the bounded same-origin BFF endpoint. */
export async function uploadImport(
  kind: ImportKind,
  file: File,
  options: ImportUploadOptions,
): Promise<ActionResult<ImportJob>> {
  const query = new URLSearchParams({ sourceName: options.sourceName });
  if (options.lang) query.set("lang", options.lang);
  if (options.status) query.set("status", options.status);
  if (options.riderCategories) {
    query.set("riderCategories", options.riderCategories);
  }
  if (options.active) query.set("active", options.active);

  try {
    const response = await fetch(
      `/api/admin/imports/${encodeURIComponent(kind)}?${query.toString()}`,
      {
        method: "POST",
        headers: { "Content-Type": CONTENT_TYPES[kind] },
        body: file,
      },
    );
    const text = await response.text();
    const payload = safeJson(text);
    if (!response.ok) {
      const envelope = payload as
        | { error?: { code?: string; message?: string; details?: unknown } }
        | null;
      return {
        ok: false,
        error: {
          code: envelope?.error?.code ?? `http.${response.status}`,
          message:
            envelope?.error?.message ??
            `HTTP ${response.status} ${response.statusText}`.trim(),
          details: envelope?.error?.details,
        },
      };
    }
    if (!payload || typeof payload !== "object") {
      return {
        ok: false,
        error: {
          code: "import.response_invalid",
          message: "Import service returned an invalid response",
        },
      };
    }
    return { ok: true, data: payload as ImportJob };
  } catch {
    return {
      ok: false,
      error: {
        code: "import.request_failed",
        message: "Import request could not be completed",
      },
    };
  }
}

function safeJson(text: string): unknown {
  try {
    return text ? JSON.parse(text) : null;
  } catch {
    return null;
  }
}
