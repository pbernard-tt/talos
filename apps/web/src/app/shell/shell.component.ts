// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { filter, map, startWith } from 'rxjs';

import { CreateTaskRequest } from '../api';
import { ApprovalStore } from '../approvals/approval.store';
import { AuthStore } from '../core/auth/auth.store';
import { HealthService } from '../shared/health.service';
import { IconComponent } from '../shared/icon/icon.component';
import { TaskFormDialogComponent } from '../tasks/task-form-dialog.component';
import { TaskStore } from '../tasks/task.store';
import { CommandPaletteComponent } from './command-palette.component';
import { NAV_ITEMS } from './nav-items';

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
  imports: [RouterLink, RouterLinkActive, RouterOutlet, IconComponent, CommandPaletteComponent],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent implements OnInit {
  protected readonly authStore = inject(AuthStore);
  protected readonly approvalStore = inject(ApprovalStore);
  protected readonly healthService = inject(HealthService);
  private readonly taskStore = inject(TaskStore);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly collapsed = signal(false);
  protected readonly paletteOpen = signal(false);
  protected readonly notifOpen = signal(false);
  protected readonly userMenuOpen = signal(false);

  protected readonly visibleNavItems = computed(() =>
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

  ngOnInit(): void {
    void this.approvalStore.list('PENDING');
    void this.healthService.check();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (!target.closest('.dropdown-anchor')) {
      this.notifOpen.set(false);
      this.userMenuOpen.set(false);
    }
  }

  @HostListener('document:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.paletteOpen.set(true);
      this.notifOpen.set(false);
      this.userMenuOpen.set(false);
    } else if (event.key === 'Escape') {
      this.paletteOpen.set(false);
    }
  }

  toggleCollapsed(): void {
    this.collapsed.update((value) => !value);
  }

  openPalette(): void {
    this.paletteOpen.set(true);
  }

  closePalette(): void {
    this.paletteOpen.set(false);
  }

  toggleNotifications(): void {
    this.notifOpen.update((value) => !value);
    this.userMenuOpen.set(false);
  }

  closeNotifications(): void {
    this.notifOpen.set(false);
  }

  toggleUserMenu(): void {
    this.userMenuOpen.update((value) => !value);
    this.notifOpen.set(false);
  }

  closeUserMenu(): void {
    this.userMenuOpen.set(false);
  }

  openNewTask(): void {
    const dialogRef = this.dialog.open<TaskFormDialogComponent, unknown, CreateTaskRequest>(
      TaskFormDialogComponent,
    );
    dialogRef.afterClosed().subscribe((request) => {
      if (!request) {
        return;
      }
      this.taskStore
        .create(request)
        .then(() => this.snackBar.open(`Task "${request.title}" created.`, 'Dismiss', { duration: 4000 }))
        .catch(() => this.snackBar.open('Could not create task.', 'Dismiss', { duration: 4000 }));
    });
  }

  logout(): void {
    this.authStore.logout();
    void this.router.navigateByUrl('/login');
  }
}
