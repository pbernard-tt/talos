/**
 * RunRow — one row of the Agent Runs table. Composes AgentBadge,
 * AuthModeBadge, and StatusBadge (do not re-implement their visuals here).
 *
 * @startingPoint section="Runs" subtitle="Agent Runs table row" viewport="900x60"
 */
export interface RunRowData {
  id: string;
  taskTitle: string;
  projectName: string;
  agentKey: string;
  authMode: "api_key" | "subscription_local" | "n/a";
  duration: string;
  cost: string;
  status: string;
  statusTone: "neutral" | "purple" | "success" | "warning" | "error" | "info";
}
export interface RunRowProps {
  run: RunRowData;
  onClick?: () => void;
}
