import React from "react";

export function Select({ value, onChange, options }) {
  return (
    <select
      value={value}
      onChange={onChange}
      style={{
        background: "var(--surface-elevated)",
        border: "1px solid var(--border-strong)",
        borderRadius: "var(--r-sm)",
        padding: "8px 11px",
        color: "var(--text-primary)",
        font: "500 12.5px var(--font-ui)",
      }}
    >
      {options.map((o) => (
        <option key={o.value} value={o.value}>
          {o.label}
        </option>
      ))}
    </select>
  );
}
