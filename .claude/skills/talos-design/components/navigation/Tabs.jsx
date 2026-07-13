import React from "react";

export function Tabs({ items, activeKey, onChange }) {
  return (
    <div style={{ display: "flex", gap: 4, borderBottom: "1px solid var(--border-default)" }}>
      {items.map((it) => {
        const active = it.key === activeKey;
        return (
          <button
            key={it.key}
            onClick={() => onChange(it.key)}
            style={{
              padding: "10px 14px",
              background: "none",
              border: "0",
              borderBottom: `2px solid ${active ? "var(--accent)" : "transparent"}`,
              color: active ? "var(--text-primary)" : "var(--text-secondary)",
              font: "600 12.5px var(--font-ui)",
              cursor: "pointer",
              whiteSpace: "nowrap",
            }}
          >
            {it.label}
          </button>
        );
      })}
    </div>
  );
}
