// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';

import { CreateUserRequest, Role } from '../api';

const ROLES: Role[] = ['OWNER', 'MAINTAINER', 'REVIEWER', 'VIEWER'];

@Component({
  selector: 'app-user-form-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule],
  templateUrl: './user-form-dialog.component.html',
  styleUrl: './user-form-dialog.component.scss',
})
export class UserFormDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<UserFormDialogComponent>);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly roles = ROLES;

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    name: ['', Validators.required],
    password: ['', [Validators.required, Validators.minLength(8)]],
    role: ['VIEWER' as Role, Validators.required],
  });

  save(): void {
    if (this.form.invalid) {
      return;
    }
    const request: CreateUserRequest = this.form.getRawValue();
    this.dialogRef.close(request);
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
