// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';

import { ConfigPanelComponent } from './config-panel.component';
import { ProjectStore } from './project.store';
import { AgentBadgeComponent } from '../shared/badges/agent-badge.component';
import { StatusBadgeComponent } from '../shared/badges/status-badge.component';
import { projectStatusTone, runStatusTone } from '../shared/badges/status-tone';
import { IconComponent } from '../shared/icon/icon.component';

@Component({
  selector: 'app-project-detail-page',
  imports: [
    RouterLink,
    ConfigPanelComponent,
    MatProgressSpinnerModule,
    MatTableModule,
    AgentBadgeComponent,
    StatusBadgeComponent,
    IconComponent,
  ],
  templateUrl: './project-detail.page.html',
  styleUrl: './project-detail.page.scss',
})
export class ProjectDetailPage implements OnInit {
  protected readonly store = inject(ProjectStore);
  private readonly route = inject(ActivatedRoute);

  protected readonly runColumns = ['agentKey', 'status', 'createdAt'];
  protected readonly costColumns = ['agentKey', 'month', 'totalCostUsd', 'totalInputTokens', 'totalOutputTokens', 'runCount'];

  protected readonly projectStatusTone = projectStatusTone;
  protected readonly runStatusTone = runStatusTone;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      void this.store.loadDetail(id);
      void this.store.loadMonthlyCosts(id);
      void this.store.loadRecommendations(id);
    }
  }
}
