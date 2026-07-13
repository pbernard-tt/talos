// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, computed, input } from '@angular/core';

/** Talos design system AuthModeBadge (components/badges/AuthModeBadge.jsx). Distinguishes the two
 * provider auth modes Section 13 prices differently -- subscription runs never get an estimated
 * cost, so this label is the operator's cue for why costUsd may be null. */
interface AuthModeVisual {
  label: string;
  color: string;
  background: string;
  border: string;
}

const MODES: Record<string, AuthModeVisual> = {
  subscription_local: {
    label: 'personal subscription',
    color: '#F59E0B',
    background: 'rgba(245,158,11,0.1)',
    border: 'rgba(245,158,11,0.28)',
  },
  api_key: {
    label: 'api key',
    color: '#38BDF8',
    background: 'rgba(56,189,248,0.1)',
    border: 'rgba(56,189,248,0.28)',
  },
};

const FALLBACK: AuthModeVisual = {
  label: 'n/a',
  color: 'var(--text-muted)',
  background: 'rgba(160,167,181,0.1)',
  border: 'rgba(160,167,181,0.2)',
};

@Component({
  selector: 'app-auth-mode-badge',
  template: `
    <span class="auth-mode-badge" [style.color]="mode().color" [style.background]="mode().background" [style.border-color]="mode().border">
      {{ mode().label }}
    </span>
  `,
  styles: `
    .auth-mode-badge {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 2px 8px;
      border-radius: var(--r-sm);
      font: var(--weight-semibold) 10.5px/1.6 var(--font-mono);
      border: 1px solid transparent;
      white-space: nowrap;
    }
  `,
})
export class AuthModeBadgeComponent {
  readonly authMode = input<string>('api_key');
  protected readonly mode = computed(() => MODES[this.authMode()] ?? FALLBACK);
}
