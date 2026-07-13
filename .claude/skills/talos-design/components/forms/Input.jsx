import React from "react";

export function Input({ value, onChange, placeholder, type = "text", disabled = false, error }) {
  return (
    <div>
      <input
        type={type}
        value={value}
        onChange={onChange}
        placeholder={placeholder}
        disabled={disabled}
        style={{
          width: "100%",
          boxSizing: "border-box",
          background: "var(--surface-sunken)",
          border: `1px solid ${error ? "var(--status-error)" : "var(--border-strong)"}`,
          borderRadius: "var(--r-md)",
          padding: "10px 12px",
          color: "var(--text-primary)",
          font: "400 13px var(--font-ui)",
          outline: "none",
          opacity: disabled ? 0.5 : 1,
        }}
      />
      {error && (
        <div style={{ marginTop: 5, font: "500 11.5px var(--font-ui)", color: "var(--status-error)" }}>{error}</div>
      )}
    </div>
  );
}
