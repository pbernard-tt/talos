// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, computed, input } from '@angular/core';

/** Talos design system AgentBadge (components/badges/AgentBadge.jsx), keyed by the registered
 * adapter keys from talos.schema.json's agentKey enum (CLAUDE.md's adapter order). */
interface AgentVisual {
  label: string;
  initial: string;
  color: string;
  border: string;
}

const AGENTS: Record<string, AgentVisual> = {
  'claude-code': { label: 'Claude Code', initial: 'C', color: '#A78BFA', border: 'rgba(139,92,246,0.3)' },
  'codex-cli': { label: 'Codex CLI', initial: 'X', color: '#38BDF8', border: 'rgba(56,189,248,0.3)' },
  opencode: { label: 'OpenCode', initial: 'O', color: '#22C55E', border: 'rgba(34,197,94,0.3)' },
  openhands: { label: 'OpenHands', initial: 'H', color: '#F59E0B', border: 'rgba(245,158,11,0.3)' },
  'custom-shell': { label: 'Custom Shell', initial: 'S', color: 'var(--text-secondary)', border: 'rgba(160,167,181,0.3)' },
};

const FALLBACK: AgentVisual = AGENTS['custom-shell'];

@Component({
  selector: 'app-agent-badge',
  template: `
    <span class="agent-badge" [style.color]="agent().color" [style.border-color]="agent().border">
      <span class="mark" [style.background]="agent().color">{{ agent().initial }}</span>
      {{ agent().label }}
    </span>
  `,
  styles: `
    .agent-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 3px 9px 3px 7px;
      border-radius: var(--r-xs);
      font: var(--weight-semibold) var(--text-xs) var(--font-ui);
      white-space: nowrap;
      background: var(--surface-elevated);
      border: 1px solid transparent;
    }

    .mark {
      width: 14px;
      height: 14px;
      border-radius: 4px;
      flex: none;
      display: flex;
      align-items: center;
      justify-content: center;
      font: var(--weight-bold) 8px var(--font-mono);
      color: var(--surface-elevated);
    }
  `,
})
export class AgentBadgeComponent {
  readonly agentKey = input.required<string>();
  protected readonly agent = computed(() => AGENTS[this.agentKey()] ?? FALLBACK);
}
