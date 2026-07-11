import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { Approval, ApprovalsService, DashboardService, PageApproval, PageRunSummary, RunSummary, RunsService } from '../api';
import { CommandCenterStore } from './command-center.store';

function makeRun(overrides: Partial<RunSummary> = {}): RunSummary {
  return {
    id: 'run-1',
    taskId: 'task-1',
    status: 'RUNNING_AGENT',
    agentKey: 'claude-code',
    createdAt: '2026-07-10T00:00:00Z',
    ...overrides,
  };
}

function makeApproval(overrides: Partial<Approval> = {}): Approval {
  return {
    id: 'approval-1',
    taskId: 'task-1',
    runId: 'run-1',
    approvalType: 'RUN_RESULT',
    requestedAction: 'PUSH',
    status: 'PENDING',
    createdAt: '2026-07-10T00:00:00Z',
    ...overrides,
  };
}

describe('CommandCenterStore', () => {
  function setup(runs: RunSummary[], approvals: Approval[], dlqDepth = 0) {
    const runsPage: PageRunSummary = { content: runs, page: 0, size: runs.length, totalElements: runs.length };
    const approvalsPage: PageApproval = { content: approvals, page: 0, size: approvals.length, totalElements: approvals.length };
    TestBed.configureTestingModule({
      providers: [
        { provide: RunsService, useValue: { listRuns: vi.fn().mockReturnValue(of(runsPage)) } },
        { provide: ApprovalsService, useValue: { listApprovals: vi.fn().mockReturnValue(of(approvalsPage)) } },
        { provide: DashboardService, useValue: { getDlqDepth: vi.fn().mockReturnValue(of({ depth: dlqDepth })) } },
      ],
    });
    return TestBed.inject(CommandCenterStore);
  }

  it('splits runs into active/failed/completed buckets and sorts each by recency', async () => {
    const store = setup(
      [
        makeRun({ id: 'old-active', status: 'RUNNING_AGENT', createdAt: '2026-07-01T00:00:00Z' }),
        makeRun({ id: 'new-active', status: 'WAITING_APPROVAL', createdAt: '2026-07-05T00:00:00Z' }),
        makeRun({ id: 'old-failure', status: 'FAILED', createdAt: '2026-07-01T00:00:00Z' }),
        makeRun({ id: 'new-failure', status: 'FAILED', createdAt: '2026-07-06T00:00:00Z' }),
        makeRun({ id: 'completed', status: 'COMPLETED', createdAt: '2026-07-03T00:00:00Z' }),
        makeRun({ id: 'cancelled', status: 'CANCELLED', createdAt: '2026-07-04T00:00:00Z' }),
      ],
      [],
    );

    await store.load();

    expect(store.activeRuns().map((r) => r.id)).toEqual(['new-active', 'old-active']);
    expect(store.recentFailures().map((r) => r.id)).toEqual(['new-failure', 'old-failure']);
    expect(store.recentCompletions().map((r) => r.id)).toEqual(['completed']);
  });

  it('exposes pending approvals as returned by the API', async () => {
    const approval = makeApproval();
    const store = setup([], [approval]);

    await store.load();

    expect(store.pendingApprovals()).toEqual([approval]);
  });

  it('a missing DLQ depth does not fail the rest of the dashboard load', async () => {
    TestBed.configureTestingModule({
      providers: [
        { provide: RunsService, useValue: { listRuns: vi.fn().mockReturnValue(of({ content: [], page: 0, size: 0, totalElements: 0 })) } },
        { provide: ApprovalsService, useValue: { listApprovals: vi.fn().mockReturnValue(of({ content: [], page: 0, size: 0, totalElements: 0 })) } },
        { provide: DashboardService, useValue: { getDlqDepth: vi.fn().mockReturnValue(throwError(() => new Error('unreachable'))) } },
      ],
    });
    const store = TestBed.inject(CommandCenterStore);

    await store.load();

    expect(store.error()).toBeNull();
    expect(store.dlqDepth()).toBeNull();
  });

  it('a failed runs/approvals fetch surfaces a load error', async () => {
    TestBed.configureTestingModule({
      providers: [
        { provide: RunsService, useValue: { listRuns: vi.fn().mockReturnValue(throwError(() => new Error('down'))) } },
        { provide: ApprovalsService, useValue: { listApprovals: vi.fn().mockReturnValue(of({ content: [], page: 0, size: 0, totalElements: 0 })) } },
        { provide: DashboardService, useValue: { getDlqDepth: vi.fn().mockReturnValue(of({ depth: 0 })) } },
      ],
    });
    const store = TestBed.inject(CommandCenterStore);

    await store.load();

    expect(store.error()).toBe('Could not load the Command Center.');
  });
});
