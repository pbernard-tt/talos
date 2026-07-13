// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, OnInit, computed, inject } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { CostsStore } from './costs.store';

interface Bar {
  label: string;
  valueLabel: string;
  widthPct: number;
  isSubscription?: boolean;
}

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

function usd(value: number): string {
  return `$${value.toFixed(2)}`;
}

function percent(value: number | null): string {
  return value === null ? '—' : `${(value * 100).toFixed(0)}%`;
}

/** Talos.dc.html's "Costs & Insights" screen. All bars/stats below are derived entirely from
 * CostsStore's real per-project MonthlyCostSummary rows -- see the store for what's aggregated and
 * why avg run duration is intentionally left out. */
@Component({
  selector: 'app-costs-page',
  imports: [MatProgressSpinnerModule],
  templateUrl: './costs.page.html',
  styleUrl: './costs.page.scss',
})
export class CostsPage implements OnInit {
  protected readonly store = inject(CostsStore);
  protected readonly percent = percent;

  protected readonly totalSpendThisMonth = computed(() => {
    const month = currentMonth();
    let total = 0;
    let hasPriced = false;
    for (const row of this.store.rows()) {
      for (const summary of row.summaries) {
        if (summary.month === month && summary.totalCostUsd !== undefined) {
          total += summary.totalCostUsd;
          hasPriced = true;
        }
      }
    }
    return hasPriced ? usd(total) : '$0.00';
  });

  protected readonly avgCostPerRun = computed(() => {
    let totalCost = 0;
    let totalRuns = 0;
    for (const row of this.store.rows()) {
      for (const summary of row.summaries) {
        if (summary.totalCostUsd !== undefined) {
          totalCost += summary.totalCostUsd;
          totalRuns += summary.runCount;
        }
      }
    }
    return totalRuns > 0 ? usd(totalCost / totalRuns) : '—';
  });

  protected readonly costByProject = computed<Bar[]>(() => {
    const totals = this.store
      .rows()
      .map((row) => ({
        label: row.project.name,
        value: row.summaries.reduce((sum, s) => sum + (s.totalCostUsd ?? 0), 0),
      }))
      .filter((entry) => entry.value > 0)
      .sort((a, b) => b.value - a.value);
    const max = Math.max(...totals.map((entry) => entry.value), 1);
    return totals.map((entry) => ({
      label: entry.label,
      valueLabel: usd(entry.value),
      widthPct: Math.round((entry.value / max) * 100),
    }));
  });

  protected readonly costByProvider = computed<Bar[]>(() => {
    const byAgent = new Map<string, { value: number; hasSubscription: boolean }>();
    for (const row of this.store.rows()) {
      for (const summary of row.summaries) {
        const entry = byAgent.get(summary.agentKey) ?? { value: 0, hasSubscription: false };
        entry.value += summary.totalCostUsd ?? 0;
        entry.hasSubscription = entry.hasSubscription || summary.subscriptionRunCount > 0;
        byAgent.set(summary.agentKey, entry);
      }
    }
    const entries = [...byAgent.entries()].sort((a, b) => b[1].value - a[1].value);
    const max = Math.max(...entries.map(([, v]) => v.value), 1);
    return entries.map(([label, v]) => ({
      label,
      valueLabel: usd(v.value),
      widthPct: Math.round((v.value / max) * 100),
      isSubscription: v.hasSubscription,
    }));
  });

  ngOnInit(): void {
    void this.store.load();
  }
}
