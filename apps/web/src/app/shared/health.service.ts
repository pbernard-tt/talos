// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { Configuration } from '../api';

export interface ActuatorHealth {
  status: 'UP' | 'DOWN' | string;
  components?: Record<string, { status: string }>;
}

/** GET /actuator/health (permitAll, Section 12.2) -- the only service health the browser can reach
 * directly. talos-orchestrator and talos-runner-supervisor have no host-mapped port in
 * infra/docker-compose.dev.yml, so their health isn't checkable from here. Actuator isn't under the
 * /api/v1 prefix the generated API client uses, so the URL is derived from Configuration.basePath
 * (`{apiOrigin}/api/v1`) rather than going through a generated service. */
@Injectable({ providedIn: 'root' })
export class HealthService {
  private readonly http = inject(HttpClient);
  private readonly apiConfig = inject(Configuration);

  private readonly apiHealthSignal = signal<ActuatorHealth | null>(null);
  private readonly errorSignal = signal<string | null>(null);

  readonly apiHealth = this.apiHealthSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();

  async check(): Promise<void> {
    const origin = (this.apiConfig.basePath ?? '').replace(/\/api\/v1\/?$/, '');
    try {
      const health = await firstValueFrom(this.http.get<ActuatorHealth>(`${origin}/actuator/health`));
      this.apiHealthSignal.set(health);
      this.errorSignal.set(null);
    } catch {
      this.apiHealthSignal.set(null);
      this.errorSignal.set('Could not reach talos-api.');
    }
  }
}
