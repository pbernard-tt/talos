// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, input } from '@angular/core';

export interface TimelineStep {
  key: string;
  label: string;
  state: 'COMPLETED' | 'RUNNING' | 'FAILED' | 'SKIPPED';
}

interface StepVisual {
  background: string;
  color: string;
  border: string;
  char: string;
  line: string;
}

/** Talos design system StatusTimeline (components/data-display/StatusTimeline.jsx). Renders only
 * the run's actual steps (Step[] from RunDetail) -- no placeholder "not started yet" entries are
 * synthesized, since the API doesn't create a Step row until that stage begins. */
const STEP_VISUAL: Record<TimelineStep['state'], StepVisual> = {
  COMPLETED: { background: 'rgba(34,197,94,0.16)', color: '#22C55E', border: '#22C55E', char: '✓', line: 'rgba(34,197,94,0.35)' },
  RUNNING: { background: 'rgba(139,92,246,0.16)', color: '#A78BFA', border: '#8B5CF6', char: '●', line: 'rgba(255,255,255,0.08)' },
  FAILED: { background: 'rgba(239,68,68,0.16)', color: '#EF4444', border: '#EF4444', char: '✕', line: 'rgba(239,68,68,0.3)' },
  SKIPPED: { background: 'rgba(160,167,181,0.08)', color: '#4a5163', border: 'rgba(160,167,181,0.15)', char: '–', line: 'rgba(255,255,255,0.05)' },
};

@Component({
  selector: 'app-status-timeline',
  template: `
    <div class="timeline">
      @for (step of steps(); track step.key; let last = $last) {
        <div class="segment">
          <div class="node">
            <span
              class="circle"
              [style.background]="visual(step.state).background"
              [style.color]="visual(step.state).color"
              [style.border-color]="visual(step.state).border"
            >
              {{ visual(step.state).char }}
            </span>
            <span class="label" [style.color]="visual(step.state).color">{{ step.label }}</span>
          </div>
          @if (!last) {
            <div class="connector" [style.background]="visual(step.state).line"></div>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .timeline {
      display: flex;
      align-items: center;
      background: var(--surface-card);
      border: 1px solid var(--border-default);
      border-radius: var(--r-lg);
      padding: 16px 18px;
    }

    .segment {
      display: flex;
      align-items: center;
      flex: 1;
    }

    .node {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 6px;
      min-width: 70px;
    }

    .circle {
      width: 22px;
      height: 22px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font: var(--weight-bold) 10px var(--font-ui);
      border: 2px solid transparent;
    }

    .label {
      font: var(--weight-semibold) 9.5px var(--font-ui);
      text-align: center;
    }

    .connector {
      flex: 1;
      height: 2px;
      margin: 0 2px 18px;
    }
  `,
})
export class StatusTimelineComponent {
  readonly steps = input.required<TimelineStep[]>();

  protected visual(state: TimelineStep['state']): StepVisual {
    return STEP_VISUAL[state];
  }
}
