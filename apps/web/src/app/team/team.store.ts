// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { CreateUserRequest, UpdateUserRequest, User, UsersService } from '../api';

/** One signal-based store per domain (Section 6.1). Backs /team -- GET/POST /users and
 * PATCH /users/{id} are all OWNER only (Section 16 Phase 15). */
@Injectable({ providedIn: 'root' })
export class TeamStore {
  private readonly usersService = inject(UsersService);

  private readonly usersSignal = signal<User[]>([]);
  private readonly loadingSignal = signal(false);
  private readonly errorSignal = signal<string | null>(null);

  readonly users = this.usersSignal.asReadonly();
  readonly loading = this.loadingSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();

  async load(): Promise<void> {
    this.loadingSignal.set(true);
    this.errorSignal.set(null);
    try {
      const users = await firstValueFrom(this.usersService.listUsers());
      this.usersSignal.set(users);
    } catch {
      this.errorSignal.set('Could not load users.');
    } finally {
      this.loadingSignal.set(false);
    }
  }

  async create(request: CreateUserRequest): Promise<User> {
    const user = await firstValueFrom(this.usersService.createUser({ createUserRequest: request }));
    this.usersSignal.update((users) => [...users, user]);
    return user;
  }

  async update(id: string, request: UpdateUserRequest): Promise<void> {
    const user = await firstValueFrom(this.usersService.updateUser({ id, updateUserRequest: request }));
    this.usersSignal.update((users) => users.map((u) => (u.id === id ? user : u)));
  }
}
