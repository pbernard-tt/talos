// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, input } from '@angular/core';

import { StatusTone } from './status-tone';

/** Talos design system StatusBadge (components/badges/StatusBadge.jsx). Renders the exact
 * state-machine value verbatim (e.g. "WAITING_APPROVAL", never softened) with a tone dot. */
@Component({
  selector: 'app-status-badge',
  template: `
    <span class="status-badge" [class]="'tone-' + tone()">
      <span class="dot"></span>
      {{ label() }}
    </span>
  `,
  styles: `
    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 3px 10px;
      border-radius: var(--r-pill);
      font: var(--weight-semibold) var(--text-xs) var(--font-ui);
      letter-spacing: 0.02em;
      white-space: nowrap;
      border: 1px solid transparent;
    }

    .dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: currentColor;
      flex: none;
    }

    .tone-neutral {
      color: var(--text-secondary);
      background: rgba(160, 167, 181, 0.12);
      border-color: rgba(160, 167, 181, 0.25);
    }

    .tone-purple {
      color: var(--accent-soft);
      background: var(--accent-tint-strong);
      border-color: var(--accent-border);
    }

    .tone-success {
      color: var(--status-success);
      background: var(--status-success-tint);
      border-color: rgba(34, 197, 94, 0.3);
    }

    .tone-warning {
      color: var(--status-warning);
      background: var(--status-warning-tint);
      border-color: rgba(245, 158, 11, 0.3);
    }

    .tone-error {
      color: var(--status-error);
      background: var(--status-error-tint);
      border-color: rgba(239, 68, 68, 0.32);
    }

    .tone-info {
      color: var(--status-info);
      background: var(--status-info-tint);
      border-color: rgba(56, 189, 248, 0.3);
    }
  `,
})
export class StatusBadgeComponent {
  readonly label = input.required<string>();
  readonly tone = input<StatusTone>('neutral');
}
