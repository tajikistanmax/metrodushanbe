"use client";

import { useState } from "react";
import type { AiBriefing, AiChatResponse } from "@/lib/types";
import { sendAiChat } from "@/lib/admin-actions";
import { formatDateTime, pickName } from "@/lib/i18n";
import { Badge, Button, Card, type BadgeTone } from "@/shared/ui";
import { useI18n } from "./I18nProvider";

type AgentsPanelProps = {
  data: AiBriefing | null;
  error: string | null;
};

/**
 * Статус агента → тон бейджа. Было: своя тройка border/bg/text на каждый
 * статус (цветной текст на цветном тинте — в тёмной теме ниже AA). Тон теперь
 * несёт подложка и точка, подпись остаётся текстом (SC 1.4.1).
 */
const STATUS_TONE: Record<string, BadgeTone> = {
  ready: "success",
  monitoring: "success",
  watching: "warning",
  needs_data: "warning",
  needs_schedule: "warning",
  needs_content: "warning",
  needs_hardening: "critical",
};

type ChatPanelProps = {
  agentCode: string;
};

function ChatPanel({ agentCode }: ChatPanelProps) {
  const { dict } = useI18n();
  const [open, setOpen] = useState(false);
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);
  const [response, setResponse] = useState<AiChatResponse | null>(null);
  const [chatError, setChatError] = useState<string | null>(null);

  async function handleSend() {
    if (!message.trim()) return;
    setLoading(true);
    setChatError(null);
    setResponse(null);
    const result = await sendAiChat({ agentCode, message: message.trim() });
    if (result.error) {
      setChatError(result.error);
    } else {
      setResponse(result.data);
    }
    setLoading(false);
  }

  return (
    <div className="mt-4">
      <Button onClick={() => setOpen(!open)} aria-expanded={open}>
        {open ? dict.agents.chatClose : dict.agents.chatOpen}
      </Button>
      {open && (
        <div className="mt-3 space-y-3">
          <div className="flex gap-2">
            <input
              type="text"
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") handleSend(); }}
              placeholder={dict.agents.chatPlaceholder}
              aria-label={dict.agents.chatInputLabel}
              className="min-w-0 flex-1 rounded-control border border-[var(--border-strong)] bg-[var(--surface-raised)] px-3 py-2 text-small outline-none focus:border-info"
            />
            <Button
              variant="primary"
              onClick={handleSend}
              disabled={loading || !message.trim()}
            >
              {loading ? dict.agents.chatSending : dict.agents.chatSend}
            </Button>
          </div>
          {/* aria-live: ответ приходит асинхронно и должен быть объявлен */}
          <div aria-live="polite">
            {chatError && (
              <p role="alert" className="text-small font-semibold text-brand-red">
                {chatError}
              </p>
            )}
            {response && (
              <div className="rounded-control border border-[var(--border-subtle)] bg-[var(--surface-chip)] p-3">
                <p className="text-small">{response.reply}</p>
                {response.sources.length > 0 && (
                  <details className="mt-2">
                    <summary className="cursor-pointer text-caption font-semibold text-text-secondary">
                      {dict.agents.chatSources} ({response.sources.length})
                    </summary>
                    <ul className="mt-1 list-inside list-disc space-y-1">
                      {response.sources.map((src) => (
                        <li key={src} className="text-caption text-text-secondary">{src}</li>
                      ))}
                    </ul>
                  </details>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export default function AgentsPanel({ data, error }: AgentsPanelProps) {
  const { dict, lang } = useI18n();

  // Радиус был rounded-xl(12) у элемента той же роли, что и карточка (8),
  // плюс своя тень на каждой панели. Теперь — примитив Card: 8px, граница,
  // без тени (см. packages/design/MIGRATION.md).
  if (error) {
    return (
      <Card as="div" padding="lg">
        <p className="text-small font-bold">{dict.agents.errorTitle}</p>
        <p className="mt-1 text-small text-text-secondary">{error}</p>
      </Card>
    );
  }

  if (!data) {
    return (
      <Card as="div" padding="lg">
        <p className="text-small text-text-secondary">{dict.agents.empty}</p>
      </Card>
    );
  }

  return (
    <div className="space-y-5">
      <Card padding="lg" aria-label={dict.agents.postureTitle}>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-caption font-bold uppercase tracking-[0.08em] text-text-secondary">
              {dict.agents.postureTitle}
            </p>
            <p className="mt-1 text-title-m font-bold">
              {dict.agents.postures[data.posture] ?? data.posture}
            </p>
          </div>
          <p className="rounded-chip bg-[var(--surface-chip)] px-3 py-1 text-caption font-semibold text-text-secondary">
            {formatDateTime(data.generatedAt, lang)}
          </p>
        </div>
        {/* Рекомендации — i18n-объекты без стабильного кода (одна из них рождается в рантайме
            ответом LLM), поэтому ключ по индексу: список неупорядочиваемый и перестраивается целиком. */}
        <ul className="mt-4 grid gap-2">
          {data.recommendations.map((item, index) => (
            <li key={index} className="rounded-control bg-[var(--surface-chip)] px-3 py-2 text-small">
              {pickName(item, lang)}
            </li>
          ))}
        </ul>
      </Card>

      <section className="grid grid-cols-1 gap-4 xl:grid-cols-2">
        {data.agents.map((agent) => (
          <Card as="article" key={agent.code} padding="lg">
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="text-title-s font-bold">{pickName(agent.name, lang)}</h2>
                <p className="mt-1 text-small text-text-secondary">{pickName(agent.role, lang)}</p>
              </div>
              <Badge tone={STATUS_TONE[agent.status] ?? "info"} dot className="shrink-0">
                {dict.agents.statuses[agent.status] ?? agent.status}
              </Badge>
            </div>

            <dl className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div>
                <dt className="text-caption font-bold uppercase tracking-[0.08em] text-text-secondary">
                  {dict.agents.fieldProvider}
                </dt>
                <dd className="mt-1 text-small font-semibold">{agent.modelProvider}</dd>
              </div>
              <div>
                <dt className="text-caption font-bold uppercase tracking-[0.08em] text-text-secondary">
                  {dict.agents.fieldModelClass}
                </dt>
                <dd className="mt-1 text-small font-semibold">{agent.modelClass}</dd>
              </div>
            </dl>

            <div className="mt-4">
              <p className="text-caption font-bold uppercase tracking-[0.08em] text-text-secondary">
                {dict.agents.fieldCapabilities}
              </p>
              <div className="mt-2 flex flex-wrap gap-2">
                {agent.capabilities.map((capability, index) => (
                  <Badge key={index}>{pickName(capability, lang)}</Badge>
                ))}
              </div>
            </div>

            <div className="mt-4 grid gap-2">
              {agent.signals.map((signal, index) => (
                <p key={index} className="text-small text-text-secondary">
                  {pickName(signal, lang)}
                </p>
              ))}
            </div>

            <p className="mt-4 rounded-control border border-[var(--border-subtle)] px-3 py-2 text-small font-semibold">
              {pickName(agent.nextAction, lang)}
            </p>

            <ChatPanel agentCode={agent.code} />
          </Card>
        ))}
      </section>
    </div>
  );
}
