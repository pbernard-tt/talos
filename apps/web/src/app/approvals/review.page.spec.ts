// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { ApprovalDetail } from '../api';
import { ReviewPage } from './review.page';
import { ApprovalStore } from './approval.store';

function makeDetail(statusOverride: ApprovalDetail['approval']['status'] = 'PENDING'): ApprovalDetail {
  return {
    approval: {
      id: 'approval-1',
      taskId: 'task-1',
      runId: 'run-1',
      approvalType: 'RUN_RESULT',
      requestedAction: 'Review run results',
      status: statusOverride,
      createdAt: '2026-07-09T00:00:00Z',
    },
    run: {
      id: 'run-1',
      taskId: 'task-1',
      projectId: 'project-1',
      status: 'WAITING_APPROVAL',
      agentKey: 'claude-code',
      providerAuthMode: 'api_key',
      testStatus: 'PASSED',
      reviewStatus: 'CLEAN',
      createdAt: '2026-07-09T00:00:00Z',
      updatedAt: '2026-07-09T00:00:00Z',
    },
    changes: [],
  };
}

describe('ReviewPage', () => {
  function setup() {
    const approve = vi.fn().mockResolvedValue(undefined);
    const reject = vi.fn().mockResolvedValue(undefined);
    const requestChanges = vi.fn().mockResolvedValue(undefined);
    const approvalStoreStub = {
      detail: signal<ApprovalDetail | null>(makeDetail()),
      diffText: signal<string | undefined>('diff --git a/x b/x'),
      loading: signal(false),
      error: signal<string | null>(null),
      loadForRun: vi.fn().mockResolvedValue(undefined),
      approve,
      reject,
      requestChanges,
      clear: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [ReviewPage],
      providers: [provideRouter([]), { provide: ApprovalStore, useValue: approvalStoreStub }],
    });
    const fixture = TestBed.createComponent(ReviewPage);
    const dialog = TestBed.inject(MatDialog);
    const snackBar = TestBed.inject(MatSnackBar);
    const snackBarSpy = vi.spyOn(snackBar, 'open');
    return { fixture, component: fixture.componentInstance, dialog, approve, reject, requestChanges, snackBarSpy };
  }

  it('approving confirms via the dialog and calls store.approve with the entered notes', async () => {
    const { component, dialog, approve, snackBarSpy } = setup();
    vi.spyOn(dialog, 'open').mockReturnValue({ afterClosed: () => of({ notes: 'looks good' }) } as ReturnType<
      MatDialog['open']
    >);

    component.approve();
    await Promise.resolve();
    await Promise.resolve();

    expect(approve).toHaveBeenCalledWith('approval-1', 'looks good');
    expect(snackBarSpy).toHaveBeenCalledWith('Run approved.', 'Dismiss', expect.anything());
  });

  it('cancelling the confirmation dialog does not call store.approve', () => {
    const { component, dialog, approve } = setup();
    vi.spyOn(dialog, 'open').mockReturnValue({ afterClosed: () => of(undefined) } as ReturnType<MatDialog['open']>);

    component.approve();

    expect(approve).not.toHaveBeenCalled();
  });

  it('rejecting requires notes -- a confirm result with no notes does not call store.reject', () => {
    const { component, dialog, reject } = setup();
    vi.spyOn(dialog, 'open').mockReturnValue({ afterClosed: () => of({ notes: undefined }) } as ReturnType<
      MatDialog['open']
    >);

    component.reject();

    expect(reject).not.toHaveBeenCalled();
  });

  it('requesting changes with notes calls store.requestChanges and shows a snackbar', async () => {
    const { component, dialog, requestChanges, snackBarSpy } = setup();
    vi.spyOn(dialog, 'open').mockReturnValue({ afterClosed: () => of({ notes: 'add tests' }) } as ReturnType<
      MatDialog['open']
    >);

    component.requestChanges();
    await Promise.resolve();
    await Promise.resolve();

    expect(requestChanges).toHaveBeenCalledWith('approval-1', 'add tests');
    expect(snackBarSpy).toHaveBeenCalledWith('Changes requested.', 'Dismiss', expect.anything());
  });
});
