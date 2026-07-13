// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';

export interface ApprovalActionDialogData {
  title: string;
  message: string;
  notesRequired: boolean;
  confirmLabel: string;
}

export interface ApprovalActionDialogResult {
  notes?: string;
}

/** Section 15's UX rule: approval actions require a confirmation dialog restating what will happen. */
@Component({
  selector: 'app-approval-action-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule],
  templateUrl: './approval-action-dialog.component.html',
})
export class ApprovalActionDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<ApprovalActionDialogComponent, ApprovalActionDialogResult>);
  protected readonly data = inject<ApprovalActionDialogData>(MAT_DIALOG_DATA);
  private readonly formBuilder = inject(FormBuilder);

  readonly form = this.formBuilder.nonNullable.group({
    notes: ['', this.data.notesRequired ? [Validators.required] : []],
  });

  confirm(): void {
    if (this.form.invalid) {
      return;
    }
    this.dialogRef.close({ notes: this.form.getRawValue().notes || undefined });
  }

  cancel(): void {
    this.dialogRef.close(undefined);
  }
}
