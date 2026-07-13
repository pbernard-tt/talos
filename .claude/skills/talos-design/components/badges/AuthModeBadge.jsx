import React from "react";

export function AuthModeBadge({ mode = "api_key" }) {
  if (mode === "subscription_local") {
    return (
      <span style={badgeStyle("#F59E0B", "rgba(245,158,11,0.1)", "rgba(245,158,11,0.28)")}>
        personal subscription
      </span>
    );
  }
  if (mode === "api_key") {
    return (
      <span style={badgeStyle("#38BDF8", "rgba(56,189,248,0.1)", "rgba(56,189,248,0.28)")}>
        api key
      </span>
    );
  }
  return (
    <span style={badgeStyle("var(--text-muted)", "rgba(160,167,181,0.1)", "rgba(160,167,181,0.2)")}>
      n/a
    </span>
  );
}

function badgeStyle(fg, bg, border) {
  return {
    display: "inline-flex",
    alignItems: "center",
    gap: 5,
    padding: "2px 8px",
    borderRadius: "var(--r-sm)",
    font: "600 10.5px/1.6 var(--font-mono)",
    background: bg,
    color: fg,
    border: `1px solid ${border}`,
  };
}
