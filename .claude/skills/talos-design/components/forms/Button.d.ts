/**
 * Button — the one button primitive for the whole product. Press state
 * shrinks to scale(0.98) is intentionally NOT implemented inline here (do it
 * with a wrapping style-active if your renderer supports it); hover always
 * steps one shade darker/lighter, never scales.
 *
 * @startingPoint section="Core" subtitle="Primary/secondary/ghost/danger button" viewport="500x120"
 */
export interface ButtonProps {
  children: React.ReactNode;
  variant?: "primary" | "secondary" | "ghost" | "danger";
  size?: "sm" | "md" | "lg";
  disabled?: boolean;
  onClick?: () => void;
  /** Optional leading icon element (16-18px svg) */
  icon?: React.ReactNode;
}
