// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, OnInit, computed, inject } from '@angular/core';

import { HealthService } from '../shared/health.service';
import { StatusBadgeComponent } from '../shared/badges/status-badge.component';
import { StatusTone } from '../shared/badges/status-tone';

interface ServiceRow {
  name: string;
  kind: string;
  status: string;
  tone: StatusTone;
  extra?: string;
}

/** Talos.dc.html's "System Health" screen. talos-api's row is live (GET /actuator/health, which
 * Spring Boot's own indicators aggregate from Postgres/RabbitMQ/Redis -- see SecurityConfig's
 * management.endpoints comment). talos-orchestrator has no host-mapped port in
 * infra/docker-compose.dev.yml at all -- it only consumes from RabbitMQ, never serves HTTP.
 * talos-runner-supervisor's :8081 is host-mapped (for its own Docker healthcheck), but it has no
 * CORS middleware and, per CLAUDE.md's architecture ("the browser calls only talos-api" --
 * Section 6's four communication paths), isn't meant to be browser-facing at all; giving it CORS
 * just to light up a dot here would be the wrong fix. Both rows say so rather than showing a
 * fabricated green dot. */
@Component({
  selector: 'app-system-health-page',
  imports: [StatusBadgeComponent],
  templateUrl: './system-health.page.html',
  styleUrl: './system-health.page.scss',
})
export class SystemHealthPage implements OnInit {
  protected readonly healthService = inject(HealthService);

  protected readonly apiComponents = computed(() => {
    const components = this.healthService.apiHealth()?.components ?? {};
    return Object.entries(components).map(([name, value]) => ({ name, status: value.status }));
  });

  protected readonly apiRow = computed<ServiceRow>(() => {
    const health = this.healthService.apiHealth();
    if (health) {
      return {
        name: 'talos-api',
        kind: 'Spring Boot -- REST, persistence, auth, SSE fan-out',
        status: health.status,
        tone: health.status === 'UP' ? 'success' : 'error',
      };
    }
    return {
      name: 'talos-api',
      kind: 'Spring Boot -- REST, persistence, auth, SSE fan-out',
      status: this.healthService.error() ? 'UNREACHABLE' : 'CHECKING',
      tone: this.healthService.error() ? 'error' : 'neutral',
    };
  });

  protected readonly unreachableRows: ServiceRow[] = [
    {
      name: 'talos-orchestrator',
      kind: 'Python -- stateless run pipeline driver',
      status: 'NOT EXPOSED',
      tone: 'neutral',
      extra: 'No host-mapped port in infra/docker-compose.dev.yml -- only reachable on the internal Docker network.',
    },
    {
      name: 'talos-runner-supervisor',
      kind: 'Python/FastAPI -- worktrees, adapter execution, log streaming',
      status: 'NOT EXPOSED',
      tone: 'neutral',
      extra: 'Its :8081 healthcheck port is only mapped for the container\'s own Docker healthcheck -- no CORS middleware, and only talos-orchestrator is meant to call it directly.',
    },
  ];

  ngOnInit(): void {
    void this.healthService.check();
  }

  refresh(): void {
    void this.healthService.check();
  }
}
