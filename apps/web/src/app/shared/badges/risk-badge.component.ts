// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, input } from '@angular/core';

import { IconComponent } from '../icon/icon.component';

/** Talos design system RiskBadge (components/badges/RiskBadge.jsx) -- renders nothing for a
 * non-HIGH level, since it exists to call out the one risk level operators must act on. */
@Component({
  selector: 'app-risk-badge',
  imports: [IconComponent],
  template: `
    @if (level() === 'HIGH') {
      <span class="risk-badge">
        <app-icon name="alert-triangle" [size]="10" />
        HIGH RISK
      </span>
    }
  `,
  styles: `
    .risk-badge {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 3px 9px;
      border-radius: var(--r-pill);
      font: var(--weight-bold) 10.5px/1.5 var(--font-ui);
      letter-spacing: 0.03em;
      background: var(--status-error-tint);
      color: var(--status-error);
      border: 1px solid rgba(239, 68, 68, 0.3);
    }
  `,
})
export class RiskBadgeComponent {
  readonly level = input<string>('NORMAL');
}
