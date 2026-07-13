// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { AuthService, Role } from '../../api';

/** Section 9.3/Phase 15: a strict hierarchy -- each role implies every permission of the ones
 * after it. Mirrors the API's RoleHierarchy bean (dev.talos.auth.SecurityConfig). */
const ROLE_HIERARCHY_HIGH_TO_LOW: Role[] = ['OWNER', 'MAINTAINER', 'REVIEWER', 'VIEWER'];

/**
 * JWT held in memory only (Section 15: "Email/password -> JWT in memory + refresh on load") —
 * never persisted to localStorage/sessionStorage, so a page reload requires logging in again.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
	private readonly authService = inject(AuthService);

	private readonly tokenSignal = signal<string | null>(null);
	private readonly roleSignal = signal<Role | null>(null);
	private readonly emailSignal = signal<string | null>(null);

	readonly token = this.tokenSignal.asReadonly();
	readonly role = this.roleSignal.asReadonly();
	readonly email = this.emailSignal.asReadonly();
	readonly isAuthenticated = computed(() => this.tokenSignal() !== null);

	async login(email: string, password: string): Promise<void> {
		const response = await firstValueFrom(
			this.authService.login({ loginRequest: { email, password } }),
		);
		this.tokenSignal.set(response.token);
		this.roleSignal.set(response.role);
		this.emailSignal.set(response.email);
	}

	logout(): void {
		this.tokenSignal.set(null);
		this.roleSignal.set(null);
		this.emailSignal.set(null);
	}

	/** Section 16 Phase 15: a UI-hiding hint only ("UI hides actions the current role cannot
	 * perform, but enforcement is server-side") -- never treat this as the real gate. */
	hasRole(minimum: Role): boolean {
		const role = this.roleSignal();
		if (!role) {
			return false;
		}
		return ROLE_HIERARCHY_HIGH_TO_LOW.indexOf(role) <= ROLE_HIERARCHY_HIGH_TO_LOW.indexOf(minimum);
	}
}
