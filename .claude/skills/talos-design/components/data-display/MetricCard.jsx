import React from "react";

export function MetricCard({ label, value, color = "var(--text-primary)", onClick }) {
  const Tag = onClick ? "button" : "div";
  return (
    <Tag
      onClick={onClick}
      style={{
        textAlign: "left",
        background: "var(--surface-card)",
        border: "1px solid var(--border-default)",
        borderRadius: "var(--r-lg)",
        padding: 14,
        cursor: onClick ? "pointer" : "default",
        width: "100%",
        boxSizing: "border-box",
      }}
    >
      <div
        style={{
          font: "600 11px var(--font-ui)",
          color: "var(--text-muted)",
          marginBottom: 8,
          whiteSpace: "nowrap",
          overflow: "hidden",
          textOverflow: "ellipsis",
        }}
      >
        {label}
      </div>
      <div style={{ font: "700 24px var(--font-mono)", color }}>{value}</div>
    </Tag>
  );
}
