// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthStore } from './auth.store';

export const authGuard: CanActivateFn = () => {
	const authStore = inject(AuthStore);
	if (authStore.isAuthenticated()) {
		return true;
	}
	return inject(Router).parseUrl('/login');
};
