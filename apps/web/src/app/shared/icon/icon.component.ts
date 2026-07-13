// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, input } from '@angular/core';

/** Lucide-compatible icon name (24x24 viewBox, 2px stroke, round caps) -- see talos-design skill
 * README "Iconography": hand-drawn inline SVGs matching Lucide's style, the flagged substitution
 * for the unsupplied first-party icon set. Extend this union as new icons are needed. */
export type IconName =
  | 'plus'
  | 'x'
  | 'chevron-left'
  | 'chevron-right'
  | 'play'
  | 'download'
  | 'search'
  | 'check'
  | 'arrow-left'
  | 'log-out'
  | 'panel-left'
  | 'alert-triangle';

@Component({
  selector: 'app-icon',
  template: `
    <svg
      [attr.width]="size()"
      [attr.height]="size()"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      @switch (name()) {
        @case ('plus') {
          <path d="M5 12h14" />
          <path d="M12 5v14" />
        }
        @case ('x') {
          <path d="M18 6 6 18" />
          <path d="m6 6 12 12" />
        }
        @case ('chevron-left') {
          <path d="m15 18-6-6 6-6" />
        }
        @case ('chevron-right') {
          <path d="m9 18 6-6-6-6" />
        }
        @case ('play') {
          <polygon points="6 3 20 12 6 21 6 3" />
        }
        @case ('download') {
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
          <polyline points="7 10 12 15 17 10" />
          <line x1="12" x2="12" y1="15" y2="3" />
        }
        @case ('search') {
          <circle cx="11" cy="11" r="8" />
          <path d="m21 21-4.3-4.3" />
        }
        @case ('check') {
          <path d="M20 6 9 17l-5-5" />
        }
        @case ('arrow-left') {
          <path d="m12 19-7-7 7-7" />
          <path d="M19 12H5" />
        }
        @case ('log-out') {
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
          <polyline points="16 17 21 12 16 7" />
          <line x1="21" x2="9" y1="12" y2="12" />
        }
        @case ('panel-left') {
          <rect width="18" height="18" x="3" y="3" rx="2" />
          <path d="M9 3v18" />
        }
        @case ('alert-triangle') {
          <path
            d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"
          />
          <line x1="12" y1="9" x2="12" y2="13" />
          <line x1="12" y1="17" x2="12.01" y2="17" />
        }
      }
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      line-height: 0;
    }
  `,
})
export class IconComponent {
  readonly name = input.required<IconName>();
  readonly size = input(20);
}
