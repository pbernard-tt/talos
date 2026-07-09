import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { signal } from '@angular/core';
import { describe, expect, it, vi } from 'vitest';

import { Task } from '../api';
import { BoardPage } from './board.page';
import { TaskStore } from './task.store';

function makeTask(overrides: Partial<Task> = {}): Task {
  return {
    id: 'task-1',
    projectId: 'project-1',
    title: 'Add /hello endpoint',
    source: 'DASHBOARD',
    status: 'BACKLOG',
    priority: 'MEDIUM',
    riskLevel: 'NORMAL',
    boardPosition: 0,
    createdAt: '2026-07-09T00:00:00Z',
    updatedAt: '2026-07-09T00:00:00Z',
    ...overrides,
  };
}

function makeDropEvent(task: Task, sameContainer: boolean, currentIndex: number): CdkDragDrop<Task[]> {
  const container = { id: 'target' } as CdkDragDrop<Task[]>['container'];
  const previousContainer = sameContainer ? container : ({ id: 'source' } as CdkDragDrop<Task[]>['container']);
  return {
    previousContainer,
    container,
    previousIndex: 0,
    currentIndex,
    item: { data: task },
  } as unknown as CdkDragDrop<Task[]>;
}

describe('BoardPage', () => {
  function setup() {
    const move = vi.fn().mockResolvedValue(undefined);
    const taskStoreStub = {
      tasks: signal<Task[]>([]),
      loading: signal(false),
      error: signal<string | null>(null),
      selectedTask: signal(null),
      load: vi.fn().mockResolvedValue(undefined),
      loadDetail: vi.fn().mockResolvedValue(undefined),
      clearSelectedTask: vi.fn(),
      move,
    };

    TestBed.configureTestingModule({
      imports: [BoardPage],
      providers: [provideRouter([]), { provide: TaskStore, useValue: taskStoreStub }],
    });
    const fixture = TestBed.createComponent(BoardPage);
    const snackBar = TestBed.inject(MatSnackBar);
    const snackBarSpy = vi.spyOn(snackBar, 'open');
    return { fixture, component: fixture.componentInstance, move, snackBarSpy };
  }

  it('moving a card to a new column calls store.move with the target status and drop index', () => {
    const { component, move } = setup();
    const task = makeTask();

    component.onTaskDropped(makeDropEvent(task, false, 2), 'READY');

    expect(move).toHaveBeenCalledWith('task-1', 'READY', 2);
  });

  it('dropping in the same position within the same column does nothing', () => {
    const { component, move } = setup();
    const task = makeTask();

    component.onTaskDropped(makeDropEvent(task, true, 0), 'BACKLOG');

    expect(move).not.toHaveBeenCalled();
  });

  it('an illegal move (rejected by the API) shows an error snackbar instead of throwing', async () => {
    const { component, move, snackBarSpy } = setup();
    move.mockRejectedValueOnce(new Error('422'));
    const task = makeTask();

    component.onTaskDropped(makeDropEvent(task, false, 0), 'DONE');
    await Promise.resolve();
    await Promise.resolve();

    expect(snackBarSpy).toHaveBeenCalledWith(
      expect.stringContaining('Cannot move'),
      'Dismiss',
      expect.anything(),
    );
  });

  it('tasksFor() filters and sorts tasks by boardPosition within a column', () => {
    const { component, fixture } = setup();
    const store = TestBed.inject(TaskStore) as unknown as { tasks: ReturnType<typeof signal<Task[]>> };
    store.tasks.set([
      makeTask({ id: 'a', status: 'BACKLOG', boardPosition: 2 }),
      makeTask({ id: 'b', status: 'READY', boardPosition: 0 }),
      makeTask({ id: 'c', status: 'BACKLOG', boardPosition: 1 }),
    ]);
    fixture.detectChanges();

    expect(component.tasksFor('BACKLOG').map((t) => t.id)).toEqual(['c', 'a']);
    expect(component.tasksFor('READY').map((t) => t.id)).toEqual(['b']);
  });
});
