// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, input } from '@angular/core';

/** Talos design system MetricCard (components/data-display/MetricCard.jsx). `value` is always a
 * real computed figure -- never an estimate (CLAUDE.md "Numbers are real"). */
@Component({
  selector: 'app-metric-card',
  template: `
    <div class="metric-card">
      <div class="label">{{ label() }}</div>
      <div class="value" [style.color]="color()">{{ value() }}</div>
    </div>
  `,
  styles: `
    .metric-card {
      text-align: left;
      background: var(--surface-card);
      border: 1px solid var(--border-default);
      border-radius: var(--r-lg);
      padding: 14px;
      width: 100%;
      box-sizing: border-box;
    }

    .label {
      font: var(--weight-semibold) var(--text-xs) var(--font-ui);
      color: var(--text-muted);
      margin-bottom: 8px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .value {
      font: var(--weight-bold) var(--text-2xl) var(--font-mono);
      color: var(--text-primary);
    }
  `,
})
export class MetricCardComponent {
  readonly label = input.required<string>();
  readonly value = input.required<string | number>();
  readonly color = input<string>('var(--text-primary)');
}
