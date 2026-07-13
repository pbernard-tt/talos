// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, EventEmitter, Input, Output } from '@angular/core';

import { Task } from '../api';
import { AgentBadgeComponent } from '../shared/badges/agent-badge.component';
import { RiskBadgeComponent } from '../shared/badges/risk-badge.component';
import { priorityColorVar } from '../shared/badges/status-tone';

@Component({
  selector: 'app-task-card',
  imports: [AgentBadgeComponent, RiskBadgeComponent],
  templateUrl: './task-card.component.html',
  styleUrl: './task-card.component.scss',
})
export class TaskCardComponent {
  @Input({ required: true }) task!: Task;
  @Output() readonly cardClick = new EventEmitter<Task>();

  protected readonly priorityColorVar = priorityColorVar;

  onClick(): void {
    this.cardClick.emit(this.task);
  }
}
