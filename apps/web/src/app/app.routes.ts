import { Routes } from '@angular/router';

import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: '',
    pathMatch: 'full',
    canActivate: [authGuard],
    loadComponent: () => import('./dashboard/command-center.page').then((m) => m.CommandCenterPage),
  },
  {
    path: 'projects',
    canActivate: [authGuard],
    loadComponent: () => import('./projects/project-list.page').then((m) => m.ProjectListPage),
  },
  {
    path: 'projects/:id',
    canActivate: [authGuard],
    loadComponent: () => import('./projects/project-detail.page').then((m) => m.ProjectDetailPage),
  },
  {
    path: 'board',
    canActivate: [authGuard],
    loadComponent: () => import('./tasks/board.page').then((m) => m.BoardPage),
  },
  {
    path: 'runs/:id',
    canActivate: [authGuard],
    loadComponent: () => import('./runs/run-detail.page').then((m) => m.RunDetailPage),
  },
  {
    path: 'approvals',
    canActivate: [authGuard],
    loadComponent: () => import('./approvals/approval-inbox.page').then((m) => m.ApprovalInboxPage),
  },
  {
    path: 'integrations',
    canActivate: [authGuard],
    loadComponent: () => import('./integrations/integrations.page').then((m) => m.IntegrationsPage),
  },
  {
    path: 'review/:runId',
    canActivate: [authGuard],
    loadComponent: () => import('./approvals/review.page').then((m) => m.ReviewPage),
  },
  { path: '**', redirectTo: '' },
];
