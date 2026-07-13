// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { IntegrationCreateRequest } from '../api';

@Component({
  selector: 'app-integration-form-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule],
  templateUrl: './integration-form-dialog.component.html',
  styleUrl: './integration-form-dialog.component.scss',
})
export class IntegrationFormDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<IntegrationFormDialogComponent>);
  private readonly formBuilder = inject(FormBuilder);

  readonly jsonError = signal<string | null>(null);

  readonly form = this.formBuilder.nonNullable.group({
    type: ['', Validators.required],
    name: ['', Validators.required],
    configJson: [''],
    secret: [''],
    authMode: [''],
  });

  save(): void {
    this.jsonError.set(null);
    if (this.form.invalid) {
      return;
    }
    const value = this.form.getRawValue();
    let configJson: Record<string, unknown> | undefined;
    if (value.configJson.trim()) {
      try {
        configJson = JSON.parse(value.configJson);
      } catch {
        this.jsonError.set('configJson must be valid JSON.');
        return;
      }
    }
    const request: IntegrationCreateRequest = {
      type: value.type,
      name: value.name,
      ...(configJson ? { configJson } : {}),
      ...(value.secret ? { secret: value.secret } : {}),
      ...(value.authMode ? { authMode: value.authMode } : {}),
    };
    this.dialogRef.close(request);
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
