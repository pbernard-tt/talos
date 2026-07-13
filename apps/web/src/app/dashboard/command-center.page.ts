// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ProjectStore } from '../projects/project.store';
import { StatusBadgeComponent } from '../shared/badges/status-badge.component';
import { projectStatusTone } from '../shared/badges/status-tone';
import { ApprovalCardComponent } from './approval-card.component';
import { CommandCenterStore } from './command-center.store';
import { RunCardComponent } from './run-card.component';

/** Section 15 / plan line 961: CommandCenterPage at '/', cards for active runs, approvals waiting,
 * failed builds, recently completed tasks; the DLQ alert from Section 11. The project health
 * table reuses ProjectStore (already the source for the Projects page) rather than fetching per-
 * project cost/deploy fan-outs on every landing-page visit -- those live on the dedicated
 * Costs/Deployments screens instead. */
@Component({
  selector: 'app-command-center-page',
  imports: [RouterLink, MatProgressSpinnerModule, ApprovalCardComponent, RunCardComponent, StatusBadgeComponent],
  templateUrl: './command-center.page.html',
  styleUrl: './command-center.page.scss',
})
export class CommandCenterPage implements OnInit {
  protected readonly store = inject(CommandCenterStore);
  protected readonly projectStore = inject(ProjectStore);
  protected readonly projectStatusTone = projectStatusTone;

  ngOnInit(): void {
    void this.store.load();
    if (this.projectStore.projects().length === 0) {
      void this.projectStore.load();
    }
  }
}
