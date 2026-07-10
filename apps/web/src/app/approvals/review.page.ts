import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatToolbarModule } from '@angular/material/toolbar';

import {
  ApprovalActionDialogComponent,
  ApprovalActionDialogData,
  ApprovalActionDialogResult,
} from './approval-action-dialog.component';
import { ApprovalStore } from './approval.store';

@Component({
  selector: 'app-review-page',
  imports: [
    RouterLink,
    MatButtonModule,
    MatChipsModule,
    MatListModule,
    MatProgressSpinnerModule,
    MatToolbarModule,
  ],
  templateUrl: './review.page.html',
  styleUrl: './review.page.scss',
})
export class ReviewPage implements OnInit, OnDestroy {
  protected readonly store = inject(ApprovalStore);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

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
