// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { MemoryDocument, MemoryDocumentRequest, MemoryService } from '../api';

/** Backs /memory. The API only exposes POST /projects/{id}/memory/documents (ingest) to the
 * browser -- reading back or searching indexed memory is internal-only
 * (/internal/v1/projects/{id}/memory/search, service-token auth for the orchestrator), so this
 * store can't list what's already indexed. It only tracks documents ingested this session,
 * built from the real ingest responses -- never a fabricated "indexed content" list. */
@Injectable({ providedIn: 'root' })
export class MemoryStore {
  private readonly memoryService = inject(MemoryService);

  private readonly ingestedSignal = signal<MemoryDocument[]>([]);
  private readonly errorSignal = signal<string | null>(null);
  private readonly ingestingSignal = signal(false);

  readonly ingested = this.ingestedSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();
  readonly ingesting = this.ingestingSignal.asReadonly();

  async ingest(projectId: string, request: MemoryDocumentRequest): Promise<MemoryDocument> {
    this.ingestingSignal.set(true);
    this.errorSignal.set(null);
    try {
      const document = await firstValueFrom(
        this.memoryService.ingestProjectMemoryDocument({ projectId, memoryDocumentRequest: request }),
      );
      this.ingestedSignal.update((docs) => [document, ...docs]);
      return document;
    } catch (err) {
      this.errorSignal.set('Could not ingest the document.');
      throw err;
    } finally {
      this.ingestingSignal.set(false);
    }
  }

  clear(): void {
    this.ingestedSignal.set([]);
    this.errorSignal.set(null);
  }
}
