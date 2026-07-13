// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { describe, expect, it } from 'vitest';

import { RunSummary, TaskDetail } from '../api';
import { TaskDrawerComponent } from './task-drawer.component';

function makeDetail(runs: Partial<RunSummary>[]): TaskDetail {
  return {
    id: 'task-1',
    projectId: 'project-1',
    title: 'Add /hello endpoint',
    source: 'DASHBOARD',
    status: 'READY',
    priority: 'MEDIUM',
    riskLevel: 'NORMAL',
    boardPosition: 0,
    createdAt: '2026-07-09T00:00:00Z',
    updatedAt: '2026-07-09T00:00:00Z',
    runs: runs.map((run, i) => ({
      id: `run-${i}`,
      agentKey: 'custom-shell',
      status: 'COMPLETED',
      createdAt: '2026-07-09T00:00:00Z',
      ...run,
    })) as RunSummary[],
  } as TaskDetail;
}

describe('TaskDrawerComponent.hasActiveRun', () => {
  it('is false with no runs or only terminal runs', () => {
    const component = new TaskDrawerComponent();
    component.task = makeDetail([]);
    expect(component.hasActiveRun()).toBe(false);

    component.task = makeDetail([{ status: 'COMPLETED' }, { status: 'FAILED' }, { status: 'REJECTED' }]);
    expect(component.hasActiveRun()).toBe(false);
  });

  it('is true while any run is in a non-terminal state (start-run would 409)', () => {
    const component = new TaskDrawerComponent();
    component.task = makeDetail([{ status: 'COMPLETED' }, { status: 'WAITING_APPROVAL' }]);
    expect(component.hasActiveRun()).toBe(true);
  });
});
