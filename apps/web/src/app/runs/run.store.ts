import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { Diff, LogEntry, RunDetail, RunsService } from '../api';
import { RunEventStreamService, RunStreamEvent } from './run-event-stream.service';

/** One signal-based store per domain (Section 6.1); components read signals and call store methods. */
@Injectable({ providedIn: 'root' })
export class RunStore {
  private readonly runsService = inject(RunsService);
  private readonly streamService = inject(RunEventStreamService);

  private readonly runSignal = signal<RunDetail | null>(null);
  private readonly diffSignal = signal<Diff | null>(null);
  private readonly logsSignal = signal<LogEntry[]>([]);
  private readonly loadingSignal = signal(false);
  private readonly errorSignal = signal<string | null>(null);

  readonly run = this.runSignal.asReadonly();
  readonly diff = this.diffSignal.asReadonly();
  readonly logs = this.logsSignal.asReadonly();
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
  }

  async cancel(runId: string): Promise<void> {
    const run = await firstValueFrom(this.runsService.cancelRun({ id: runId }));
    this.runSignal.update((current) => (current ? { ...current, status: run.status } : current));
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
  }
}
