import React from "react";

const VARIANTS = {
  primary: { bg: "var(--accent)", hoverBg: "var(--accent-hover)", fg: "#fff", border: "transparent" },
  secondary: { bg: "var(--surface-elevated)", hoverBg: "var(--surface-hover)", fg: "var(--text-primary)", border: "var(--border-strong)" },
  ghost: { bg: "transparent", hoverBg: "var(--surface-hover)", fg: "var(--text-secondary)", border: "transparent" },
  danger: { bg: "transparent", hoverBg: "rgba(239,68,68,0.1)", fg: "var(--status-error)", border: "rgba(239,68,68,0.35)" },
};

const SIZES = {
  sm: { padding: "6px 11px", font: "600 11.5px var(--font-ui)" },
  md: { padding: "9px 14px", font: "600 12.5px var(--font-ui)" },
  lg: { padding: "11px 18px", font: "600 13.5px var(--font-ui)" },
};

export function Button({ children, variant = "primary", size = "md", disabled = false, onClick, icon }) {
  const v = VARIANTS[variant] || VARIANTS.primary;
  const s = SIZES[size] || SIZES.md;
  const [hover, setHover] = React.useState(false);
  return (
    <button
      onClick={disabled ? undefined : onClick}
      disabled={disabled}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 7,
        padding: s.padding,
        font: s.font,
        borderRadius: "var(--r-md)",
        border: v.border === "transparent" ? "0" : `1px solid ${v.border}`,
        background: hover && !disabled ? v.hoverBg : v.bg,
        color: v.fg,
        cursor: disabled ? "not-allowed" : "pointer",
        opacity: disabled ? 0.4 : 1,
        transition: "background var(--duration-micro) var(--ease-out)",
      }}
    >
      {icon}
      {children}
    </button>
  );
}
