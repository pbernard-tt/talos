// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Injectable, inject, signal } from '@angular/core';
import { catchError, firstValueFrom, of } from 'rxjs';

import { ApprovalsService, CostsService, MonthlyCostSummary, ProjectSummary, ProjectsService, RunStatus, RunsService } from '../api';

export interface ProjectCostRow {
  project: ProjectSummary;
  summaries: MonthlyCostSummary[];
}

/** Talos.dc.html's "Costs & Insights" screen. There's no global costs endpoint (Section 16 Phase 14
 * only added per-project GET /projects/{id}/costs/monthly), so this fans out one call per project
 * and aggregates client-side -- same shape as DeploymentsStore. Failure/rejection rates are
 * computed from a real GET /runs and GET /approvals sample (size 200), not fabricated; average
 * run duration is intentionally omitted because RunSummary carries no start/end timestamps to
 * compute it from. */
@Injectable({ providedIn: 'root' })
export class CostsStore {
  private readonly projectsService = inject(ProjectsService);
  private readonly costsService = inject(CostsService);
  private readonly runsService = inject(RunsService);
  private readonly approvalsService = inject(ApprovalsService);

  private readonly rowsSignal = signal<ProjectCostRow[]>([]);
  private readonly failureRateSignal = signal<number | null>(null);
  private readonly rejectionRateSignal = signal<number | null>(null);
  private readonly loadingSignal = signal(false);
  private readonly errorSignal = signal<string | null>(null);

  readonly rows = this.rowsSignal.asReadonly();
  readonly failureRate = this.failureRateSignal.asReadonly();
  readonly rejectionRate = this.rejectionRateSignal.asReadonly();
  readonly loading = this.loadingSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();

  async load(): Promise<void> {
    this.loadingSignal.set(true);
    this.errorSignal.set(null);
    try {
      const projectPage = await firstValueFrom(this.projectsService.listProjects({ size: 200 }));
      const rows = await Promise.all(
        projectPage.content.map(async (project) => ({
          project,
          summaries: await firstValueFrom(
            this.costsService.getProjectMonthlyCosts({ id: project.id }).pipe(catchError(() => of([]))),
          ),
        })),
      );
      this.rowsSignal.set(rows);
    } catch {
      this.errorSignal.set('Could not load costs.');
    } finally {
      this.loadingSignal.set(false);
    }

    /** Independent of the cost load -- a missing rate shouldn't block the cost breakdown. */
    try {
      const runsPage = await firstValueFrom(this.runsService.listRuns({ size: 200 }));
      const terminal = runsPage.content.filter((run) =>
        (['COMPLETED', 'FAILED'] as RunStatus[]).includes(run.status),
      );
      this.failureRateSignal.set(
        terminal.length > 0 ? terminal.filter((run) => run.status === 'FAILED').length / terminal.length : null,
      );
    } catch {
      this.failureRateSignal.set(null);
    }

    try {
      const approvalsPage = await firstValueFrom(this.approvalsService.listApprovals({ size: 200 }));
      const decided = approvalsPage.content.filter((approval) =>
        (['APPROVED', 'REJECTED'] as const).includes(approval.status as 'APPROVED' | 'REJECTED'),
      );
      this.rejectionRateSignal.set(
        decided.length > 0 ? decided.filter((approval) => approval.status === 'REJECTED').length / decided.length : null,
      );
    } catch {
      this.rejectionRateSignal.set(null);
    }
  }
}
