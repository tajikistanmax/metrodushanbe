"use client";

import { useState } from "react";
import type { AiBriefing, AiChatResponse } from "@/lib/types";
import { sendAiChat } from "@/lib/admin-actions";

type AgentsPanelProps = {
  data: AiBriefing | null;
  error: string | null;
};

const STATUS_TONE: Record<string, string> = {
  ready: "border-brand-green/40 bg-brand-green/10 text-brand-green",
  watching: "border-warning/40 bg-warning/10 text-warning",
  needs_data: "border-warning/40 bg-warning/10 text-warning",
  needs_schedule: "border-warning/40 bg-warning/10 text-warning",
  needs_content: "border-warning/40 bg-warning/10 text-warning",
  needs_hardening: "border-brand-red/40 bg-brand-red/10 text-brand-red",
};

type ChatPanelProps = {
  agentCode: string;
};

function ChatPanel({ agentCode }: ChatPanelProps) {
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
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className="rounded-lg border border-[var(--table-border)] px-3 py-2 text-sm font-semibold transition-colors hover:bg-[var(--chip-bg)]"
      >
        {open ? "Close chat" : "Chat"}
      </button>
      {open && (
        <div className="mt-3 space-y-3">
          <div className="flex gap-2">
            <input
              type="text"
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") handleSend(); }}
              placeholder="Ask the agent..."
              className="min-w-0 flex-1 rounded-lg border border-[var(--card-border)] bg-[var(--card-bg)] px-3 py-2 text-sm outline-none focus:border-[var(--info)]"
            />
            <button
              type="button"
              onClick={handleSend}
              disabled={loading || !message.trim()}
              className="shrink-0 rounded-lg bg-[var(--info)] px-4 py-2 text-sm font-bold text-white disabled:opacity-50"
            >
              {loading ? "..." : "Send"}
            </button>
          </div>
          {chatError && (
            <p className="text-sm text-brand-red">{chatError}</p>
          )}
          {response && (
            <div className="rounded-lg border border-[var(--card-border)] bg-[var(--chip-bg)] p-3">
              <p className="text-sm">{response.reply}</p>
              {response.sources.length > 0 && (
                <details className="mt-2">
                  <summary className="cursor-pointer text-xs font-semibold text-text-secondary">
                    Sources ({response.sources.length})
                  </summary>
                  <ul className="mt-1 list-inside list-disc space-y-1">
                    {response.sources.map((src) => (
                      <li key={src} className="text-xs text-text-secondary">{src}</li>
                    ))}
                  </ul>
                </details>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default function AgentsPanel({ data, error }: AgentsPanelProps) {
  if (error) {
    return (
      <div className="rounded-xl border border-[var(--card-border)] bg-[var(--card-bg)] p-5 shadow-[var(--shadow-card)]">
        <p className="text-sm font-bold">AI briefing is unavailable</p>
        <p className="mt-1 text-sm text-text-secondary">{error}</p>
      </div>
    );
  }

  if (!data) {
    return (
      <div className="rounded-xl border border-[var(--card-border)] bg-[var(--card-bg)] p-5 shadow-[var(--shadow-card)]">
        <p className="text-sm text-text-secondary">No AI briefing data.</p>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <section className="rounded-xl border border-[var(--card-border)] bg-[var(--card-bg)] p-5 shadow-[var(--shadow-card)]">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-xs font-bold uppercase tracking-wide text-text-secondary">
              Readiness posture
            </p>
            <p className="mt-1 text-2xl font-extrabold">{data.posture}</p>
          </div>
          <p className="rounded-full bg-[var(--chip-bg)] px-3 py-1 text-xs font-semibold text-text-secondary">
            {new Date(data.generatedAt).toLocaleString()}
          </p>
        </div>
        <ul className="mt-4 grid gap-2">
          {data.recommendations.map((item) => (
            <li key={item} className="rounded-lg bg-[var(--chip-bg)] px-3 py-2 text-sm">
              {item}
            </li>
          ))}
        </ul>
      </section>

      <section className="grid grid-cols-1 gap-4 xl:grid-cols-2">
        {data.agents.map((agent) => (
          <article
            key={agent.code}
            className="rounded-xl border border-[var(--card-border)] bg-[var(--card-bg)] p-5 shadow-[var(--shadow-card)]"
          >
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="text-lg font-extrabold">{agent.name.en}</h2>
                <p className="mt-1 text-sm text-text-secondary">{agent.role}</p>
              </div>
              <span
                className={`shrink-0 rounded-full border px-2.5 py-1 text-xs font-bold ${
                  STATUS_TONE[agent.status] ?? "border-info/40 bg-info/10 text-info"
                }`}
              >
                {agent.status}
              </span>
            </div>

            <dl className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div>
                <dt className="text-xs font-bold uppercase tracking-wide text-text-secondary">
                  Provider
                </dt>
                <dd className="mt-1 text-sm font-semibold">{agent.modelProvider}</dd>
              </div>
              <div>
                <dt className="text-xs font-bold uppercase tracking-wide text-text-secondary">
                  Model class
                </dt>
                <dd className="mt-1 text-sm font-semibold">{agent.modelClass}</dd>
              </div>
            </dl>

            <div className="mt-4">
              <p className="text-xs font-bold uppercase tracking-wide text-text-secondary">
                Capabilities
              </p>
              <div className="mt-2 flex flex-wrap gap-2">
                {agent.capabilities.map((capability) => (
                  <span
                    key={capability}
                    className="rounded-full bg-[var(--chip-bg)] px-2.5 py-1 text-xs font-semibold"
                  >
                    {capability}
                  </span>
                ))}
              </div>
            </div>

            <div className="mt-4 grid gap-2">
              {agent.signals.map((signal) => (
                <p key={signal} className="text-sm text-text-secondary">
                  {signal}
                </p>
              ))}
            </div>

            <p className="mt-4 rounded-lg border border-[var(--table-border)] px-3 py-2 text-sm font-semibold">
              {agent.nextAction}
            </p>

            <ChatPanel agentCode={agent.code} />
          </article>
        ))}
      </section>
    </div>
  );
}
