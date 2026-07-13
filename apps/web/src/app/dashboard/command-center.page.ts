// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, OnInit, inject } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ApprovalCardComponent } from './approval-card.component';
import { CommandCenterStore } from './command-center.store';
import { RunCardComponent } from './run-card.component';

/** Section 15 / plan line 961: CommandCenterPage at '/', cards for active runs, approvals waiting,
 * failed builds, recently completed tasks; the DLQ alert from Section 11. */
@Component({
  selector: 'app-command-center-page',
  imports: [MatProgressSpinnerModule, ApprovalCardComponent, RunCardComponent],
  templateUrl: './command-center.page.html',
  styleUrl: './command-center.page.scss',
})
export class CommandCenterPage implements OnInit {
  protected readonly store = inject(CommandCenterStore);

  ngOnInit(): void {
    void this.store.load();
  }
}
