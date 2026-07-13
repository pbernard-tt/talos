// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { RunStatus } from '../api';
import { ProjectStore } from '../projects/project.store';
import { TaskStore } from '../tasks/task.store';
import { AgentBadgeComponent } from '../shared/badges/agent-badge.component';
import { StatusBadgeComponent } from '../shared/badges/status-badge.component';
import { runStatusTone } from '../shared/badges/status-tone';
import { RunStore } from './run.store';

const STATUS_OPTIONS: RunStatus[] = [
  'CREATED',
  'QUEUED',
  'PREPARING_WORKSPACE',
  'RUNNING_AGENT',
  'RUNNING_TESTS',
  'REVIEWING',
  'WAITING_APPROVAL',
  'APPROVED',
  'REJECTED',
  'COMPLETED',
  'FAILED',
  'CANCELLED',
];

/** Talos.dc.html's "Agent Runs" screen -- every run across every project (Section 15). RunSummary
 * only carries taskId/agentKey/status/createdAt, so task title and project name are joined
 * client-side from the already-loaded TaskStore/ProjectStore rather than fetching a RunDetail per
 * row (which would be an N+1 call for a list this size). */
@Component({
  selector: 'app-runs-list-page',
  imports: [DatePipe, MatProgressSpinnerModule, AgentBadgeComponent, StatusBadgeComponent],
  templateUrl: './runs-list.page.html',
  styleUrl: './runs-list.page.scss',
})
export class RunsListPage implements OnInit {
  protected readonly store = inject(RunStore);
  protected readonly projectStore = inject(ProjectStore);
  protected readonly taskStore = inject(TaskStore);
  private readonly router = inject(Router);

  protected readonly statusOptions = STATUS_OPTIONS;
  protected readonly runStatusTone = runStatusTone;

  protected readonly filterProjectId = signal('');
  protected readonly filterStatus = signal<RunStatus | ''>('');

  private readonly taskById = computed(() => new Map(this.taskStore.tasks().map((task) => [task.id, task])));
  private readonly projectById = computed(
    () => new Map(this.projectStore.projects().map((project) => [project.id, project])),
  );

  protected readonly rows = computed(() =>
    this.store.runsList().map((run) => {
      const task = this.taskById().get(run.taskId);
      const project = task ? this.projectById().get(task.projectId) : undefined;
      return {
        run,
        taskTitle: task?.title ?? run.taskId,
        projectName: project?.name ?? '—',
      };
    }),
  );

  ngOnInit(): void {
    if (this.projectStore.projects().length === 0) {
      void this.projectStore.load();
    }
    if (this.taskStore.tasks().length === 0) {
      void this.taskStore.load();
    }
    void this.reload();
  }

  reload(): void {
    void this.store.listAll({
      projectId: this.filterProjectId() || undefined,
      status: this.filterStatus() || undefined,
    });
  }

  setProjectFilter(value: string): void {
    this.filterProjectId.set(value);
    this.reload();
  }

  setStatusFilter(value: string): void {
    this.filterStatus.set(value as RunStatus | '');
    this.reload();
  }

  openRun(runId: string): void {
    void this.router.navigate(['/runs', runId]);
  }
}
