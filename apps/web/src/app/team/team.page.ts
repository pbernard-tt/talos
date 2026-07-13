// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { DatePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';

import { CreateUserRequest, Role, User } from '../api';
import { AuthStore } from '../core/auth/auth.store';
import { StatusBadgeComponent } from '../shared/badges/status-badge.component';
import { UserFormDialogComponent } from './user-form-dialog.component';
import { TeamStore } from './team.store';

const ROLES: Role[] = ['OWNER', 'MAINTAINER', 'REVIEWER', 'VIEWER'];

/** Talos.dc.html's "Team" screen. GET/POST/PATCH /users are all OWNER only (Section 16 Phase 15),
 * so this page doesn't attempt the load for lower roles -- same pattern as IntegrationsPage. */
@Component({
  selector: 'app-team-page',
  imports: [DatePipe, MatButtonModule, MatProgressSpinnerModule, StatusBadgeComponent],
  templateUrl: './team.page.html',
  styleUrl: './team.page.scss',
})
export class TeamPage implements OnInit {
  protected readonly store = inject(TeamStore);
  protected readonly authStore = inject(AuthStore);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly roles = ROLES;

  ngOnInit(): void {
    if (this.authStore.hasRole('OWNER')) {
      void this.store.load();
    }
  }

  isSelf(user: User): boolean {
    return user.email === this.authStore.email();
  }

  openCreateDialog(): void {
    const dialogRef = this.dialog.open<UserFormDialogComponent, unknown, CreateUserRequest>(UserFormDialogComponent);
    dialogRef.afterClosed().subscribe((request) => {
      if (!request) {
        return;
      }
      this.store
        .create(request)
        .then(() => this.snackBar.open(`User "${request.name}" created.`, 'Dismiss', { duration: 4000 }))
        .catch(() => this.snackBar.open('Could not create the user.', 'Dismiss', { duration: 4000 }));
    });
  }

  changeRole(user: User, role: string): void {
    this.store
      .update(user.id, { role: role as Role })
      .catch(() => this.snackBar.open('Could not change the role.', 'Dismiss', { duration: 4000 }));
  }

  toggleActive(user: User): void {
    this.store
      .update(user.id, { active: !user.active })
      .catch(() => this.snackBar.open('Could not update the user.', 'Dismiss', { duration: 4000 }));
  }
}
