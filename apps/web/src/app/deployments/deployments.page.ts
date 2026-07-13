// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { DatePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { StatusBadgeComponent } from '../shared/badges/status-badge.component';
import { deployStatusTone } from '../shared/badges/status-tone';
import { DeploymentsStore } from './deployments.store';

@Component({
  selector: 'app-deployments-page',
  imports: [DatePipe, MatProgressSpinnerModule, StatusBadgeComponent],
  templateUrl: './deployments.page.html',
  styleUrl: './deployments.page.scss',
})
export class DeploymentsPage implements OnInit {
  protected readonly store = inject(DeploymentsStore);
  private readonly router = inject(Router);

  protected readonly deployStatusTone = deployStatusTone;

  ngOnInit(): void {
    void this.store.load();
  }

  openRun(runId: string | undefined): void {
    if (!runId) {
      return;
    }
    void this.router.navigate(['/runs', runId]);
  }
}
