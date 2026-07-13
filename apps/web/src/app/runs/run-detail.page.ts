// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, OnDestroy, OnInit, computed, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';

import {
  ApprovalActionDialogComponent,
  ApprovalActionDialogData,
  ApprovalActionDialogResult,
} from '../approvals/approval-action-dialog.component';
import { AuthStore } from '../core/auth/auth.store';
import { RunArtifact } from '../api';
import { AgentBadgeComponent } from '../shared/badges/agent-badge.component';
import { AuthModeBadgeComponent } from '../shared/badges/auth-mode-badge.component';
import { StatusBadgeComponent } from '../shared/badges/status-badge.component';
import { deployStatusTone, reviewStatusTone, runStatusTone, testStatusTone } from '../shared/badges/status-tone';
import { FileChangeRowComponent } from '../shared/file-change-row/file-change-row.component';
import { IconComponent } from '../shared/icon/icon.component';
import { StatusTimelineComponent, TimelineStep } from '../shared/status-timeline/status-timeline.component';
import { RunStore } from './run.store';

const CANCELLABLE_STATUSES = new Set([
  'QUEUED',
  'PREPARING_WORKSPACE',
  'RUNNING_AGENT',
  'RUNNING_TESTS',
  'REVIEWING',
  'WAITING_APPROVAL',
]);

@Component({
  selector: 'app-run-detail-page',
  imports: [
    RouterLink,
    MatButtonModule,
    MatProgressSpinnerModule,
    AgentBadgeComponent,
    AuthModeBadgeComponent,
    StatusBadgeComponent,
    StatusTimelineComponent,
    FileChangeRowComponent,
    IconComponent,
  ],
  templateUrl: './run-detail.page.html',
  styleUrl: './run-detail.page.scss',
})
export class RunDetailPage implements OnInit, OnDestroy {
  protected readonly store = inject(RunStore);
  protected readonly authStore = inject(AuthStore);
  private readonly route = inject(ActivatedRoute);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  private runId: string | null = null;

  protected readonly runStatusTone = runStatusTone;
  protected readonly testStatusTone = testStatusTone;
  protected readonly reviewStatusTone = reviewStatusTone;
  protected readonly deployStatusTone = deployStatusTone;

  protected readonly timelineSteps = computed<TimelineStep[]>(
    () =>
      this.store.run()?.steps.map((step) => ({
        key: step.id,
        label: step.stepType,
        state: step.status,
      })) ?? [],
  );

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      return;
    }
    this.runId = id;
    void this.store.load(id);
    this.store.connectLiveUpdates(id);
  }

  ngOnDestroy(): void {
    this.store.clear();
  }

  isCancellable(status: string): boolean {
    return CANCELLABLE_STATUSES.has(status);
  }

  cancel(): void {
    if (!this.runId) {
      return;
    }
    this.store
      .cancel(this.runId)
      .then(() => this.snackBar.open('Run cancelled.', 'Dismiss', { duration: 4000 }))
      .catch(() => this.snackBar.open('Could not cancel the run.', 'Dismiss', { duration: 4000 }));
  }

  /** POST /runs/{id}/deploy (Phase 10) -- disabled in the template unless the run is COMPLETED. */
  requestDeploy(): void {
    if (!this.runId) {
      return;
    }
    this.store
      .requestDeploy(this.runId)
      .then(() => {
        const message = this.store.pendingDeployApproval()
          ? 'Deploy requires approval.'
          : 'Deploy triggered.';
        this.snackBar.open(message, 'Dismiss', { duration: 4000 });
      })
      .catch(() => this.snackBar.open('Could not request a deploy.', 'Dismiss', { duration: 4000 }));
  }

  approveDeploy(): void {
    const approvalId = this.store.pendingDeployApproval()?.id;
    if (!approvalId) {
      return;
    }
    this.openDialog({
      title: 'Approve deploy',
      message: 'Approving triggers the deploy immediately via Dokploy.',
      notesRequired: false,
      confirmLabel: 'Approve',
    }).subscribe((result) => {
      if (!result) {
        return;
      }
      this.store
        .approveDeploy(approvalId, result.notes)
        .then(() => this.snackBar.open('Deploy approved and triggered.', 'Dismiss', { duration: 4000 }))
        .catch(() => this.snackBar.open('Could not approve the deploy.', 'Dismiss', { duration: 4000 }));
    });
  }

  rejectDeploy(): void {
    const approvalId = this.store.pendingDeployApproval()?.id;
    if (!approvalId) {
      return;
    }
    this.openDialog({
      title: 'Reject deploy',
      message: 'Rejecting cancels this deploy request. Nothing is deployed.',
      notesRequired: true,
      confirmLabel: 'Reject',
    }).subscribe((result) => {
      if (!result?.notes) {
        return;
      }
      this.store
        .rejectDeploy(approvalId, result.notes)
        .then(() => this.snackBar.open('Deploy rejected.', 'Dismiss', { duration: 4000 }))
        .catch(() => this.snackBar.open('Could not reject the deploy.', 'Dismiss', { duration: 4000 }));
    });
  }

  downloadArtifact(artifact: RunArtifact): void {
    if (!this.runId) {
      return;
    }
    this.store
      .downloadArtifact(this.runId, artifact)
      .catch(() => this.snackBar.open('Could not download the artifact.', 'Dismiss', { duration: 4000 }));
  }

  private openDialog(data: ApprovalActionDialogData) {
    return this.dialog
      .open<ApprovalActionDialogComponent, ApprovalActionDialogData, ApprovalActionDialogResult>(
        ApprovalActionDialogComponent,
        { data },
      )
      .afterClosed();
  }
}
