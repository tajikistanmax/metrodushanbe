"use client";

import { useEffect, useMemo, useState } from "react";
import { formatDushanbeDateTime } from "@/lib/date-time";
import { pickName } from "@/lib/i18n";
import { loadNetworkData } from "@/lib/network-data";
import { createCitizenRequest, trackCitizenRequest } from "@/lib/request-data";
import {
  isLineFeature,
  isStationFeature,
  type CitizenRequestCreateBody,
  type CitizenRequestCreateResult,
  type CitizenRequestPublic,
  type CitizenRequestStatus,
  type CitizenRequestType,
  type NetworkGeoJson,
} from "@/lib/types";
import Header from "./Header";
import { useI18n } from "./I18nProvider";

const MAIN_ID = "requests-content";
const REQUEST_TYPES: CitizenRequestType[] = [
  "complaint",
  "suggestion",
  "incident",
  "question",
  "lost_item",
];

const fieldClass =
  "mt-1 h-11 w-full rounded-xl border border-[var(--panel-border)] bg-[var(--field-bg)] px-3 text-sm text-[var(--text-primary)]";
const labelClass =
  "block text-xs font-bold uppercase tracking-wide text-text-secondary";

function optional(value: FormDataEntryValue | null): string | undefined {
  const trimmed = String(value ?? "").trim();
  return trimmed === "" ? undefined : trimmed;
}

function tokenStorageKey(code: string): string {
  return `metro-dushanbe.request.${code.trim().toUpperCase()}`;
}

function StatusBadge({ status }: { status: CitizenRequestStatus }) {
  const { dict } = useI18n();
  const colors: Record<CitizenRequestStatus, string> = {
    new: "bg-info/15 text-info",
    in_progress: "bg-warning/15 text-warning",
    awaiting_info: "bg-warning/15 text-warning",
    resolved: "bg-brand-green/15 text-brand-green",
    closed: "bg-text-secondary/15 text-text-secondary",
    reopened: "bg-brand-red/15 text-brand-red",
  };
  return (
    <span className={`rounded-full px-3 py-1 text-xs font-bold ${colors[status]}`}>
      {dict.requests.statuses[status]}
    </span>
  );
}

function RequestStatusCard({ request }: { request: CitizenRequestPublic }) {
  const { lang, dict } = useI18n();
  const formatDate = (value: string) => formatDushanbeDateTime(value, lang);

  return (
    <section className="mt-5 rounded-2xl border border-[var(--panel-border)] bg-[var(--panel-bg)] p-4 shadow-[var(--shadow-card)] sm:p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-xs font-bold uppercase tracking-wide text-text-secondary">
            {dict.requests.code}
          </p>
          <p className="mt-1 font-mono text-lg font-bold">{request.code}</p>
        </div>
        <StatusBadge status={request.status} />
      </div>
      <h2 className="mt-5 text-lg font-bold">{request.subject}</h2>
      <p className="mt-1 text-sm text-text-secondary">
        {dict.requests.types[request.type]}
      </p>
      <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
        <div>
          <dt className="text-xs font-bold uppercase tracking-wide text-text-secondary">
            {dict.requests.createdAt}
          </dt>
          <dd className="mt-1">{formatDate(request.createdAt)}</dd>
        </div>
        <div>
          <dt className="text-xs font-bold uppercase tracking-wide text-text-secondary">
            {dict.requests.updatedAt}
          </dt>
          <dd className="mt-1">{formatDate(request.updatedAt)}</dd>
        </div>
      </dl>
      <div className="mt-5 rounded-xl bg-[var(--control-hover)] p-4">
        <h3 className="text-xs font-bold uppercase tracking-wide text-text-secondary">
          {dict.requests.response}
        </h3>
        <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed">
          {request.response || dict.requests.noResponse}
        </p>
      </div>
    </section>
  );
}

function SuccessCard({ result }: { result: CitizenRequestCreateResult }) {
  const { dict } = useI18n();
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(
        `${dict.requests.code}: ${result.request.code}\n${dict.requests.token}: ${result.trackingToken}`,
      );
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };

  return (
    <section role="status" className="mt-6 rounded-2xl border border-brand-green/40 bg-brand-green/10 p-5">
      <h2 className="text-xl font-extrabold">{dict.requests.successTitle}</h2>
      <p className="mt-2 text-sm leading-relaxed text-text-secondary">
        {dict.requests.successBody}
      </p>
      <dl className="mt-5 grid gap-4">
        <div>
          <dt className="text-xs font-bold uppercase tracking-wide text-text-secondary">
            {dict.requests.code}
          </dt>
          <dd className="mt-1 break-all font-mono text-lg font-bold">
            {result.request.code}
          </dd>
        </div>
        <div>
          <dt className="text-xs font-bold uppercase tracking-wide text-text-secondary">
            {dict.requests.token}
          </dt>
          <dd className="mt-1 break-all rounded-xl bg-[var(--field-bg)] p-3 font-mono text-sm">
            {result.trackingToken}
          </dd>
          <p className="mt-2 text-xs text-text-secondary">{dict.requests.tokenHint}</p>
        </div>
      </dl>
      <button
        type="button"
        onClick={copy}
        className="mt-4 rounded-xl bg-brand-navy px-4 py-2.5 text-sm font-bold text-surface-light"
      >
        {copied ? dict.requests.copied : dict.requests.copy}
      </button>
    </section>
  );
}

