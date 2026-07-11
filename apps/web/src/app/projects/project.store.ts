import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import {
  CostsService,
  CreateProjectRequest,
  MonthlyCostSummary,
  Project,
  ProjectDetail,
  ProjectStatus,
  ProjectsService,
  RecommendationResponse,
  RecommendationsService,
  SyncConfigRequest,
  UpdateProjectRequest,
} from '../api';

/** One signal-based store per domain (Section 6.1); components read signals and call store methods. */
@Injectable({ providedIn: 'root' })
export class ProjectStore {
  private readonly projectsService = inject(ProjectsService);
  private readonly costsService = inject(CostsService);
  private readonly recommendationsService = inject(RecommendationsService);

  private readonly projectsSignal = signal<Project[]>([]);
  private readonly totalElementsSignal = signal(0);
  private readonly loadingSignal = signal(false);
  private readonly errorSignal = signal<string | null>(null);
  private readonly selectedProjectSignal = signal<ProjectDetail | null>(null);
  private readonly monthlyCostsSignal = signal<MonthlyCostSummary[]>([]);
  private readonly recommendationsSignal = signal<RecommendationResponse | null>(null);

  readonly projects = this.projectsSignal.asReadonly();
  readonly totalElements = this.totalElementsSignal.asReadonly();
  readonly loading = this.loadingSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();
  readonly selectedProject = this.selectedProjectSignal.asReadonly();
  readonly monthlyCosts = this.monthlyCostsSignal.asReadonly();
  readonly recommendations = this.recommendationsSignal.asReadonly();

  async load(status?: ProjectStatus): Promise<void> {
    this.loadingSignal.set(true);
    this.errorSignal.set(null);
    try {
      const page = await firstValueFrom(this.projectsService.listProjects({ status }));
      this.projectsSignal.set(page.content);
      this.totalElementsSignal.set(page.totalElements);
    } catch {
      this.errorSignal.set('Could not load projects.');
    } finally {
      this.loadingSignal.set(false);
    }
  }

  async create(request: CreateProjectRequest): Promise<Project> {
    const project = await firstValueFrom(
      this.projectsService.createProject({ createProjectRequest: request }),
    );
    this.projectsSignal.update((projects) => [project, ...projects]);
    return project;
  }

  async loadDetail(id: string): Promise<void> {
    this.loadingSignal.set(true);
    this.errorSignal.set(null);
    try {
      const detail = await firstValueFrom(this.projectsService.getProject({ id }));
      this.selectedProjectSignal.set(detail);
    } catch {
      this.errorSignal.set('Could not load the project.');
    } finally {
      this.loadingSignal.set(false);
    }
  }

  async update(id: string, request: UpdateProjectRequest): Promise<Project> {
    return firstValueFrom(this.projectsService.updateProject({ id, updateProjectRequest: request }));
  }

  async syncConfig(id: string, request: SyncConfigRequest): Promise<void> {
    await firstValueFrom(this.projectsService.syncProjectConfig({ id, syncConfigRequest: request }));
    await this.loadDetail(id);
  }

  /** Phase 14: monthly per-agent cost/usage totals (the dashboard cost widget). Failures are
   * swallowed to an empty list -- a missing cost widget shouldn't block viewing the project. */
  async loadMonthlyCosts(id: string): Promise<void> {
    try {
      const costs = await firstValueFrom(this.costsService.getProjectMonthlyCosts({ id }));
      this.monthlyCostsSignal.set(costs);
    } catch {
      this.monthlyCostsSignal.set([]);
    }
  }

  /** Phase 14: advisory-only suggested-agent/risk-flag signals. Never auto-applied -- the operator
   * always confirms an agent choice explicitly. */
  async loadRecommendations(id: string): Promise<void> {
    try {
      const recommendations = await firstValueFrom(this.recommendationsService.getProjectRecommendations({ id }));
      this.recommendationsSignal.set(recommendations);
    } catch {
      this.recommendationsSignal.set(null);
    }
  }
}
