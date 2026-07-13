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
  | 'chevron-down'
  | 'play'
  | 'download'
  | 'search'
  | 'check'
  | 'arrow-left'
  | 'log-out'
  | 'panel-left'
  | 'alert-triangle'
  | 'activity'
  | 'folder'
  | 'kanban'
  | 'terminal'
  | 'file-check'
  | 'rocket'
  | 'book'
  | 'bar-chart'
  | 'grid'
  | 'users'
  | 'shield'
  | 'server'
  | 'settings'
  | 'bell';

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
        @case ('chevron-down') {
          <polyline points="6 9 12 15 18 9" />
        }
        @case ('activity') {
          <polyline points="2 14 7 14 9 8 13 20 15 14 22 14" />
        }
        @case ('folder') {
          <path d="M3 7v12a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-8l-2-3H5a2 2 0 0 0-2 2v0z" />
        }
        @case ('kanban') {
          <rect x="3" y="4" width="5" height="16" rx="1" />
          <rect x="9.5" y="4" width="5" height="10" rx="1" />
          <rect x="16" y="4" width="5" height="13" rx="1" />
        }
        @case ('terminal') {
          <rect x="3" y="4" width="18" height="16" rx="2" />
          <polyline points="7 9 10 12 7 15" />
          <line x1="12" y1="15" x2="16" y2="15" />
        }
        @case ('file-check') {
          <rect x="3" y="3" width="18" height="18" rx="3" />
          <polyline points="8 12 11 15 16 9" />
        }
        @case ('rocket') {
          <path d="M12 2l3 5h5l-4 4 2 6-6-3-6 3 2-6-4-4h5z" />
        }
        @case ('book') {
          <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
          <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
        }
        @case ('bar-chart') {
          <line x1="5" y1="21" x2="5" y2="12" />
          <line x1="12" y1="21" x2="12" y2="7" />
          <line x1="19" y1="21" x2="19" y2="15" />
        }
        @case ('grid') {
          <rect x="3" y="3" width="7" height="7" rx="1.5" />
          <rect x="14" y="3" width="7" height="7" rx="1.5" />
          <rect x="3" y="14" width="7" height="7" rx="1.5" />
          <rect x="14" y="14" width="7" height="7" rx="1.5" />
        }
        @case ('users') {
          <path d="M17 20v-1a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v1" />
          <circle cx="10" cy="7" r="4" />
          <path d="M22 20v-1a4 4 0 0 0-3-3.87" />
          <path d="M17 3.13a4 4 0 0 1 0 7.75" />
        }
        @case ('shield') {
          <path d="M12 2l8 3.5v6C20 16.5 16.5 20.5 12 22 7.5 20.5 4 16.5 4 11.5v-6z" />
        }
        @case ('server') {
          <rect x="2" y="3" width="20" height="7" rx="1.5" />
          <rect x="2" y="14" width="20" height="7" rx="1.5" />
          <line x1="6" y1="6.5" x2="6" y2="6.5" />
          <line x1="6" y1="17.5" x2="6" y2="17.5" />
        }
        @case ('settings') {
          <circle cx="12" cy="12" r="3" />
          <path
            d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"
          />
        }
        @case ('bell') {
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.73 21a2 2 0 0 1-3.46 0" />
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
