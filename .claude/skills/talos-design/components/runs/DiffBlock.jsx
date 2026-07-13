import React from "react";
import { RiskBadge } from "../badges/RiskBadge.jsx";

export function DiffBlock({ file }) {
  return (
    <div style={{ background: "var(--surface-sunken)", border: "1px solid var(--border-default)", borderRadius: "var(--r-md)", marginBottom: 10, overflow: "hidden" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 10, padding: "9px 13px", background: "var(--surface-card)", borderBottom: "1px solid var(--border-default)" }}>
        <span style={{ font: "600 12px var(--font-mono)", color: "var(--text-primary)", flex: 1 }}>{file.path}</span>
        <span style={{ font: "600 11px var(--font-ui)", color: "var(--status-success)" }}>+{file.additions}</span>
        <span style={{ font: "600 11px var(--font-ui)", color: "var(--status-error)" }}>-{file.deletions}</span>
        {file.risk && <RiskBadge level="HIGH" />}
      </div>
      <pre style={{ margin: 0, padding: "12px 14px", font: "11.5px/1.7 var(--font-mono)", color: "var(--text-secondary)", whiteSpace: "pre-wrap", overflowX: "auto" }}>
        {file.hunkText}
      </pre>
      {file.risk && file.riskLabel && (
        <div style={{ padding: "8px 14px", background: "rgba(239,68,68,0.08)", borderTop: "1px solid rgba(239,68,68,0.2)", font: "500 11.5px var(--font-ui)", color: "#f5a3a3" }}>
          {file.riskLabel}
        </div>
      )}
    </div>
  );
}
