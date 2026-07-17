"use client";

/**
 * Обращения граждан: подача и отслеживание по коду + токену.
 *
 * Форма переведена на общие примитивы (@/shared/ui): Input/Textarea/Select
 * связывают подпись с контролом, объявляют обязательность через aria-required
 * и показывают ошибку текстом, а не только красной рамкой (SC 1.4.1). До
 * редизайна здесь были самодельные fieldClass/labelClass и сырые <button>.
 *
 * Раздел не показывает данные сети как таковые (селекты линий и станций —
 * вспомогательные), поэтому источник в шапку не публикуется: пилюля источника
 * относится к содержимому страницы, а содержимое здесь — форма.
 */

import { useEffect, useMemo, useState, type ReactNode } from "react";
import { formatDushanbeDateTime } from "@/lib/date-time";
import { MAIN_CONTENT_ID } from "@/lib/dom-ids";
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
import {
  Alert,
  Badge,
  Button,
  Card,
  Checkbox,
  Container,
  Input,
  Select,
  Stack,
  Textarea,
  type BadgeTone,
} from "@/shared/ui";
import { useI18n } from "./I18nProvider";

const REQUEST_TYPES: CitizenRequestType[] = [
  "complaint",
  "suggestion",
  "incident",
  "question",
  "lost_item",
];

/**
 * Тон бейджа статуса. Цвет ДУБЛИРУЕТ подпись, а не заменяет её (SC 1.4.1).
 * Прежние классы вида «bg-info/15 text-info» давали цветной текст на цветном
 * фоне — ровно та комбинация, что регулярно проваливает 4.5:1; у Badge текст
 * всегда --text-primary поверх тинта (≈13:1 в обеих темах).
 */
const STATUS_TONE: Record<CitizenRequestStatus, BadgeTone> = {
  new: "info",
  in_progress: "warning",
  awaiting_info: "warning",
  resolved: "success",
  closed: "neutral",
  reopened: "critical",
};

function optional(value: FormDataEntryValue | null): string | undefined {
  const trimmed = String(value ?? "").trim();
  return trimmed === "" ? undefined : trimmed;
}

function tokenStorageKey(code: string): string {
  return `metro-dushanbe.request.${code.trim().toUpperCase()}`;
}

/** Строка «подпись → значение» в карточке обращения. */
function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-caption font-semibold uppercase tracking-[0.08em] text-text-secondary">
        {label}
      </dt>
      <dd className="mt-1 text-small">{children}</dd>
    </div>
  );
}

