// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { Component, OnInit, Signal, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatSnackBar } from '@angular/material/snack-bar';

import { CreateTaskRequest, Task, TaskDetail, TaskStatus } from '../api';
import { AuthStore } from '../core/auth/auth.store';
import { IconComponent } from '../shared/icon/icon.component';
import { TaskColumnComponent } from './task-column.component';
import { TaskDrawerComponent } from './task-drawer.component';
import { TaskFormDialogComponent } from './task-form-dialog.component';
import { TaskStore } from './task.store';

interface Column {
  status: TaskStatus;
  title: string;
  listId: string;
}

const COLUMNS: Column[] = [
  { status: 'BACKLOG', title: 'Backlog', listId: 'board-list-backlog' },
  { status: 'READY', title: 'Ready', listId: 'board-list-ready' },
  { status: 'RUNNING', title: 'Running', listId: 'board-list-running' },
  { status: 'REVIEW', title: 'Review', listId: 'board-list-review' },
  { status: 'BLOCKED', title: 'Blocked', listId: 'board-list-blocked' },
  { status: 'DONE', title: 'Done', listId: 'board-list-done' },
];

@Component({
  selector: 'app-board-page',
  imports: [
    TaskColumnComponent,
    TaskDrawerComponent,
    IconComponent,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatSidenavModule,
  ],
  templateUrl: './board.page.html',
  styleUrl: './board.page.scss',
})
export class BoardPage implements OnInit {
  protected readonly store = inject(TaskStore);
  protected readonly authStore = inject(AuthStore);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);

  protected readonly columns = COLUMNS;
  protected readonly listIds = COLUMNS.map((c) => c.listId);
  protected drawerOpen = false;

  private readonly tasksByStatus: Record<TaskStatus, Signal<Task[]>> = Object.fromEntries(
    COLUMNS.map((column) => [
      column.status,
      computed(() =>
        this.store
          .tasks()
          .filter((task) => task.status === column.status)
          .sort((a, b) => a.boardPosition - b.boardPosition),
      ),
    ]),
  ) as Record<TaskStatus, Signal<Task[]>>;

  ngOnInit(): void {
    void this.store.load();
  }

  tasksFor(status: TaskStatus): Task[] {
    return this.tasksByStatus[status]();
  }

  openCreateDialog(): void {
    const dialogRef = this.dialog.open<TaskFormDialogComponent, unknown, CreateTaskRequest>(
      TaskFormDialogComponent,
    );
    dialogRef.afterClosed().subscribe((request) => {
      if (!request) {
        return;
      }
      this.store
        .create(request)
        .then(() => this.snackBar.open(`Task "${request.title}" created.`, 'Dismiss', { duration: 4000 }))
        .catch(() => this.snackBar.open('Could not create task.', 'Dismiss', { duration: 4000 }));
    });
  }

  onTaskClick(task: Task): void {
    this.drawerOpen = true;
    void this.store.loadDetail(task.id);
  }

  closeDrawer(): void {
    this.drawerOpen = false;
    this.store.clearSelectedTask();
  }

  onStartRun(task: TaskDetail): void {
    this.store
      .startRun(task.id)
      .then((run) => {
        this.snackBar.open(`Run started for "${task.title}".`, 'Dismiss', { duration: 4000 });
        void this.router.navigate(['/runs', run.id]);
      })
      .catch(() =>
        this.snackBar.open(`Could not start a run for "${task.title}".`, 'Dismiss', { duration: 4000 }),
      );
  }

  onTaskDropped(event: CdkDragDrop<Task[]>, targetStatus: TaskStatus): void {
    const task: Task = event.item.data;
    if (event.previousContainer === event.container && event.previousIndex === event.currentIndex) {
      return;
    }
    this.store.move(task.id, targetStatus, event.currentIndex).catch(() => {
      this.snackBar.open(`Cannot move "${task.title}" to ${targetStatus}.`, 'Dismiss', { duration: 4000 });
    });
  }
}
