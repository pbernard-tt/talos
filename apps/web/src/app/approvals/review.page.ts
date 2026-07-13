// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';

import {
  ApprovalActionDialogComponent,
  ApprovalActionDialogData,
  ApprovalActionDialogResult,
} from './approval-action-dialog.component';
import { ApprovalStore } from './approval.store';
import { AuthStore } from '../core/auth/auth.store';
import { StatusBadgeComponent } from '../shared/badges/status-badge.component';
import { approvalStatusTone, reviewStatusTone, testStatusTone } from '../shared/badges/status-tone';
import { FileChangeRowComponent } from '../shared/file-change-row/file-change-row.component';
import { IconComponent } from '../shared/icon/icon.component';

@Component({
  selector: 'app-review-page',
  imports: [
    RouterLink,
    MatButtonModule,
    MatProgressSpinnerModule,
    StatusBadgeComponent,
    FileChangeRowComponent,
    IconComponent,
  ],
  templateUrl: './review.page.html',
  styleUrl: './review.page.scss',
})
export class ReviewPage implements OnInit, OnDestroy {
  protected readonly store = inject(ApprovalStore);
  protected readonly authStore = inject(AuthStore);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly approvalStatusTone = approvalStatusTone;
  protected readonly reviewStatusTone = reviewStatusTone;
  protected readonly testStatusTone = testStatusTone;

  ngOnInit(): void {
    const runId = this.route.snapshot.paramMap.get('runId');
    if (runId) {
      void this.store.loadForRun(runId);
    }
  }

  ngOnDestroy(): void {
    this.store.clear();
  }

  approve(): void {
    const approvalId = this.store.detail()?.approval.id;
    if (!approvalId) {
      return;
    }
    this.openDialog({
      title: 'Approve run',
      message: 'Approving marks this run APPROVED. Push/PR happens automatically once that flow ships (Phase 9).',
      notesRequired: false,
      confirmLabel: 'Approve',
    }).subscribe((result) => {
      if (!result) {
        return;
      }
      this.store
        .approve(approvalId, result.notes)
        .then(() => this.snackBar.open('Run approved.', 'Dismiss', { duration: 4000 }))
        .catch(() => this.snackBar.open('Could not approve the run.', 'Dismiss', { duration: 4000 }));
    });
  }

  reject(): void {
    const approvalId = this.store.detail()?.approval.id;
    if (!approvalId) {
      return;
    }
    this.openDialog({
      title: 'Reject run',
      message: 'Rejecting returns the task to Ready for rework. Nothing is pushed.',
      notesRequired: true,
      confirmLabel: 'Reject',
    }).subscribe((result) => {
      if (!result?.notes) {
        return;
      }
      this.store
        .reject(approvalId, result.notes)
        .then(() => this.snackBar.open('Run rejected.', 'Dismiss', { duration: 4000 }))
        .catch(() => this.snackBar.open('Could not reject the run.', 'Dismiss', { duration: 4000 }));
    });
  }

  requestChanges(): void {
    const approvalId = this.store.detail()?.approval.id;
    if (!approvalId) {
      return;
    }
    this.openDialog({
      title: 'Request changes',
      message: 'Requesting changes returns the task to Ready with your notes attached for the next run.',
      notesRequired: true,
      confirmLabel: 'Request changes',
    }).subscribe((result) => {
      if (!result?.notes) {
        return;
      }
      this.store
        .requestChanges(approvalId, result.notes)
        .then(() => this.snackBar.open('Changes requested.', 'Dismiss', { duration: 4000 }))
        .catch(() => this.snackBar.open('Could not request changes.', 'Dismiss', { duration: 4000 }));
    });
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
