import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { Approval, ApprovalDetail, ApprovalStatus, ApprovalsService, RunsService } from '../api';

/** One signal-based store per domain (Section 6.1); backs the /review/:runId Review Center page
 * (detail/action methods) and the /approvals inbox page (list()). */
@Injectable({ providedIn: 'root' })
export class ApprovalStore {
  private readonly approvalsService = inject(ApprovalsService);
  private readonly runsService = inject(RunsService);

  private readonly detailSignal = signal<ApprovalDetail | null>(null);
  private readonly diffTextSignal = signal<string | undefined>(undefined);
  private readonly approvalsSignal = signal<Approval[]>([]);
  private readonly loadingSignal = signal(false);
  private readonly errorSignal = signal<string | null>(null);

  readonly detail = this.detailSignal.asReadonly();
  readonly diffText = this.diffTextSignal.asReadonly();
  readonly approvals = this.approvalsSignal.asReadonly();
  readonly loading = this.loadingSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();

  /** GET /approvals, optionally filtered by status -- backs the approvals inbox page. */
  async list(status?: ApprovalStatus): Promise<void> {
    this.loadingSignal.set(true);
    this.errorSignal.set(null);
    try {
      const page = await firstValueFrom(this.approvalsService.listApprovals({ status, size: 50 }));
      this.approvalsSignal.set(page.content);
    } catch {
      this.errorSignal.set('Could not load approvals.');
    } finally {
      this.loadingSignal.set(false);
    }
  }

  /** GET /approvals?runId= (Phase 8's runId filter) -- the Review page only knows the run, not the approval id. */
  async loadForRun(runId: string): Promise<void> {
    this.loadingSignal.set(true);
    this.errorSignal.set(null);
    try {
      const [page, diff] = await Promise.all([
        firstValueFrom(this.approvalsService.listApprovals({ runId, size: 1 })),
        firstValueFrom(this.runsService.getRunDiff({ id: runId })),
      ]);
      this.diffTextSignal.set(diff.diff);
      const approval = page.content[0];
      if (!approval) {
        this.errorSignal.set('No approval found for this run.');
        return;
      }
      const detail = await firstValueFrom(this.approvalsService.getApproval({ id: approval.id }));
      this.detailSignal.set(detail);
    } catch {
      this.errorSignal.set('Could not load the approval.');
    } finally {
      this.loadingSignal.set(false);
    }
  }

  async approve(id: string, notes?: string): Promise<void> {
    const approval = await firstValueFrom(this.approvalsService.approveApproval({ id, approveRequest: { notes } }));
    this.detailSignal.update((current) => (current ? { ...current, approval } : current));
  }

  async reject(id: string, notes: string): Promise<void> {
    const approval = await firstValueFrom(this.approvalsService.rejectApproval({ id, rejectRequest: { notes } }));
    this.detailSignal.update((current) => (current ? { ...current, approval } : current));
  }

  async requestChanges(id: string, notes: string): Promise<void> {
    const approval = await firstValueFrom(
      this.approvalsService.requestApprovalChanges({ id, requestChangesRequest: { notes } }),
    );
    this.detailSignal.update((current) => (current ? { ...current, approval } : current));
  }

  clear(): void {
    this.detailSignal.set(null);
    this.diffTextSignal.set(undefined);
    this.errorSignal.set(null);
  }
}