function RequestStatusCard({ request }: { request: CitizenRequestPublic }) {
  const { lang, dict } = useI18n();
  const formatDate = (value: string) => formatDushanbeDateTime(value, lang);

  return (
    <Card padding="lg" className="mt-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-caption font-semibold uppercase tracking-[0.08em] text-text-secondary">
            {dict.requests.code}
          </p>
          <p className="data-text mt-1 text-title-s font-bold">{request.code}</p>
        </div>
        <Badge tone={STATUS_TONE[request.status]} dot>
          {dict.requests.statuses[request.status]}
        </Badge>
      </div>

      <h2 className="mt-6 text-title-s font-bold">{request.subject}</h2>
      <p className="mt-1 text-small text-text-secondary">
        {dict.requests.types[request.type]}
      </p>

      <dl className="mt-4 grid gap-4 sm:grid-cols-2">
        <Row label={dict.requests.createdAt}>{formatDate(request.createdAt)}</Row>
        <Row label={dict.requests.updatedAt}>{formatDate(request.updatedAt)}</Row>
      </dl>

      <div className="mt-6 rounded-control bg-[var(--surface-sunken)] p-4">
        <h3 className="text-caption font-semibold uppercase tracking-[0.08em] text-text-secondary">
          {dict.requests.response}
        </h3>
        <p className="mt-2 whitespace-pre-wrap text-small leading-relaxed">
          {request.response || dict.requests.noResponse}
        </p>
      </div>
    </Card>
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
    <div className="mt-6">
      <Alert
        tone="success"
        label={dict.requests.successTitle}
        heading={dict.requests.successTitle}
      >
        {dict.requests.successBody}
      </Alert>

      <Card padding="lg" className="mt-4">
        <dl className="grid gap-6">
          <Row label={dict.requests.code}>
            <span className="data-text break-all text-title-s font-bold">
              {result.request.code}
            </span>
          </Row>
          <div>
            <dt className="text-caption font-semibold uppercase tracking-[0.08em] text-text-secondary">
              {dict.requests.token}
            </dt>
            <dd className="data-text mt-1 break-all rounded-control bg-[var(--surface-sunken)] p-3 text-small">
              {result.trackingToken}
            </dd>
            <p className="mt-2 text-caption text-text-secondary">
              {dict.requests.tokenHint}
            </p>
          </div>
        </dl>
        <Button variant="primary" onClick={copy} className="mt-4">
          {copied ? dict.requests.copied : dict.requests.copy}
        </Button>
      </Card>
    </div>
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
        sessionStorage.setItem(tokenStorageKey(result.request.code), result.trackingToken);
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
      const saved = sessionStorage.getItem(tokenStorageKey(code));
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

  /** «· необязательно» — тем же способом, что и раньше, но одним местом. */
  const optionalSuffix = ` · ${dict.requests.optional}`;

  return (
    <main id={MAIN_CONTENT_ID} className="flex-1 py-8 sm:py-12">
      <Container width="narrow">
        <h1 className="text-title-l font-bold sm:text-title-xl">
          {dict.requests.heading}
        </h1>
        <p className="mt-3 max-w-[65ch] text-lead text-text-secondary">
          {dict.requests.intro}
        </p>

        {/* Переключатель «подать / отследить» */}
        <div
          className="mt-6 inline-flex gap-1 rounded-control bg-[var(--surface-sunken)] p-1"
          role="tablist"
        >
          {(["submit", "track"] as const).map((value) => (
            <button
              key={value}
              type="button"
              role="tab"
              aria-selected={tab === value}
              onClick={() => setTab(value)}
              className={
                tab === value
                  ? "rounded-control bg-[var(--surface-raised)] px-4 py-2 text-small font-bold"
                  : "rounded-control px-4 py-2 text-small font-semibold text-text-secondary"
              }
            >
              {value === "submit" ? dict.requests.submitTab : dict.requests.trackTab}
            </button>
          ))}
        </div>

        {tab === "submit" && !created && (
          <Card as="div" padding="lg" className="mt-6">
            <form onSubmit={submitRequest}>
              <Stack gap={4}>
                <Select
                  label={dict.requests.type}
                  name="type"
                  required
                  options={REQUEST_TYPES.map((type) => ({
                    value: type,
                    label: dict.requests.types[type],
                  }))}
                />
                <Input
                  label={dict.requests.subject}
                  name="subject"
                  required
                  maxLength={200}
                  placeholder={dict.requests.subjectPlaceholder}
                />
                <Textarea
                  label={dict.requests.message}
                  name="message"
                  required
                  maxLength={10000}
                  rows={6}
                  placeholder={dict.requests.messagePlaceholder}
                />

                <div className="grid gap-4 sm:grid-cols-3">
                  <Input
                    label={dict.requests.contactName + optionalSuffix}
                    name="contactName"
                    maxLength={160}
                  />
                  <Input
                    label={dict.requests.contactEmail + optionalSuffix}
                    name="contactEmail"
                    type="email"
                    maxLength={254}
                  />
                  <Input
                    label={dict.requests.contactPhone + optionalSuffix}
                    name="contactPhone"
                    type="tel"
                    maxLength={40}
                    pattern="[+0-9()\-\s]*"
                  />
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <Select
                    label={dict.requests.line + optionalSuffix}
                    name="lineCode"
                    value={lineCode}
                    onChange={(event) => setLineCode(event.target.value)}
                    options={[
                      { value: "", label: dict.requests.anyLine },
                      ...lines.map((line) => ({
                        value: line.properties.code,
                        label: pickName(line.properties.name, lang),
                      })),
                    ]}
                  />
                  <Select
                    label={dict.requests.station + optionalSuffix}
                    name="stationCode"
                    options={[
                      { value: "", label: dict.requests.anyStation },
                      ...stations.map((station) => ({
                        value: station.properties.code,
                        label: pickName(station.properties.name, lang),
                      })),
                    ]}
                  />
                </div>

                <Checkbox label={dict.requests.consent} name="consent" required />

                {submitError && (
                  <p role="alert" className="text-small font-semibold text-brand-red">
                    {submitError}
                  </p>
                )}

                {/* Основное действие — navy: красный в системе означает
                    опасное действие (Button variant="danger"). */}
                <Button
                  type="submit"
                  variant="primary"
                  size="lg"
                  block
                  disabled={submitBusy}
                >
                  {submitBusy ? dict.requests.submitting : dict.requests.submit}
                </Button>
              </Stack>
            </form>
          </Card>
        )}

        {tab === "submit" && created && <SuccessCard result={created} />}

        {tab === "track" && (
          <>
            <Card
              as="div"
              padding="lg"
              className="mt-6"
              heading={dict.requests.trackTitle}
              description={dict.requests.trackIntro}
            >
              <form onSubmit={trackRequest}>
                <Stack gap={4}>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <Input
                      label={dict.requests.code}
                      value={trackCode}
                      onChange={(event) => restoreToken(event.target.value)}
                      autoComplete="off"
                      required
                      mono
                    />
                    <Input
                      label={dict.requests.token}
                      type="password"
                      value={trackToken}
                      onChange={(event) => setTrackToken(event.target.value)}
                      autoComplete="off"
                      required
                      mono
                    />
                  </div>

                  {trackError && (
                    <p role="alert" className="text-small font-semibold text-brand-red">
                      {trackError}
                    </p>
                  )}

                  <Button
                    type="submit"
                    variant="primary"
                    size="lg"
                    block
                    disabled={trackBusy}
                  >
                    {trackBusy ? dict.requests.tracking : dict.requests.trackSubmit}
                  </Button>
                </Stack>
              </form>
            </Card>
            {tracked && <RequestStatusCard request={tracked} />}
          </>
        )}
      </Container>
    </main>
  );
}
