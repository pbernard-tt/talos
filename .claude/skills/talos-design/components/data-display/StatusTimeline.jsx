import React from "react";

const STEP_VISUAL = {
  COMPLETED: { bg: "rgba(34,197,94,0.16)", fg: "#22C55E", border: "#22C55E", char: "✓", line: "rgba(34,197,94,0.35)" },
  RUNNING: { bg: "rgba(139,92,246,0.16)", fg: "#A78BFA", border: "#8B5CF6", char: "●", line: "rgba(255,255,255,0.08)" },
  PENDING: { bg: "rgba(160,167,181,0.1)", fg: "#687080", border: "rgba(160,167,181,0.3)", char: "", line: "rgba(255,255,255,0.08)" },
  FAILED: { bg: "rgba(239,68,68,0.16)", fg: "#EF4444", border: "#EF4444", char: "✕", line: "rgba(239,68,68,0.3)" },
  SKIPPED: { bg: "rgba(160,167,181,0.08)", fg: "#4a5163", border: "rgba(160,167,181,0.15)", char: "–", line: "rgba(255,255,255,0.05)" },
};

export function StatusTimeline({ steps }) {
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        background: "var(--surface-card)",
        border: "1px solid var(--border-default)",
        borderRadius: "var(--r-lg)",
        padding: "16px 18px",
      }}
    >
      {steps.map((st, i) => {
        const v = STEP_VISUAL[st.state] || STEP_VISUAL.PENDING;
        return (
          <div key={st.key || i} style={{ display: "flex", alignItems: "center", flex: 1 }}>
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 6, minWidth: 70 }}>
              <span
                style={{
                  width: 22,
                  height: 22,
                  borderRadius: "50%",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  font: "700 10px var(--font-ui)",
                  background: v.bg,
                  color: v.fg,
                  border: `2px solid ${v.border}`,
                }}
              >
                {v.char}
              </span>
              <span style={{ font: "600 9.5px var(--font-ui)", color: v.fg, textAlign: "center" }}>{st.label}</span>
            </div>
            {i < steps.length - 1 && (
              <div style={{ flex: 1, height: 2, background: v.line, margin: "0 2px 18px" }} />
            )}
          </div>
        );
      })}
    </div>
  );
}
