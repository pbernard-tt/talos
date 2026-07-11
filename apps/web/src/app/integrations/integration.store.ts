import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { Integration, IntegrationCreateRequest, IntegrationsService, TestIntegrationResponse } from '../api';

/** One signal-based store per domain (Section 6.1); backs the OWNER-only /integrations admin page
 * (review gap #6 -- the API existed with no UI consumer). */
@Injectable({ providedIn: 'root' })
export class IntegrationStore {
  private readonly integrationsService = inject(IntegrationsService);

  private readonly integrationsSignal = signal<Integration[]>([]);
  private readonly loadingSignal = signal(false);
  private readonly errorSignal = signal<string | null>(null);

  readonly integrations = this.integrationsSignal.asReadonly();
  readonly loading = this.loadingSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();

  async load(): Promise<void> {
    this.loadingSignal.set(true);
    this.errorSignal.set(null);
    try {
      const integrations = await firstValueFrom(this.integrationsService.listIntegrations());
      this.integrationsSignal.set(integrations);
    } catch {
      this.errorSignal.set('Could not load integrations.');
    } finally {
      this.loadingSignal.set(false);
    }
  }

  async create(request: IntegrationCreateRequest): Promise<Integration> {
    const integration = await firstValueFrom(
      this.integrationsService.createIntegration({ integrationCreateRequest: request }),
    );
    this.integrationsSignal.update((integrations) => [integration, ...integrations]);
    return integration;
  }

  async test(id: string): Promise<TestIntegrationResponse> {
    return firstValueFrom(this.integrationsService.testIntegration({ id }));
  }
}
