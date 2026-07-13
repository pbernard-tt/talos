// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';

import { MemorySourceType } from '../api';
import { ProjectStore } from '../projects/project.store';
import { MemoryStore } from './memory.store';

const SOURCE_TYPES: MemorySourceType[] = ['OPERATOR_NOTE', 'CONTEXT_DOC', 'RUN_SUMMARY'];

/** Talos.dc.html's "Memory & Docs" screen. Only ingestion is exposed to the browser (see
 * MemoryStore) -- this deliberately doesn't render a fabricated "chunks indexed" count or search
 * box, since neither is backed by a public read endpoint. */
@Component({
  selector: 'app-memory-page',
  imports: [DatePipe, ReactiveFormsModule, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './memory.page.html',
  styleUrl: './memory.page.scss',
})
export class MemoryPage implements OnInit {
  protected readonly projectStore = inject(ProjectStore);
  protected readonly store = inject(MemoryStore);
  private readonly formBuilder = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly sourceTypes = SOURCE_TYPES;
  protected readonly selectedProjectId = signal('');

  protected readonly form = this.formBuilder.nonNullable.group({
    sourceType: ['OPERATOR_NOTE' as MemorySourceType, Validators.required],
    title: [''],
    sourceRef: [''],
    content: ['', Validators.required],
  });

  ngOnInit(): void {
    if (this.projectStore.projects().length === 0) {
      void this.projectStore.load();
    }
  }

  selectProject(id: string): void {
    this.selectedProjectId.set(id);
    this.store.clear();
  }

  ingest(): void {
    const projectId = this.selectedProjectId();
    if (!projectId || this.form.invalid) {
      return;
    }
    const value = this.form.getRawValue();
    this.store
      .ingest(projectId, {
        sourceType: value.sourceType,
        content: value.content,
        ...(value.title ? { title: value.title } : {}),
        ...(value.sourceRef ? { sourceRef: value.sourceRef } : {}),
      })
      .then(() => {
        this.snackBar.open('Document ingested into project memory.', 'Dismiss', { duration: 4000 });
        this.form.reset({ sourceType: 'OPERATOR_NOTE', title: '', sourceRef: '', content: '' });
      })
      .catch(() => this.snackBar.open('Could not ingest the document.', 'Dismiss', { duration: 4000 }));
  }
}
