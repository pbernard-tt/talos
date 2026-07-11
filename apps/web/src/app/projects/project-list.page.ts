import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatToolbarModule } from '@angular/material/toolbar';

import { CreateProjectRequest } from '../api';
import { AuthStore } from '../core/auth/auth.store';
import { ProjectFormDialogComponent } from './project-form-dialog.component';
import { ProjectStore } from './project.store';

@Component({
  selector: 'app-project-list-page',
  imports: [
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatToolbarModule,
  ],
  templateUrl: './project-list.page.html',
  styleUrl: './project-list.page.scss',
})
export class ProjectListPage implements OnInit {
  protected readonly store = inject(ProjectStore);
  protected readonly authStore = inject(AuthStore);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly displayedColumns = ['name', 'stackType', 'defaultBranch', 'status'];

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
