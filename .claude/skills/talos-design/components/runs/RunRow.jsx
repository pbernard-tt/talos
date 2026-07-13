import React from "react";
import { AgentBadge } from "../badges/AgentBadge.jsx";
import { AuthModeBadge } from "../badges/AuthModeBadge.jsx";
import { StatusBadge } from "../badges/StatusBadge.jsx";

export function RunRow({ run, onClick }) {
  return (
    <button
      onClick={onClick}
      style={{
        width: "100%",
        display: "grid",
        gridTemplateColumns: "0.7fr 1.4fr 1fr 1fr 0.9fr 0.9fr 0.7fr 1fr",
        gap: 10,
        padding: "12px 16px",
        border: "0",
        borderBottom: "1px solid rgba(255,255,255,0.04)",
        background: "none",
        cursor: "pointer",
        textAlign: "left",
        alignItems: "center",
      }}
    >
      <span style={{ font: "600 12px var(--font-mono)", color: "var(--text-secondary)" }}>{run.id}</span>
      <span style={{ font: "500 12.5px var(--font-ui)", color: "var(--text-primary)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{run.taskTitle}</span>
      <span style={{ font: "500 12px var(--font-ui)", color: "var(--text-secondary)" }}>{run.projectName}</span>
      <span><AgentBadge agentKey={run.agentKey} /></span>
      <span><AuthModeBadge mode={run.authMode} /></span>
      <span style={{ font: "500 11.5px var(--font-mono)", color: "var(--text-muted)" }}>{run.duration}</span>
      <span style={{ font: "600 11.5px var(--font-mono)", color: "var(--text-secondary)" }}>{run.cost}</span>
      <span><StatusBadge label={run.status} tone={run.statusTone} /></span>
    </button>
  );
}
