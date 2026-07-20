import { revalidatePath } from "next/cache";
import { NextResponse } from "next/server";
import { roleAtLeast } from "@/lib/auth";
import { getAdminSession } from "@/lib/server-auth";
import { createActorToken } from "@/lib/actor-token";
import {
  ADMIN_API_BASE,
  ADMIN_IMPORT_TIMEOUT_MS,
  adminApiKey,
} from "@/lib/server-config";

export const runtime = "nodejs";

const MIB = 1024 * 1024;
const LANGS = new Set(["tg", "ru", "en"]);
const STATUSES = new Set([
  "planned",
  "under_construction",
  "testing",
  "active",
]);

const IMPORTS = {
  geojson: {
    path: "/admin/imports",
    contentType: "application/json",
    maxBytes: 8 * MIB,
    fallbackSource: "admin-upload.geojson",
  },
  gtfs: {
    path: "/admin/imports/gtfs",
    contentType: "application/octet-stream",
    maxBytes: 16 * MIB,
    fallbackSource: "admin-upload.zip",
  },
  "gtfs-fares": {
    path: "/admin/imports/gtfs-fares",
    contentType: "application/octet-stream",
    maxBytes: 16 * MIB,
    fallbackSource: "admin-fares.zip",
  },
  csv: {
    path: "/admin/imports/csv",
    contentType: "text/csv; charset=utf-8",
    maxBytes: 8 * MIB,
    fallbackSource: "admin-upload.csv",
  },
} as const;

type ImportKind = keyof typeof IMPORTS;
type RouteContext = { params: Promise<{ kind: string }> };

class BodyLimitError extends Error {}

export async function POST(request: Request, context: RouteContext) {
  const requestOrigin = new URL(request.url).origin;
  if (request.headers.get("origin") !== requestOrigin) {
    return apiError(403, "import.origin_forbidden", "Cross-origin upload is forbidden");
  }

  const session = await getAdminSession();
  if (!session) {
    return apiError(401, "auth.unauthorized", "Authentication is required");
  }
  if (!roleAtLeast(session.role, "superadmin")) {
    return apiError(403, "admin.forbidden", "Superadmin role is required");
  }

  const { kind: rawKind } = await context.params;
  if (!isImportKind(rawKind)) {
    return apiError(404, "import.kind_unknown", "Unsupported import kind");
  }
  const config = IMPORTS[rawKind];
  const url = new URL(request.url);
  const validation = validateQuery(rawKind, url.searchParams);
  if (validation) {
    return apiError(400, validation.code, validation.message);
  }

  const declaredLength = request.headers.get("content-length");
  if (declaredLength !== null) {
    if (!/^\d+$/.test(declaredLength)) {
      return apiError(400, "import.length_invalid", "Invalid Content-Length");
    }
    const length = Number(declaredLength);
    if (length === 0) {
      return apiError(400, "import.file_required", "Import file is empty");
    }
    if (!Number.isSafeInteger(length) || length > config.maxBytes) {
      return apiError(413, "import.file_too_large", "Import file exceeds the size limit");
    }
  }

  let body: ArrayBuffer;
  try {
    body = await readBodyLimited(request.body, config.maxBytes);
  } catch (error) {
    if (error instanceof BodyLimitError) {
      return apiError(413, "import.file_too_large", "Import file exceeds the size limit");
    }
    return apiError(400, "import.body_invalid", "Import body could not be read");
  }
  if (body.byteLength === 0) {
    return apiError(400, "import.file_required", "Import file is empty");
  }

  const upstream = new URL(`${ADMIN_API_BASE}${config.path}`);
  copyQuery(rawKind, url.searchParams, upstream.searchParams);
  const source = safeSourceName(
    url.searchParams.get("sourceName") ?? "",
    config.fallbackSource,
  );

  // Токен актора (аудит-пункт 5): backend подтверждает имя импортёра по подписи.
  const actorToken = await createActorToken(session.username, session.accountVersion);
  try {
    const response = await fetch(upstream, {
      method: "POST",
      cache: "no-store",
      headers: {
        Accept: "application/json",
        "Content-Type": config.contentType,
        "X-Admin-Key": adminApiKey(),
        "X-Admin-Actor": session.username,
        "X-Admin-Actor-Token": actorToken,
        "X-Import-Source": source,
      },
      body,
      signal: AbortSignal.timeout(ADMIN_IMPORT_TIMEOUT_MS),
    });
    const text = await response.text();
    if (!isJson(text)) {
      return apiError(502, "import.upstream_invalid", "Import service returned an invalid response");
    }
    if (response.ok) {
      revalidatePath("/imports");
      if (rawKind === "gtfs-fares") revalidatePath("/fares");
    }
    return new Response(text, {
      status: response.status,
      headers: { "Content-Type": "application/json; charset=utf-8" },
    });
  } catch (error) {
    if (error instanceof Error && error.name === "TimeoutError") {
      return apiError(504, "import.upstream_timeout", "Import service timed out");
    }
    return apiError(502, "import.upstream_unavailable", "Import service is unavailable");
  }
}

function isImportKind(value: string): value is ImportKind {
  return Object.hasOwn(IMPORTS, value);
}

function validateQuery(kind: ImportKind, query: URLSearchParams) {
  const sourceName = query.get("sourceName") ?? "";
  if (sourceName.length > 255 || /[\r\n]/.test(sourceName)) {
    return { code: "import.source_invalid", message: "Invalid import source name" };
  }
  const lang = query.get("lang");
  if (lang && !LANGS.has(lang)) {
    return { code: "import.lang_invalid", message: "Unsupported import language" };
  }
  const status = query.get("status");
  if (kind === "gtfs" && status && !STATUSES.has(status)) {
    return { code: "import.status_invalid", message: "Unsupported target status" };
  }
  const active = query.get("active");
  if (kind === "gtfs-fares" && active && active !== "true" && active !== "false") {
    return { code: "import.active_invalid", message: "Invalid active flag" };
  }
  const riderCategories = query.get("riderCategories");
  if (kind === "gtfs-fares" && riderCategories && riderCategories.length > 2048) {
    return { code: "import.rider_categories_too_long", message: "Rider category mapping is too long" };
  }
  return null;
}

function copyQuery(kind: ImportKind, source: URLSearchParams, target: URLSearchParams) {
  const lang = source.get("lang");
  if ((kind === "gtfs" || kind === "gtfs-fares") && lang) target.set("lang", lang);
  if (kind === "gtfs") {
    const status = source.get("status");
    if (status) target.set("status", status);
  }
  if (kind === "gtfs-fares") {
    const riderCategories = source.get("riderCategories");
    const active = source.get("active");
    if (riderCategories) target.set("riderCategories", riderCategories);
    if (active) target.set("active", active);
  }
}

async function readBodyLimited(
  stream: ReadableStream<Uint8Array> | null,
  maxBytes: number,
): Promise<ArrayBuffer> {
  if (!stream) return new ArrayBuffer(0);
  const reader = stream.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > maxBytes) {
        await reader.cancel();
        throw new BodyLimitError();
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  const result = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    result.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return result.buffer;
}

function safeSourceName(value: string, fallback: string): string {
  return value.trim().replace(/[^\x20-\x7e]/g, "_").slice(0, 255) || fallback;
}

function isJson(text: string): boolean {
  try {
    JSON.parse(text);
    return true;
  } catch {
    return false;
  }
}

function apiError(status: number, code: string, message: string) {
  return NextResponse.json({ error: { code, message } }, { status });
}
