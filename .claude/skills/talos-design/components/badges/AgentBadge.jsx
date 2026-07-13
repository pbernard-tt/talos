import React from "react";

const AGENTS = {
  "claude-code": { label: "Claude Code", initial: "C", fg: "#A78BFA", border: "rgba(139,92,246,0.3)" },
  "codex-cli": { label: "Codex CLI", initial: "X", fg: "#38BDF8", border: "rgba(56,189,248,0.3)" },
  "opencode": { label: "OpenCode", initial: "O", fg: "#22C55E", border: "rgba(34,197,94,0.3)" },
  "openhands": { label: "OpenHands", initial: "H", fg: "#F59E0B", border: "rgba(245,158,11,0.3)" },
  "custom-shell": { label: "Custom Shell", initial: "S", fg: "var(--text-secondary)", border: "rgba(160,167,181,0.3)" },
};

export function AgentBadge({ agentKey = "custom-shell" }) {
  const a = AGENTS[agentKey] || AGENTS["custom-shell"];
  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 6,
        padding: "3px 9px 3px 7px",
        borderRadius: "var(--r-xs)",
        font: "600 11px/1.5 var(--font-ui)",
        whiteSpace: "nowrap",
        background: "var(--surface-elevated)",
        color: a.fg,
        border: `1px solid ${a.border}`,
      }}
    >
      <span
        style={{
          width: 14,
          height: 14,
          borderRadius: 4,
          background: a.fg,
          flex: "none",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          font: "700 8px var(--font-mono)",
          color: "var(--surface-elevated)",
        }}
      >
        {a.initial}
      </span>
      {a.label}
    </span>
  );
}
