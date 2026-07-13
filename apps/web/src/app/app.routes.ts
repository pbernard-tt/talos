// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Routes } from '@angular/router';

import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./shell/shell.component').then((m) => m.ShellComponent),
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('./dashboard/command-center.page').then((m) => m.CommandCenterPage),
      },
      {
        path: 'projects',
        loadComponent: () => import('./projects/project-list.page').then((m) => m.ProjectListPage),
      },
      {
        path: 'projects/:id',
        loadComponent: () => import('./projects/project-detail.page').then((m) => m.ProjectDetailPage),
      },
      {
        path: 'board',
        loadComponent: () => import('./tasks/board.page').then((m) => m.BoardPage),
      },
      {
        path: 'runs',
        loadComponent: () => import('./runs/runs-list.page').then((m) => m.RunsListPage),
      },
      {
        path: 'runs/:id',
        loadComponent: () => import('./runs/run-detail.page').then((m) => m.RunDetailPage),
      },
      {
        path: 'approvals',
        loadComponent: () => import('./approvals/approval-inbox.page').then((m) => m.ApprovalInboxPage),
      },
      {
        path: 'deployments',
        loadComponent: () => import('./deployments/deployments.page').then((m) => m.DeploymentsPage),
      },
      {
        path: 'memory',
        loadComponent: () => import('./memory/memory.page').then((m) => m.MemoryPage),
      },
      {
        path: 'costs',
        loadComponent: () => import('./costs/costs.page').then((m) => m.CostsPage),
      },
      {
        path: 'integrations',
        loadComponent: () => import('./integrations/integrations.page').then((m) => m.IntegrationsPage),
      },
      {
        path: 'team',
        loadComponent: () => import('./team/team.page').then((m) => m.TeamPage),
      },
      {
        path: 'audit',
        loadComponent: () => import('./audit/audit.page').then((m) => m.AuditPage),
      },
      {
        path: 'system',
        loadComponent: () => import('./system/system-health.page').then((m) => m.SystemHealthPage),
      },
      {
        path: 'settings',
        loadComponent: () => import('./settings/settings.page').then((m) => m.SettingsPage),
      },
      {
        path: 'review/:runId',
        loadComponent: () => import('./approvals/review.page').then((m) => m.ReviewPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
