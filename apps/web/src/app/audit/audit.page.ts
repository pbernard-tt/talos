// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component } from '@angular/core';

interface SecurityPanel {
  title: string;
  detail: string;
}

/** Talos.dc.html's "Audit & Security" screen. The panels below are static architectural
 * guarantees pulled directly from CLAUDE.md's hard constraints and Section 12/15 of the plan --
 * not per-instance data, so they're safe to state without an API call. The audit event table stays
 * genuinely empty: no GET /audit or equivalent endpoint exists yet (AuditService only writes rows
 * server-side, e.g. dev.talos.audit.AuditService), so listing rows here would mean fabricating
 * them. */
@Component({
  selector: 'app-audit-page',
  imports: [],
  templateUrl: './audit.page.html',
  styleUrl: './audit.page.scss',
})
export class AuditPage {
  protected readonly securityPanels: SecurityPanel[] = [
    {
      title: 'Isolated worktrees',
      detail: 'Every run executes on its own agent/task-<id>-<slug> branch in an isolated git worktree. Nothing is pushed, PR\'d, or deployed without an APPROVED approval row.',
    },
    {
      title: 'Secrets never reach workers',
      detail: 'Runners receive only enumerated injected env vars; provider credentials live in isolated provider homes outside every workspace. All injected values are masked in every log path.',
    },
    {
      title: 'Approval gate, server-enforced',
      detail: 'Push/PR/deploy require an APPROVED approval row, enforced server-side and covered by tests -- not a client-side check.',
    },
    {
      title: 'Self-approval blocked',
      detail: 'The user who requested a run or deploy cannot approve it themselves (Section 16 Phase 15) -- REVIEWER+ only, and every decision is audited.',
    },
    {
      title: 'Strict role hierarchy',
      detail: 'OWNER implies MAINTAINER implies REVIEWER implies VIEWER. Real RBAC is enforced server-side on every controller method; the UI only hides actions a role can\'t take.',
    },
    {
      title: 'Memory is project-isolated',
      detail: 'Retrieval never crosses project boundaries, even when two projects share similar code.',
    },
  ];
}
