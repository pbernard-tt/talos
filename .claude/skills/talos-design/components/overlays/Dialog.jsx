import React from "react";

export function Dialog({ open, onClose, title, consequence, children, width = 440 }) {
  if (!open) return null;
  return (
    <div
      onClick={onClose}
      style={{
        position: "fixed",
        inset: 0,
        background: "var(--scrim)",
        zIndex: 100,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          width,
          maxWidth: "92vw",
          background: "var(--surface-elevated)",
          border: "1px solid var(--border-strong)",
          borderRadius: "var(--r-xl)",
          boxShadow: "var(--shadow-4)",
          padding: 22,
        }}
      >
        {title && <div style={{ font: "700 16px var(--font-ui)", marginBottom: 6 }}>{title}</div>}
        {consequence && (
          <div style={{ font: "400 12.5px/1.5 var(--font-ui)", color: "var(--text-secondary)", marginBottom: 16 }}>
            {consequence}
          </div>
        )}
        {children}
      </div>
    </div>
  );
}
