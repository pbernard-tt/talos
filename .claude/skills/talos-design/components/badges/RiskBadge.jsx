import React from "react";

export function RiskBadge({ level = "HIGH" }) {
  if (level !== "HIGH") return null;
  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 5,
        padding: "3px 9px",
        borderRadius: "var(--r-pill)",
        font: "700 10.5px/1.5 var(--font-ui)",
        letterSpacing: ".03em",
        background: "var(--status-error-tint)",
        color: "var(--status-error)",
        border: "1px solid rgba(239,68,68,0.3)",
      }}
    >
      <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
        <line x1="12" y1="9" x2="12" y2="13" />
        <line x1="12" y1="17" x2="12.01" y2="17" />
      </svg>
      HIGH RISK
    </span>
  );
}
