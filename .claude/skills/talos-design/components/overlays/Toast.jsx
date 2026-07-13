import React from "react";

const TONE = {
  purple: "var(--accent)",
  success: "var(--status-success)",
  warning: "var(--status-warning)",
  error: "var(--status-error)",
  info: "var(--status-info)",
};

export function Toast({ message, tone = "info" }) {
  const accent = TONE[tone] || TONE.info;
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 9,
        background: "var(--surface-elevated)",
        border: "1px solid var(--border-strong)",
        borderLeft: `3px solid ${accent}`,
        borderRadius: "var(--r-lg)",
        padding: "11px 14px",
        boxShadow: "var(--shadow-2)",
        minWidth: 260,
        maxWidth: 360,
      }}
    >
      <span style={{ font: "500 12.5px/1.4 var(--font-ui)", color: "var(--text-primary)", flex: 1 }}>{message}</span>
    </div>
  );
}
