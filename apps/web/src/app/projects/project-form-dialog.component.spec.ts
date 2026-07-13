// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { describe, expect, it, vi } from 'vitest';

import { ProjectFormDialogComponent } from './project-form-dialog.component';

describe('ProjectFormDialogComponent', () => {
  function setup() {
    const dialogRefSpy = { close: vi.fn() };
    TestBed.configureTestingModule({
      imports: [ProjectFormDialogComponent],
      providers: [{ provide: MatDialogRef, useValue: dialogRefSpy }],
    });
    const fixture = TestBed.createComponent(ProjectFormDialogComponent);
    return { fixture, component: fixture.componentInstance, dialogRefSpy };
  }

  it('is invalid until name, repoUrl, and stackType are filled', () => {
    const { component } = setup();

    expect(component.form.valid).toBe(false);

    component.form.setValue({
      name: 'Example Backend',
      repoUrl: 'git@github.com:org/example-backend.git',
      defaultBranch: '',
      stackType: 'spring-boot',
    });

    expect(component.form.valid).toBe(true);
  });

  it('save() closes the dialog with a request that omits a blank defaultBranch', () => {
    const { component, dialogRefSpy } = setup();

    component.form.setValue({
      name: 'Example Backend',
      repoUrl: 'git@github.com:org/example-backend.git',
      defaultBranch: '',
      stackType: 'spring-boot',
    });
    component.save();

    expect(dialogRefSpy.close).toHaveBeenCalledWith({
      name: 'Example Backend',
      repoUrl: 'git@github.com:org/example-backend.git',
      stackType: 'spring-boot',
    });
  });

  it('save() includes defaultBranch when provided', () => {
    const { component, dialogRefSpy } = setup();

    component.form.setValue({
      name: 'Example Backend',
      repoUrl: 'git@github.com:org/example-backend.git',
      defaultBranch: 'develop',
      stackType: 'spring-boot',
    });
    component.save();

    expect(dialogRefSpy.close).toHaveBeenCalledWith({
      name: 'Example Backend',
      repoUrl: 'git@github.com:org/example-backend.git',
      stackType: 'spring-boot',
      defaultBranch: 'develop',
    });
  });

  it('save() does nothing while the form is invalid', () => {
    const { component, dialogRefSpy } = setup();

    component.save();

    expect(dialogRefSpy.close).not.toHaveBeenCalled();
  });
});
