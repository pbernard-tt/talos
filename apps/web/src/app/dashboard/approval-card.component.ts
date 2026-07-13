// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Approval } from '../api';
import { StatusBadgeComponent } from '../shared/badges/status-badge.component';
import { approvalStatusTone } from '../shared/badges/status-tone';

@Component({
  selector: 'app-approval-card',
  imports: [RouterLink, StatusBadgeComponent],
  templateUrl: './approval-card.component.html',
  styleUrl: './approval-card.component.scss',
})
export class ApprovalCardComponent {
  @Input({ required: true }) approval!: Approval;

  protected readonly approvalStatusTone = approvalStatusTone;
}
