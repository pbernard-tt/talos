// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Injectable, inject, signal } from '@angular/core';
import { catchError, firstValueFrom, of } from 'rxjs';

import { ProjectEnvironment, ProjectSummary, ProjectsService } from '../api';

export interface DeploymentRow {
  project: ProjectSummary;
  environment: ProjectEnvironment;
}

/** Talos.dc.html's "Deployments" screen -- every deploy target across every project. There's no
 * global list-deployments endpoint (Section 16 Phase 10 only added a per-project
 * GET /projects/{id}/environments), so this fans out one call per project and flattens the
 * results. Triggering/rolling back a deploy stays on the run detail page (POST /runs/{id}/deploy
 * needs a specific completed run, which this aggregate view doesn't carry) -- each card links to
 * the environment's lastRunId instead of duplicating that flow. */
@Injectable({ providedIn: 'root' })
export class DeploymentsStore {
  private readonly projectsService = inject(ProjectsService);

  private readonly rowsSignal = signal<DeploymentRow[]>([]);
  private readonly loadingSignal = signal(false);
  private readonly errorSignal = signal<string | null>(null);

  readonly rows = this.rowsSignal.asReadonly();
  readonly loading = this.loadingSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();

  async load(): Promise<void> {
    this.loadingSignal.set(true);
    this.errorSignal.set(null);
    try {
      const page = await firstValueFrom(this.projectsService.listProjects({ size: 200 }));
      const perProject = await Promise.all(
        page.content.map(async (project) => {
          const environments = await firstValueFrom(
            this.projectsService.listProjectEnvironments({ id: project.id }).pipe(catchError(() => of([]))),
          );
          return environments.map((environment) => ({ project, environment }));
        }),
      );
      this.rowsSignal.set(perProject.flat());
    } catch {
      this.errorSignal.set('Could not load deployments.');
    } finally {
      this.loadingSignal.set(false);
    }
  }
}
