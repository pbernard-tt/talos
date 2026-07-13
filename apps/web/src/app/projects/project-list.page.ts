// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';

import { CreateProjectRequest } from '../api';
import { AuthStore } from '../core/auth/auth.store';
import { StatusBadgeComponent } from '../shared/badges/status-badge.component';
import { projectStatusTone } from '../shared/badges/status-tone';
import { IconComponent } from '../shared/icon/icon.component';
import { ProjectFormDialogComponent } from './project-form-dialog.component';
import { ProjectStore } from './project.store';

@Component({
  selector: 'app-project-list-page',
  imports: [RouterLink, MatButtonModule, MatProgressSpinnerModule, MatTableModule, IconComponent, StatusBadgeComponent],
  templateUrl: './project-list.page.html',
  styleUrl: './project-list.page.scss',
})
export class ProjectListPage implements OnInit {
  protected readonly store = inject(ProjectStore);
  protected readonly authStore = inject(AuthStore);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly displayedColumns = ['name', 'stackType', 'defaultBranch', 'status'];
  protected readonly projectStatusTone = projectStatusTone;

  ngOnInit(): void {
    void this.store.load();
  }

  openCreateDialog(): void {
    const dialogRef = this.dialog.open<ProjectFormDialogComponent, unknown, CreateProjectRequest>(
      ProjectFormDialogComponent,
    );
    dialogRef.afterClosed().subscribe((request) => {
      if (!request) {
        return;
      }
      this.store
        .create(request)
        .then(() => this.snackBar.open(`Project "${request.name}" created.`, 'Dismiss', { duration: 4000 }))
        .catch(() => this.snackBar.open('Could not create project.', 'Dismiss', { duration: 4000 }));
    });
  }
}
