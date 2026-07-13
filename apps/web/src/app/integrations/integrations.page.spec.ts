// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { Integration, Role } from '../api';
import { AuthStore } from '../core/auth/auth.store';
import { IntegrationsPage } from './integrations.page';
import { IntegrationStore } from './integration.store';

function makeIntegration(overrides: Partial<Integration> = {}): Integration {
  return {
    id: 'integration-1',
    type: 'github',
    name: 'primary-github',
    enabled: true,
    createdAt: '2026-07-10T00:00:00Z',
    ...overrides,
  };
}

describe('IntegrationsPage', () => {
  function setup(role: Role = 'OWNER') {
    const load = vi.fn().mockResolvedValue(undefined);
    const create = vi.fn().mockResolvedValue(makeIntegration());
    const test = vi.fn().mockResolvedValue({ ok: true, message: 'Connection succeeded' });
    const integrationStoreStub = {
      integrations: signal<Integration[]>([]),
      loading: signal(false),
      error: signal<string | null>(null),
      load,
      create,
      test,
    };
    const authStoreStub = { hasRole: (minimum: Role) => (minimum === 'OWNER' ? role === 'OWNER' : true) };

    TestBed.configureTestingModule({
      imports: [IntegrationsPage],
      providers: [
        provideRouter([]),
        { provide: IntegrationStore, useValue: integrationStoreStub },
        { provide: AuthStore, useValue: authStoreStub },
      ],
    });
    const fixture = TestBed.createComponent(IntegrationsPage);
    const dialog = TestBed.inject(MatDialog);
    const snackBar = TestBed.inject(MatSnackBar);
    const snackBarSpy = vi.spyOn(snackBar, 'open');
    return { fixture, component: fixture.componentInstance, dialog, load, create, test, snackBarSpy };
  }

  it('loads integrations for an OWNER', () => {
    const { component, load } = setup('OWNER');

    component.ngOnInit();

    expect(load).toHaveBeenCalled();
  });

  it('does not attempt to load integrations for a non-OWNER (backend is OWNER-only)', () => {
    const { component, load } = setup('MAINTAINER');

    component.ngOnInit();

    expect(load).not.toHaveBeenCalled();
  });

  it('creating via the dialog calls store.create with the built request', async () => {
    const { component, dialog, create, snackBarSpy } = setup();
    const request = { type: 'github', name: 'primary-github' };
    vi.spyOn(dialog, 'open').mockReturnValue({ afterClosed: () => of(request) } as ReturnType<MatDialog['open']>);

    component.openCreateDialog();
    await Promise.resolve();
    await Promise.resolve();

    expect(create).toHaveBeenCalledWith(request);
    expect(snackBarSpy).toHaveBeenCalledWith(
      expect.stringContaining('primary-github'),
      'Dismiss',
      expect.anything(),
    );
  });

  it('testing an integration shows the result message from the API', async () => {
    const { component, test, snackBarSpy } = setup();

    component.test(makeIntegration());
    await Promise.resolve();
    await Promise.resolve();

    expect(test).toHaveBeenCalledWith('integration-1');
    expect(snackBarSpy).toHaveBeenCalledWith('Connection succeeded', 'Dismiss', expect.anything());
  });
});
