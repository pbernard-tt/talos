// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import {
  Approval,
  ApprovalsService,
  DashboardService,
  RunStatus,
  RunSummary,
  RunsService,
} from '../api';

const TERMINAL_RUN_STATUSES: RunStatus[] = [
  RunStatus.COMPLETED,
  RunStatus.FAILED,
  RunStatus.CANCELLED,
  RunStatus.REJECTED,
];

const RECENT_LIST_SIZE = 10;

/** One signal-based store per domain (Section 6.1). Section 15's Command Center: cards for active
 * runs, approvals waiting, failed builds, recently completed tasks (plan line 961), built entirely
 * from the existing GET /runs and GET /approvals endpoints -- no projectId filter, so this spans
 * every project. */
@Injectable({ providedIn: 'root' })
export class CommandCenterStore {
  private readonly runsService = inject(RunsService);
  private readonly approvalsService = inject(ApprovalsService);
  private readonly dashboardService = inject(DashboardService);

  private readonly activeRunsSignal = signal<RunSummary[]>([]);
  private readonly recentFailuresSignal = signal<RunSummary[]>([]);
  private readonly recentCompletionsSignal = signal<RunSummary[]>([]);
  private readonly pendingApprovalsSignal = signal<Approval[]>([]);
  private readonly dlqDepthSignal = signal<number | null>(null);
  private readonly loadingSignal = signal(false);
  private readonly errorSignal = signal<string | null>(null);

  readonly activeRuns = this.activeRunsSignal.asReadonly();
  readonly recentFailures = this.recentFailuresSignal.asReadonly();
  readonly recentCompletions = this.recentCompletionsSignal.asReadonly();
  readonly pendingApprovals = this.pendingApprovalsSignal.asReadonly();
  readonly dlqDepth = this.dlqDepthSignal.asReadonly();
  readonly loading = this.loadingSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();

  async load(): Promise<void> {
    this.loadingSignal.set(true);
    this.errorSignal.set(null);
    try {
      const [runsPage, approvalsPage] = await Promise.all([
        firstValueFrom(this.runsService.listRuns({ size: 100 })),
        firstValueFrom(this.approvalsService.listApprovals({ status: 'PENDING', size: 50 })),
      ]);
      const runsByRecency = [...runsPage.content].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
      this.activeRunsSignal.set(runsByRecency.filter((run) => !TERMINAL_RUN_STATUSES.includes(run.status)));
      this.recentFailuresSignal.set(
        runsByRecency.filter((run) => run.status === RunStatus.FAILED).slice(0, RECENT_LIST_SIZE),
      );
      this.recentCompletionsSignal.set(
        runsByRecency.filter((run) => run.status === RunStatus.COMPLETED).slice(0, RECENT_LIST_SIZE),
      );
      this.pendingApprovalsSignal.set(approvalsPage.content);
    } catch {
      this.errorSignal.set('Could not load the Command Center.');
    } finally {
      this.loadingSignal.set(false);
    }

    /** DLQ depth (review gap #11) is fetched independently -- a missing/erroring metric shouldn't
     * block the rest of the dashboard. */
    try {
      const { depth } = await firstValueFrom(this.dashboardService.getDlqDepth());
      this.dlqDepthSignal.set(depth);
    } catch {
      this.dlqDepthSignal.set(null);
    }
  }
}
