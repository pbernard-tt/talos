// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { EnvironmentProviders, makeEnvironmentProviders, provideAppInitializer } from '@angular/core';

import { Configuration } from '../api';

interface EnvConfig {
	apiUrl: string;
}

const DEV_DEFAULT_API_URL = 'http://localhost:8080';

let resolvedApiUrl = DEV_DEFAULT_API_URL;

/**
 * Reads /assets/env-config.json (written by apps/web/docker-entrypoint.sh from TALOS_API_URL at
 * container start) before the app renders. Falls back to the local dev API when that file
 * doesn't exist, e.g. under `ng serve`.
 */
export function provideApiConfig(): EnvironmentProviders {
	return makeEnvironmentProviders([
		provideAppInitializer(async () => {
			try {
				const response = await fetch('/assets/env-config.json');
				if (response.ok) {
					const config = (await response.json()) as EnvConfig;
					if (config.apiUrl) {
						resolvedApiUrl = config.apiUrl;
					}
				}
			} catch {
				// No env-config.json served (e.g. `ng serve`); keep the dev default.
			}
		}),
		{
			provide: Configuration,
			useFactory: () => new Configuration({ basePath: `${resolvedApiUrl}/api/v1` }),
		},
	]);
}
