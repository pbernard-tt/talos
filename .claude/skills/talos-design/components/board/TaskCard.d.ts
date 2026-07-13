/**
 * TaskCard — the Kanban card on the Task Board. Composes AgentBadge and
 * RiskBadge (do not re-implement badge visuals here). `draggable` should be
 * false only for read-only contexts (e.g. a project-detail summary list).
 *
 * @startingPoint section="Board" subtitle="Kanban task card" viewport="280x160"
 */
export interface TaskCardData {
  title: string;
  projectName: string;
  priority: "LOW" | "MEDIUM" | "HIGH";
  agentKey?: string;
  riskLevel?: "NORMAL" | "HIGH";
  /** e.g. "Agent executing" — shown with a live pulse dot when a run is active */
  activeStepLabel?: string;
  blockedReason?: string;
}
export interface TaskCardProps {
  task: TaskCardData;
  onClick?: () => void;
  draggable?: boolean;
  onDragStart?: (e: React.DragEvent) => void;
  density?: "comfortable" | "compact";
}
