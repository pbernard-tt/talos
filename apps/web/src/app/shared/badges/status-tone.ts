// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

/** Tone vocabulary from the talos-design skill's StatusBadge (components/badges/StatusBadge.jsx) --
 * every status enum in the API maps into one of these six, never a raw hex. */
export type StatusTone = 'neutral' | 'purple' | 'success' | 'warning' | 'error' | 'info';

const RUN_STATUS_TONE: Record<string, StatusTone> = {
  CREATED: 'neutral',
  QUEUED: 'neutral',
  PREPARING_WORKSPACE: 'purple',
  RUNNING_AGENT: 'purple',
  RUNNING_TESTS: 'purple',
  REVIEWING: 'purple',
  WAITING_APPROVAL: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
  COMPLETED: 'success',
  FAILED: 'error',
  CANCELLED: 'neutral',
};

const TASK_STATUS_TONE: Record<string, StatusTone> = {
  BACKLOG: 'neutral',
  READY: 'info',
  RUNNING: 'purple',
  REVIEW: 'warning',
  BLOCKED: 'error',
  DONE: 'success',
  CANCELLED: 'neutral',
};

const APPROVAL_STATUS_TONE: Record<string, StatusTone> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
  CHANGES_REQUESTED: 'warning',
  EXPIRED: 'neutral',
};

const TEST_STATUS_TONE: Record<string, StatusTone> = {
  NOT_RUN: 'neutral',
  PASSED: 'success',
  FAILED: 'error',
  ERROR: 'error',
};

const REVIEW_STATUS_TONE: Record<string, StatusTone> = {
  CLEAN: 'success',
  RISK_FLAGGED: 'error',
};

const STEP_STATUS_TONE: Record<string, StatusTone> = {
  RUNNING: 'purple',
  COMPLETED: 'success',
  FAILED: 'error',
  SKIPPED: 'neutral',
};

const PROJECT_STATUS_TONE: Record<string, StatusTone> = {
  ACTIVE: 'success',
  ARCHIVED: 'neutral',
};

const DEPLOY_STATUS_TONE: Record<string, StatusTone> = {
  RUNNING: 'purple',
  SUCCEEDED: 'success',
  FAILED: 'error',
  NOT_DEPLOYED: 'neutral',
};

function lookup(map: Record<string, StatusTone>, status: string): StatusTone {
  return map[status] ?? 'neutral';
}

export const runStatusTone = (status: string): StatusTone => lookup(RUN_STATUS_TONE, status);
export const taskStatusTone = (status: string): StatusTone => lookup(TASK_STATUS_TONE, status);
export const approvalStatusTone = (status: string): StatusTone => lookup(APPROVAL_STATUS_TONE, status);
export const testStatusTone = (status: string): StatusTone => lookup(TEST_STATUS_TONE, status);
export const reviewStatusTone = (status: string): StatusTone => lookup(REVIEW_STATUS_TONE, status);
export const stepStatusTone = (status: string): StatusTone => lookup(STEP_STATUS_TONE, status);
export const projectStatusTone = (status: string): StatusTone => lookup(PROJECT_STATUS_TONE, status);
export const deployStatusTone = (status: string): StatusTone => lookup(DEPLOY_STATUS_TONE, status);

/** TaskCard.jsx's priority dot: HIGH=error, MEDIUM=warning, LOW=muted. Not a StatusBadge tone --
 * used directly as a CSS color token for the small priority indicator dot. */
export function priorityColorVar(priority: string): string {
  if (priority === 'HIGH') {
    return 'var(--status-error)';
  }
  if (priority === 'LOW') {
    return 'var(--text-muted)';
  }
  return 'var(--status-warning)';
}
