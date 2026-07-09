import { Component, inject } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { CreateProjectRequest } from '../api';

@Component({
  selector: 'app-project-form-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule],
  templateUrl: './project-form-dialog.component.html',
  styleUrl: './project-form-dialog.component.scss',
})
export class ProjectFormDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<ProjectFormDialogComponent>);
  private readonly formBuilder = inject(FormBuilder);
  protected readonly data = inject<{ project?: CreateProjectRequest } | null>(MAT_DIALOG_DATA, {
    optional: true,
  });

  readonly form = this.formBuilder.nonNullable.group({
    name: ['', Validators.required],
    repoUrl: ['', Validators.required],
    defaultBranch: [''],
    stackType: ['', Validators.required],
  });

  save(): void {
    if (this.form.invalid) {
      return;
    }
    const value = this.form.getRawValue();
    const request: CreateProjectRequest = {
      name: value.name,
      repoUrl: value.repoUrl,
      stackType: value.stackType,
      ...(value.defaultBranch ? { defaultBranch: value.defaultBranch } : {}),
    };
    this.dialogRef.close(request);
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
