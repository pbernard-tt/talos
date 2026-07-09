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
