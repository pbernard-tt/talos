import React from "react";

const TONE_COLORS = {
  neutral: { fg: "var(--text-secondary)", bg: "rgba(160,167,181,0.12)", border: "rgba(160,167,181,0.25)" },
  purple: { fg: "var(--accent-soft)", bg: "var(--accent-tint-strong)", border: "var(--accent-border)" },
  success: { fg: "var(--status-success)", bg: "var(--status-success-tint)", border: "rgba(34,197,94,0.3)" },
  warning: { fg: "var(--status-warning)", bg: "var(--status-warning-tint)", border: "rgba(245,158,11,0.3)" },
  error: { fg: "var(--status-error)", bg: "var(--status-error-tint)", border: "rgba(239,68,68,0.32)" },
  info: { fg: "var(--status-info)", bg: "var(--status-info-tint)", border: "rgba(56,189,248,0.3)" },
};

export function StatusBadge({ label, tone = "neutral" }) {
  const c = TONE_COLORS[tone] || TONE_COLORS.neutral;
  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 6,
        padding: "3px 10px",
        borderRadius: "var(--r-pill)",
        font: "600 11px/1.5 var(--font-ui)",
        letterSpacing: ".02em",
        whiteSpace: "nowrap",
        background: c.bg,
        color: c.fg,
        border: `1px solid ${c.border}`,
      }}
    >
      <span style={{ width: 6, height: 6, borderRadius: "50%", background: c.fg, flex: "none" }} />
      {label}
    </span>
  );
}
