/**
 * StatusTimeline — the run-lifecycle strip on Run Detail: Workspace →
 * Agent → Tests → Review → Push → PR → Deploy. Step states are computed by
 * the caller from the run's exact state-machine status (plan §8.2) — never
 * invent an intermediate step.
 *
 * @startingPoint section="Data display" subtitle="Run state-machine progress strip" viewport="640x110"
 */
export interface TimelineStep {
  key: string;
  label: string;
  state: "COMPLETED" | "RUNNING" | "PENDING" | "FAILED" | "SKIPPED";
}
export interface StatusTimelineProps {
  steps: TimelineStep[];
}
