/**
 * Input — single-line text input. Focus state should apply the shared
 * --shadow-glow ring at the consuming layer (border-color: var(--accent);
 * box-shadow: var(--shadow-glow)) rather than the browser default outline.
 *
 * @startingPoint section="Core" subtitle="Text input with inline error state" viewport="360x90"
 */
export interface InputProps {
  value: string;
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  placeholder?: string;
  type?: "text" | "email" | "password" | "search";
  disabled?: boolean;
  /** Inline validation message; also switches the border to error color */
  error?: string;
}
