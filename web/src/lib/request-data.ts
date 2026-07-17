import { API_BASE, API_TIMEOUT_MS } from "./api";
import type {
  CitizenRequestCreateBody,
  CitizenRequestCreateResult,
  CitizenRequestPublic,
} from "./types";

async function postJson<T>(path: string, body: unknown): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), API_TIMEOUT_MS * 3);
  try {
    const response = await fetch(`${API_BASE}${path}`, {
      method: "POST",
      cache: "no-store",
      signal: controller.signal,
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify(body),
    });
    const payload = (await response.json()) as unknown;
    if (!response.ok) {
      const envelope = payload as { error?: { code?: string; message?: string } };
      throw new Error(envelope.error?.message ?? `HTTP ${response.status}`);
    }
    return payload as T;
  } finally {
    clearTimeout(timer);
  }
}

export function createCitizenRequest(
  body: CitizenRequestCreateBody,
): Promise<CitizenRequestCreateResult> {
  return postJson<CitizenRequestCreateResult>("/requests", body);
}

export function trackCitizenRequest(
  code: string,
  trackingToken: string,
): Promise<CitizenRequestPublic> {
  return postJson<CitizenRequestPublic>("/requests/track", {
    code: code.trim(),
    trackingToken: trackingToken.trim(),
  });
}
