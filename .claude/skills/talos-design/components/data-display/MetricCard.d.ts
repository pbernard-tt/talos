/**
 * MetricCard — a single interactive number used in the Command Center's
 * 8-up metrics row (Active projects, Running agents, Pending approvals, …).
 * Clicking should always navigate to the filtered list behind the number.
 *
 * @startingPoint section="Data display" subtitle="Interactive metric tile" viewport="220x110"
 */
export interface MetricCardProps {
  label: string;
  value: string | number;
  /** Value text color — usually var(--text-primary), or a status color for at-risk metrics */
  color?: string;
  onClick?: () => void;
}
