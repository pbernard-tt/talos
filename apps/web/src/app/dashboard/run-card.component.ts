// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { RunSummary } from '../api';
import { AgentBadgeComponent } from '../shared/badges/agent-badge.component';
import { StatusBadgeComponent } from '../shared/badges/status-badge.component';
import { runStatusTone } from '../shared/badges/status-tone';

@Component({
  selector: 'app-run-card',
  imports: [RouterLink, AgentBadgeComponent, StatusBadgeComponent],
  templateUrl: './run-card.component.html',
  styleUrl: './run-card.component.scss',
})
export class RunCardComponent {
  @Input({ required: true }) run!: RunSummary;

  protected readonly runStatusTone = runStatusTone;
}
