import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { AuthService } from '../../api';

/**
 * JWT held in memory only (Section 15: "Email/password -> JWT in memory + refresh on load") —
 * never persisted to localStorage/sessionStorage, so a page reload requires logging in again.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
	private readonly authService = inject(AuthService);

	private readonly tokenSignal = signal<string | null>(null);
	readonly token = this.tokenSignal.asReadonly();
	readonly isAuthenticated = computed(() => this.tokenSignal() !== null);

	async login(email: string, password: string): Promise<void> {
		const response = await firstValueFrom(
			this.authService.login({ loginRequest: { email, password } }),
		);
		this.tokenSignal.set(response.token);
	}

	logout(): void {
		this.tokenSignal.set(null);
	}
}
