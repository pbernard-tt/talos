import { Injectable, inject, signal } from '@angular/core';
import { catchError, firstValueFrom, of } from 'rxjs';

import { Approval, ApprovalsService, Diff, LogEntry, ProjectEnvironment, PullRequest, RunDetail, RunsService } from '../api';
import { RunEventStreamService, RunStreamEvent } from './run-event-stream.service';

/** One signal-based store per domain (Section 6.1); components read signals and call store methods. */
@Injectable({ providedIn: 'root' })
export class RunStore {
  private readonly runsService = inject(RunsService);
  private readonly approvalsService = inject(ApprovalsService);
  private readonly streamService = inject(RunEventStreamService);

  private readonly runSignal = signal<RunDetail | null>(null);
  private readonly diffSignal = signal<Diff | null>(null);
  private readonly logsSignal = signal<LogEntry[]>([]);
  private readonly pullRequestSignal = signal<PullRequest | null>(null);
  private readonly deployStatusSignal = signal<ProjectEnvironment | null>(null);
  private readonly pendingDeployApprovalSignal = signal<Approval | null>(null);
  private readonly loadingSignal = signal(false);
  private readonly errorSignal = signal<string | null>(null);

  readonly run = this.runSignal.asReadonly();
  readonly diff = this.diffSignal.asReadonly();
  readonly logs = this.logsSignal.asReadonly();
  readonly pullRequest = this.pullRequestSignal.asReadonly();
  readonly deployStatus = this.deployStatusSignal.asReadonly();
  readonly pendingDeployApproval = this.pendingDeployApprovalSignal.asReadonly();
  readonly loading = this.loadingSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();

  private streamAbort: AbortController | null = null;

  async load(runId: string): Promise<void> {
    this.loadingSignal.set(true);
    this.errorSignal.set(null);
    try {
      await this.refresh(runId);
    } catch {
      this.errorSignal.set('Could not load the run.');
    } finally {
      this.loadingSignal.set(false);
    }
  }

  /** Opens the Section 10.3 SSE stream, which backfills full log history before relaying live events. */
  connectLiveUpdates(runId: string): void {
    this.disconnectLiveUpdates();
    const controller = new AbortController();
    this.streamAbort = controller;
    void this.streamService.listen(runId, (event) => this.handleEvent(runId, event), controller.signal);
  }

  disconnectLiveUpdates(): void {
    this.streamAbort?.abort();
    this.streamAbort = null;
  }

  clear(): void {
    this.disconnectLiveUpdates();
    this.runSignal.set(null);
    this.diffSignal.set(null);
    this.logsSignal.set([]);
    this.pullRequestSignal.set(null);
    this.deployStatusSignal.set(null);
    this.pendingDeployApprovalSignal.set(null);
  }

  async cancel(runId: string): Promise<void> {
    const run = await firstValueFrom(this.runsService.cancelRun({ id: runId }));
    this.runSignal.update((current) => (current ? { ...current, status: run.status } : current));
  }

  /** POST /runs/{id}/deploy (Phase 10): either triggers immediately or leaves a PENDING DEPLOY approval to act on. */
  async requestDeploy(runId: string): Promise<void> {
    const result = await firstValueFrom(this.runsService.deployRun({ id: runId }));
    this.deployStatusSignal.set(result.environment ?? null);
    this.pendingDeployApprovalSignal.set(result.approvalRequired ? (result.approval ?? null) : null);
  }

  async approveDeploy(approvalId: string, notes?: string): Promise<void> {
    await firstValueFrom(this.approvalsService.approveApproval({ id: approvalId, approveRequest: { notes } }));
    this.pendingDeployApprovalSignal.set(null);
    await this.refreshDeployStatus(this.runSignal()?.id);
  }

  async rejectDeploy(approvalId: string, notes: string): Promise<void> {
    await firstValueFrom(this.approvalsService.rejectApproval({ id: approvalId, rejectRequest: { notes } }));
    this.pendingDeployApprovalSignal.set(null);
  }

  private async refreshDeployStatus(runId: string | undefined): Promise<void> {
    if (!runId) {
      return;
    }
    const status = await firstValueFrom(
      this.runsService.getRunDeployStatus({ id: runId }).pipe(catchError(() => of(null))),
    );
    this.deployStatusSignal.set(status);
  }

  private handleEvent(runId: string, event: RunStreamEvent): void {
    if (event.type === 'log') {
      this.logsSignal.update((logs) => [...logs, event.data as LogEntry]);
    } else {
      // status/step events don't carry the full run shape -- refetch to stay in sync with the
      // server's merge logic (task mapping, review scan results, etc.) rather than duplicating it.
      void this.refresh(runId);
    }
  }

  private async refresh(runId: string): Promise<void> {
    const [run, diff] = await Promise.all([
      firstValueFrom(this.runsService.getRun({ id: runId })),
      firstValueFrom(this.runsService.getRunDiff({ id: runId })),
    ]);
    this.runSignal.set(run);
    this.diffSignal.set(diff);

    // Only COMPLETED runs have a PR (Section 8.2's APPROVED -> COMPLETED, commit/push/PR); 404
    // before that is expected, not an error -- surface it as "no PR yet" rather than failing load().
    if (run.status === 'COMPLETED') {
      const pullRequest = await firstValueFrom(
        this.runsService.getRunPullRequest({ id: runId }).pipe(catchError(() => of(null))),
      );
      this.pullRequestSignal.set(pullRequest);
      await this.refreshDeployStatus(runId);
    } else {
      this.pullRequestSignal.set(null);
      this.deployStatusSignal.set(null);
    }
  }
}
