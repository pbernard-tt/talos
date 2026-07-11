import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { CreateTaskRequest, Run, RunsService, Task, TaskDetail, TaskStatus, TasksService } from '../api';

/** One signal-based store per domain (Section 6.1); components read signals and call store methods. */
@Injectable({ providedIn: 'root' })
export class TaskStore {
  private readonly tasksService = inject(TasksService);
  private readonly runsService = inject(RunsService);

  private readonly tasksSignal = signal<Task[]>([]);
  private readonly loadingSignal = signal(false);
  private readonly errorSignal = signal<string | null>(null);
  private readonly selectedTaskSignal = signal<TaskDetail | null>(null);

  readonly tasks = this.tasksSignal.asReadonly();
  readonly loading = this.loadingSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();
  readonly selectedTask = this.selectedTaskSignal.asReadonly();

  /** The board shows every task at once (Section 15 Kanban), so this pulls a single large page rather than paginating. */
  async load(): Promise<void> {
    this.loadingSignal.set(true);
    this.errorSignal.set(null);
    try {
      const page = await firstValueFrom(this.tasksService.listTasks({ size: 500 }));
      this.tasksSignal.set(page.content);
    } catch {
      this.errorSignal.set('Could not load tasks.');
    } finally {
      this.loadingSignal.set(false);
    }
  }

  async create(request: CreateTaskRequest): Promise<Task> {
    const task = await firstValueFrom(this.tasksService.createTask({ createTaskRequest: request }));
    this.tasksSignal.update((tasks) => [task, ...tasks]);
    return task;
  }

  async loadDetail(id: string): Promise<void> {
    this.loadingSignal.set(true);
    this.errorSignal.set(null);
    try {
      const detail = await firstValueFrom(this.tasksService.getTask({ id }));
      this.selectedTaskSignal.set(detail);
    } catch {
      this.errorSignal.set('Could not load the task.');
    } finally {
      this.loadingSignal.set(false);
    }
  }

  /** Section 15 "start agent run from a card": POST /tasks/{id}/start-run, then refresh the
   * board and the open drawer so the new run (and any task status change) appear immediately. */
  async startRun(taskId: string): Promise<Run> {
    const run = await firstValueFrom(this.runsService.startRun({ id: taskId }));
    void this.load();
    if (this.selectedTaskSignal()?.id === taskId) {
      void this.loadDetail(taskId);
    }
    return run;
  }

  clearSelectedTask(): void {
    this.selectedTaskSignal.set(null);
  }

  /** Optimistic move (Section 15: "Optimistic move with rollback on 422"). */
  async move(id: string, status: TaskStatus, boardPosition: number): Promise<void> {
    const previous = this.tasksSignal();
    this.tasksSignal.set(
      previous.map((task) => (task.id === id ? { ...task, status, boardPosition } : task)),
    );
    try {
      const updated = await firstValueFrom(
        this.tasksService.moveTask({ id, moveTaskRequest: { status, boardPosition } }),
      );
      this.tasksSignal.update((tasks) => tasks.map((task) => (task.id === id ? updated : task)));
    } catch (err) {
      this.tasksSignal.set(previous);
      throw err;
    }
  }
}