export default function RequestsClient() {
  const { lang, dict } = useI18n();
  const [tab, setTab] = useState<"submit" | "track">("submit");
  const [network, setNetwork] = useState<NetworkGeoJson | null>(null);
  const [lineCode, setLineCode] = useState("");
  const [submitBusy, setSubmitBusy] = useState(false);
  const [submitError, setSubmitError] = useState("");
  const [created, setCreated] = useState<CitizenRequestCreateResult | null>(null);
  const [trackCode, setTrackCode] = useState("");
  const [trackToken, setTrackToken] = useState("");
  const [trackBusy, setTrackBusy] = useState(false);
  const [trackError, setTrackError] = useState("");
  const [tracked, setTracked] = useState<CitizenRequestPublic | null>(null);

  useEffect(() => {
    let cancelled = false;
    loadNetworkData()
      .then(({ data }) => {
        if (!cancelled) setNetwork(data);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  const lines = useMemo(
    () =>
      network?.features
        .filter(isLineFeature)
        .sort((a, b) => a.properties.sort_order - b.properties.sort_order) ?? [],
    [network],
  );
  const stations = useMemo(
    () =>
      network?.features
        .filter(isStationFeature)
        .filter(
          (station) =>
            lineCode === "" || station.properties.lines.includes(lineCode),
        )
        .sort((a, b) =>
          pickName(a.properties.name, lang).localeCompare(
            pickName(b.properties.name, lang),
            lang,
          ),
        ) ?? [],
    [lang, lineCode, network],
  );

  const submitRequest = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const body: CitizenRequestCreateBody = {
      type: String(form.get("type")) as CitizenRequestType,
      subject: String(form.get("subject") ?? "").trim(),
      message: String(form.get("message") ?? "").trim(),
      contactName: optional(form.get("contactName")),
      contactEmail: optional(form.get("contactEmail")),
      contactPhone: optional(form.get("contactPhone")),
      lineCode: optional(form.get("lineCode")),
      stationCode: optional(form.get("stationCode")),
      consent: form.get("consent") === "on",
    };
    if (!body.subject || !body.message) {
      setSubmitError(dict.requests.required);
      return;
    }
    if (!body.consent) {
      setSubmitError(dict.requests.consentRequired);
      return;
    }
    setSubmitBusy(true);
    setSubmitError("");
    try {
      const result = await createCitizenRequest(body);
      setCreated(result);
      setTrackCode(result.request.code);
      setTrackToken(result.trackingToken);
      try {
        localStorage.setItem(tokenStorageKey(result.request.code), result.trackingToken);
      } catch {
        // Private browsing can disable storage; the one-time success card remains visible.
      }
    } catch {
      setSubmitError(dict.requests.error);
    } finally {
      setSubmitBusy(false);
    }
  };

  const restoreToken = (code: string) => {
    setTrackCode(code);
    if (trackToken !== "") return;
    try {
      const saved = localStorage.getItem(tokenStorageKey(code));
      if (saved) setTrackToken(saved);
    } catch {
      // Tracking remains available through manual token entry.
    }
  };

  const trackRequest = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!trackCode.trim() || !trackToken.trim()) {
      setTrackError(dict.requests.required);
      return;
    }
    setTrackBusy(true);
    setTrackError("");
    setTracked(null);
    try {
      setTracked(await trackCitizenRequest(trackCode, trackToken));
    } catch {
      setTrackError(dict.requests.error);
    } finally {
      setTrackBusy(false);
    }
  };

  return (
    <div className="flex min-h-dvh w-full flex-col">
      <a href={`#${MAIN_ID}`} className="skip-link">
        {dict.route.skipToContent}
      </a>
      <Header />
      <main id={MAIN_ID} className="mx-auto w-full max-w-3xl flex-1 px-4 py-8 sm:px-6">
        <h1 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
          {dict.requests.heading}
        </h1>
        <p className="mt-2 max-w-2xl text-sm leading-relaxed text-text-secondary">
          {dict.requests.intro}
        </p>

        <div className="mt-6 inline-flex rounded-xl bg-[var(--control-hover)] p-1" role="tablist">
          {(["submit", "track"] as const).map((value) => (
            <button
              key={value}
              type="button"
              role="tab"
              aria-selected={tab === value}
              onClick={() => setTab(value)}
              className={
                tab === value
                  ? "rounded-lg bg-[var(--panel-bg)] px-4 py-2 text-sm font-bold shadow-sm"
                  : "rounded-lg px-4 py-2 text-sm font-semibold text-text-secondary"
              }
            >
              {value === "submit" ? dict.requests.submitTab : dict.requests.trackTab}
            </button>
          ))}
        </div>

        {tab === "submit" && !created && (
          <form onSubmit={submitRequest} className="mt-5 rounded-2xl border border-[var(--panel-border)] bg-[var(--panel-bg)] p-4 shadow-[var(--shadow-card)] sm:p-5">
            <div>
              <label htmlFor="request-type" className={labelClass}>{dict.requests.type}</label>
              <select id="request-type" name="type" required className={fieldClass}>
                {REQUEST_TYPES.map((type) => <option key={type} value={type}>{dict.requests.types[type]}</option>)}
              </select>
            </div>
            <div className="mt-4">
              <label htmlFor="request-subject" className={labelClass}>{dict.requests.subject}</label>
              <input id="request-subject" name="subject" required maxLength={200} placeholder={dict.requests.subjectPlaceholder} className={fieldClass} />
            </div>
            <div className="mt-4">
              <label htmlFor="request-message" className={labelClass}>{dict.requests.message}</label>
              <textarea id="request-message" name="message" required maxLength={10000} rows={6} placeholder={dict.requests.messagePlaceholder} className={`${fieldClass} h-auto py-3`} />
            </div>
            <div className="mt-4 grid gap-4 sm:grid-cols-3">
              <div><label htmlFor="contact-name" className={labelClass}>{dict.requests.contactName} · {dict.requests.optional}</label><input id="contact-name" name="contactName" maxLength={160} className={fieldClass} /></div>
              <div><label htmlFor="contact-email" className={labelClass}>{dict.requests.contactEmail} · {dict.requests.optional}</label><input id="contact-email" name="contactEmail" type="email" maxLength={254} className={fieldClass} /></div>
              <div><label htmlFor="contact-phone" className={labelClass}>{dict.requests.contactPhone} · {dict.requests.optional}</label><input id="contact-phone" name="contactPhone" type="tel" maxLength={40} pattern="[+0-9()\-\s]*" className={fieldClass} /></div>
            </div>
            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <div>
                <label htmlFor="request-line" className={labelClass}>{dict.requests.line} · {dict.requests.optional}</label>
                <select id="request-line" name="lineCode" value={lineCode} onChange={(event) => setLineCode(event.target.value)} className={fieldClass}>
                  <option value="">{dict.requests.anyLine}</option>
                  {lines.map((line) => <option key={line.properties.code} value={line.properties.code}>{pickName(line.properties.name, lang)}</option>)}
                </select>
              </div>
              <div>
                <label htmlFor="request-station" className={labelClass}>{dict.requests.station} · {dict.requests.optional}</label>
                <select id="request-station" name="stationCode" className={fieldClass}>
                  <option value="">{dict.requests.anyStation}</option>
                  {stations.map((station) => <option key={station.properties.code} value={station.properties.code}>{pickName(station.properties.name, lang)}</option>)}
                </select>
              </div>
            </div>
            <label className="mt-5 flex items-start gap-3 text-sm leading-relaxed">
              <input name="consent" type="checkbox" required className="mt-1 h-4 w-4 shrink-0 accent-brand-red" />
              <span>{dict.requests.consent}</span>
            </label>
            {submitError && <p role="alert" className="mt-4 text-sm font-semibold text-brand-red">{submitError}</p>}
            <button type="submit" disabled={submitBusy} className="mt-5 h-11 w-full rounded-xl bg-brand-red px-4 text-sm font-bold text-surface-light disabled:cursor-not-allowed disabled:opacity-50">
              {submitBusy ? dict.requests.submitting : dict.requests.submit}
            </button>
          </form>
        )}
        {tab === "submit" && created && <SuccessCard result={created} />}

        {tab === "track" && (
          <>
            <form onSubmit={trackRequest} className="mt-5 rounded-2xl border border-[var(--panel-border)] bg-[var(--panel-bg)] p-4 shadow-[var(--shadow-card)] sm:p-5">
              <h2 className="text-lg font-bold">{dict.requests.trackTitle}</h2>
              <p className="mt-1 text-sm text-text-secondary">{dict.requests.trackIntro}</p>
              <div className="mt-4 grid gap-4 sm:grid-cols-2">
                <div><label htmlFor="track-code" className={labelClass}>{dict.requests.code}</label><input id="track-code" value={trackCode} onChange={(event) => restoreToken(event.target.value)} autoComplete="off" required className={`${fieldClass} font-mono`} /></div>
                <div><label htmlFor="track-token" className={labelClass}>{dict.requests.token}</label><input id="track-token" value={trackToken} onChange={(event) => setTrackToken(event.target.value)} autoComplete="off" required className={`${fieldClass} font-mono`} /></div>
              </div>
              {trackError && <p role="alert" className="mt-4 text-sm font-semibold text-brand-red">{trackError}</p>}
              <button type="submit" disabled={trackBusy} className="mt-5 h-11 w-full rounded-xl bg-brand-red px-4 text-sm font-bold text-surface-light disabled:cursor-not-allowed disabled:opacity-50">
                {trackBusy ? dict.requests.tracking : dict.requests.trackSubmit}
              </button>
            </form>
            {tracked && <RequestStatusCard request={tracked} />}
          </>
        )}
      </main>
    </div>
  );
}
