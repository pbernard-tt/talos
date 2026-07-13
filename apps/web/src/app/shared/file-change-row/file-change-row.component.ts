// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, input } from '@angular/core';

import { GitChangeSummary } from '../../api';
import { RiskBadgeComponent } from '../badges/risk-badge.component';

/** Adapted from the talos-design skill's DiffBlock (components/runs/DiffBlock.jsx) header bar --
 * GitChangeSummary carries per-file additions/deletions/risk metadata but no hunk text (the
 * unified diff is only available as one blob via Diff.diff), so this renders the file header row
 * without DiffBlock's <pre> hunk body. */
@Component({
  selector: 'app-file-change-row',
  imports: [RiskBadgeComponent],
  template: `
    <div class="row">
      <span class="path">{{ file().filePath }}</span>
      <span class="change-type">{{ file().changeType }}</span>
      <span class="additions">+{{ file().additions }}</span>
      <span class="deletions">-{{ file().deletions }}</span>
      <app-risk-badge [level]="file().riskFlagged ? 'HIGH' : 'NORMAL'" />
    </div>
    @if (file().riskFlagged && file().matchedPattern) {
      <div class="risk-caption">flagged: {{ file().matchedPattern }}</div>
    }
  `,
  styles: `
    :host {
      display: block;
      background: var(--surface-sunken);
      border: 1px solid var(--border-default);
      border-radius: var(--r-md);
      margin-bottom: 8px;
      overflow: hidden;
    }

    .row {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 9px 13px;
    }

    .path {
      font: var(--weight-semibold) var(--text-sm) var(--font-mono);
      color: var(--text-primary);
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .change-type {
      font: var(--weight-medium) var(--text-2xs) var(--font-ui);
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: var(--tracking-wide);
    }

    .additions {
      font: var(--weight-semibold) var(--text-xs) var(--font-ui);
      color: var(--status-success);
    }

    .deletions {
      font: var(--weight-semibold) var(--text-xs) var(--font-ui);
      color: var(--status-error);
    }

    .risk-caption {
      padding: 8px 14px;
      background: rgba(239, 68, 68, 0.08);
      border-top: 1px solid rgba(239, 68, 68, 0.2);
      font: var(--weight-medium) var(--text-xs) var(--font-ui);
      color: #f5a3a3;
    }
  `,
})
export class FileChangeRowComponent {
  readonly file = input.required<GitChangeSummary>();
}
