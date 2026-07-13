// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { describe, expect, it, vi } from 'vitest';

import { Approval } from '../api';
import { ApprovalInboxPage } from './approval-inbox.page';
import { ApprovalStore } from './approval.store';

describe('ApprovalInboxPage', () => {
  function setup() {
    const list = vi.fn().mockResolvedValue(undefined);
    const approvalStoreStub = {
      approvals: signal<Approval[]>([]),
      loading: signal(false),
      error: signal<string | null>(null),
      list,
    };

    TestBed.configureTestingModule({
      imports: [ApprovalInboxPage],
      providers: [provideRouter([]), { provide: ApprovalStore, useValue: approvalStoreStub }],
    });
    const fixture = TestBed.createComponent(ApprovalInboxPage);
    return { fixture, component: fixture.componentInstance, list };
  }

  it('loads PENDING approvals by default', () => {
    const { component, list } = setup();

    component.ngOnInit();

    expect(list).toHaveBeenCalledWith('PENDING');
    expect(component.showAll()).toBe(false);
  });

  it('toggling to "show all" reloads with no status filter', () => {
    const { component, list } = setup();
    component.ngOnInit();

    component.togglePendingOnly();

    expect(component.showAll()).toBe(true);
    expect(list).toHaveBeenLastCalledWith(undefined);
  });

  it('toggling back reloads PENDING only', () => {
    const { component, list } = setup();
    component.ngOnInit();
    component.togglePendingOnly();

    component.togglePendingOnly();

    expect(component.showAll()).toBe(false);
    expect(list).toHaveBeenLastCalledWith('PENDING');
  });
});
