// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, EventEmitter, Input, Output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';

import { TaskDetail } from '../api';
import { AgentBadgeComponent } from '../shared/badges/agent-badge.component';
import { RiskBadgeComponent } from '../shared/badges/risk-badge.component';
import { StatusBadgeComponent } from '../shared/badges/status-badge.component';
import { runStatusTone, taskStatusTone } from '../shared/badges/status-tone';
import { IconComponent } from '../shared/icon/icon.component';

/** Run states in which POST /tasks/{id}/start-run would 409 (mirrors RunTransitionValidator's
 * terminal set: everything else is active). Used only to disable the button up front -- the API
 * enforces the rule regardless. */
const TERMINAL_RUN_STATUSES = new Set(['COMPLETED', 'FAILED', 'CANCELLED', 'REJECTED']);

@Component({
  selector: 'app-task-drawer',
  imports: [RouterLink, MatButtonModule, IconComponent, StatusBadgeComponent, AgentBadgeComponent, RiskBadgeComponent],
  templateUrl: './task-drawer.component.html',
  styleUrl: './task-drawer.component.scss',
})
export class TaskDrawerComponent {
  @Input() task: TaskDetail | null = null;
  /** UI-hiding hint only (Section 16 Phase 15) -- the endpoint requires MAINTAINER server-side. */
  @Input() canStartRun = false;
  @Output() readonly close = new EventEmitter<void>();
  @Output() readonly startRun = new EventEmitter<TaskDetail>();

  protected readonly taskStatusTone = taskStatusTone;
  protected readonly runStatusTone = runStatusTone;

  hasActiveRun(): boolean {
    return (this.task?.runs ?? []).some((run) => !TERMINAL_RUN_STATUSES.has(run.status));
  }
}
