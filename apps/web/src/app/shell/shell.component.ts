// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map, startWith } from 'rxjs';

import { AuthStore } from '../core/auth/auth.store';
import { IconComponent } from '../shared/icon/icon.component';

interface NavItem {
  path: string;
  label: string;
  exact: boolean;
  minRole?: 'OWNER';
}

/** Nav items scoped to routes that actually exist in app.routes.ts -- the talos-design skill's
 * Shell.jsx lists 13 sections from the full product plan, most still unbuilt (Deployments,
 * Memory & Docs, Costs & Insights, Team, Audit & Security, System Health, Settings); listing them
 * here would be dead links. */
const NAV_ITEMS: NavItem[] = [
  { path: '/', label: 'Command Center', exact: true },
  { path: '/projects', label: 'Projects', exact: false },
  { path: '/board', label: 'Task Board', exact: false },
  { path: '/approvals', label: 'Approvals', exact: false },
  { path: '/integrations', label: 'Integrations', exact: false, minRole: 'OWNER' },
];

function breadcrumbFor(url: string): string {
  if (url.startsWith('/runs/')) {
    return 'Agent Runs';
  }
  if (url.startsWith('/review/')) {
    return 'Review';
  }
  const match = NAV_ITEMS.find((item) => (item.exact ? url === item.path : url.startsWith(item.path)));
  return match?.label ?? 'Command Center';
}

@Component({
  selector: 'app-shell',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, IconComponent],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  protected readonly authStore = inject(AuthStore);
  private readonly router = inject(Router);

  protected readonly collapsed = signal(false);

  protected readonly navItems = computed(() =>
    NAV_ITEMS.filter((item) => !item.minRole || this.authStore.hasRole(item.minRole)),
  );

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
      startWith(this.router.url),
    ),
    { initialValue: this.router.url },
  );

  protected readonly breadcrumb = computed(() => breadcrumbFor(this.url()));

  protected readonly initials = computed(() => {
    const email = this.authStore.email();
    if (!email) {
      return '--';
    }
    const name = email.split('@')[0];
    return name.slice(0, 2).toUpperCase();
  });

  toggleCollapsed(): void {
    this.collapsed.update((value) => !value);
  }

  logout(): void {
    this.authStore.logout();
    void this.router.navigateByUrl('/login');
  }
}
