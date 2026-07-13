/**
 * DiffBlock — one changed-file's unified diff, with a +/- summary and an
 * optional policy-risk callout. This is the MVP diff presentation named in
 * the plan (unified text; a rich side-by-side viewer is a later iteration).
 *
 * @startingPoint section="Runs" subtitle="Unified diff file block" viewport="640x220"
 */
export interface DiffFile {
  path: string;
  additions: number;
  deletions: number;
  hunkText: string;
  /** true when this file matched a policy.yaml / talos.yaml blocked/approval pattern */
  risk?: boolean;
  /** e.g. "Matches policy pattern **/migrations/**" */
  riskLabel?: string;
}
export interface DiffBlockProps {
  file: DiffFile;
}
