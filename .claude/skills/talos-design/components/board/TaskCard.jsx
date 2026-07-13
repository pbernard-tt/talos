import React from "react";
import { AgentBadge } from "../badges/AgentBadge.jsx";
import { RiskBadge } from "../badges/RiskBadge.jsx";

export function TaskCard({ task, onClick, draggable = true, onDragStart, density = "comfortable" }) {
  const priorityColor = task.priority === "HIGH" ? "var(--status-error)" : task.priority === "LOW" ? "var(--text-muted)" : "var(--status-warning)";
  const pad = density === "compact" ? "9px 10px" : "13px 14px";
  return (
    <div
      draggable={draggable}
      onDragStart={onDragStart}
      onClick={onClick}
      style={{
        background: "var(--surface-card)",
        border: "1px solid var(--border-default)",
        borderRadius: "var(--r-lg)",
        padding: pad,
        cursor: draggable ? "grab" : "pointer",
      }}
    >
      <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: 6, marginBottom: 6 }}>
        <span style={{ font: "600 12.5px/1.35 var(--font-ui)", color: "var(--text-primary)" }}>{task.title}</span>
        <span style={{ width: 6, height: 6, borderRadius: "50%", background: priorityColor, flex: "none", marginTop: 5 }} />
      </div>
      <div style={{ font: "500 11px var(--font-ui)", color: "var(--text-muted)", marginBottom: 8 }}>{task.projectName}</div>

      {task.activeStepLabel && (
        <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 8 }}>
          <span style={{ width: 5, height: 5, borderRadius: "50%", background: "var(--accent)" }} />
          <span style={{ font: "600 10.5px var(--font-ui)", color: "var(--accent-soft)" }}>{task.activeStepLabel}</span>
        </div>
      )}
      {task.blockedReason && (
        <div style={{ font: "400 10.5px/1.4 var(--font-ui)", color: "var(--status-warning)", marginBottom: 8 }}>{task.blockedReason}</div>
      )}

      <div style={{ display: "flex", alignItems: "center", gap: 6, flexWrap: "wrap" }}>
        <AgentBadge agentKey={task.agentKey} />
        {task.riskLevel === "HIGH" && <RiskBadge level="HIGH" />}
      </div>
    </div>
  );
}
