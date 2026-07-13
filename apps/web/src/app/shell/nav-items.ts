// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { IconName } from '../shared/icon/icon.component';

export interface NavItem {
  path: string;
  label: string;
  exact: boolean;
  icon: IconName;
  minRole?: 'OWNER';
}

/** Talos.dc.html's Shell nav -- all 13 sections from the full product plan, each wired to a real
 * route. Team and Integrations are OWNER-gated to match their API endpoints (listUsers/createUser/
 * updateUser and integration management are all "OWNER only", Section 16 Phase 15). */
export const NAV_ITEMS: NavItem[] = [
  { path: '/', label: 'Command Center', exact: true, icon: 'activity' },
  { path: '/projects', label: 'Projects', exact: false, icon: 'folder' },
  { path: '/board', label: 'Task Board', exact: false, icon: 'kanban' },
  { path: '/runs', label: 'Agent Runs', exact: false, icon: 'terminal' },
  { path: '/approvals', label: 'Review Center', exact: false, icon: 'file-check' },
  { path: '/deployments', label: 'Deployments', exact: false, icon: 'rocket' },
  { path: '/memory', label: 'Memory & Docs', exact: false, icon: 'book' },
  { path: '/costs', label: 'Costs & Insights', exact: false, icon: 'bar-chart' },
  { path: '/integrations', label: 'Integrations', exact: false, icon: 'grid', minRole: 'OWNER' },
  { path: '/team', label: 'Team', exact: false, icon: 'users', minRole: 'OWNER' },
  { path: '/audit', label: 'Audit & Security', exact: false, icon: 'shield' },
  { path: '/system', label: 'System Health', exact: false, icon: 'server' },
  { path: '/settings', label: 'Settings', exact: false, icon: 'settings' },
];
