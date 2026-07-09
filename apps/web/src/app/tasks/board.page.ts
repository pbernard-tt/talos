import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { Component, OnInit, Signal, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatToolbarModule } from '@angular/material/toolbar';

import { CreateTaskRequest, Task, TaskStatus } from '../api';
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
    RouterLink,
    TaskColumnComponent,
    TaskDrawerComponent,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSidenavModule,
    MatToolbarModule,
  ],
  templateUrl: './board.page.html',
  styleUrl: './board.page.scss',
})
export class BoardPage implements OnInit {
  protected readonly store = inject(TaskStore);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

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
