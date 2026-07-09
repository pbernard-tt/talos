import { Component, OnInit, inject } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { CreateTaskRequest, TaskPriority, TaskRiskLevel } from '../api';
import { ProjectStore } from '../projects/project.store';

@Component({
  selector: 'app-task-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './task-form-dialog.component.html',
  styleUrl: './task-form-dialog.component.scss',
})
export class TaskFormDialogComponent implements OnInit {
  private readonly dialogRef = inject(MatDialogRef<TaskFormDialogComponent>);
  private readonly formBuilder = inject(FormBuilder);
  protected readonly projectStore = inject(ProjectStore);
  protected readonly data = inject<{ projectId?: string } | null>(MAT_DIALOG_DATA, { optional: true });

  protected readonly priorities: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH'];
  protected readonly riskLevels: TaskRiskLevel[] = ['NORMAL', 'HIGH'];

  readonly form = this.formBuilder.nonNullable.group({
    projectId: [this.data?.projectId ?? '', Validators.required],
    title: ['', Validators.required],
    description: [''],
    priority: ['MEDIUM' as TaskPriority],
    riskLevel: ['NORMAL' as TaskRiskLevel],
  });

  ngOnInit(): void {
    if (this.projectStore.projects().length === 0) {
      void this.projectStore.load();
    }
  }

  save(): void {
    if (this.form.invalid) {
      return;
    }
    const value = this.form.getRawValue();
    const request: CreateTaskRequest = {
      projectId: value.projectId,
      title: value.title,
      priority: value.priority,
      riskLevel: value.riskLevel,
      ...(value.description ? { description: value.description } : {}),
    };
    this.dialogRef.close(request);
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
