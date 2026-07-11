import { Component, EventEmitter, Input, Output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';

import { TaskDetail } from '../api';

/** Run states in which POST /tasks/{id}/start-run would 409 (mirrors RunTransitionValidator's
 * terminal set: everything else is active). Used only to disable the button up front -- the API
 * enforces the rule regardless. */
const TERMINAL_RUN_STATUSES = new Set(['COMPLETED', 'FAILED', 'CANCELLED', 'REJECTED']);

@Component({
  selector: 'app-task-drawer',
  imports: [RouterLink, MatButtonModule, MatIconModule, MatListModule],
  templateUrl: './task-drawer.component.html',
  styleUrl: './task-drawer.component.scss',
})
export class TaskDrawerComponent {
  @Input() task: TaskDetail | null = null;
  /** UI-hiding hint only (Section 16 Phase 15) -- the endpoint requires MAINTAINER server-side. */
  @Input() canStartRun = false;
  @Output() readonly close = new EventEmitter<void>();
  @Output() readonly startRun = new EventEmitter<TaskDetail>();

  hasActiveRun(): boolean {
    return (this.task?.runs ?? []).some((run) => !TERMINAL_RUN_STATUSES.has(run.status));
  }
}
