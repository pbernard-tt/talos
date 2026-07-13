/**
 * StatusBadge — a small pill showing a task/run/approval/deploy status with a
 * tone-colored dot. Tone is chosen by the caller from the entity's status
 * (never invented per-component); label is the raw enum value from the plan
 * (e.g. "WAITING_APPROVAL", "COMPLETED").
 *
 * @startingPoint section="Badges" subtitle="Status pill with tone dot" viewport="160x40"
 */
export interface StatusBadgeProps {
  /** Raw status/enum text to display, e.g. "COMPLETED", "WAITING_APPROVAL" */
  label: string;
  /** Visual tone — pick based on the status's meaning, not its literal string */
  tone?: "neutral" | "purple" | "success" | "warning" | "error" | "info";
}
