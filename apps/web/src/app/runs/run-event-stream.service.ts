// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Injectable, inject } from '@angular/core';

import { Configuration } from '../api';
import { AuthStore } from '../core/auth/auth.store';

export interface RunStreamEvent {
  type: 'log' | 'status' | 'step';
  id?: string;
  data: unknown;
}

/**
 * Section 10.3's SSE stream, consumed via fetch + a streaming reader rather than native
 * EventSource: EventSource cannot send the Authorization header this endpoint requires (JWT-only,
 * Section 12.2), and there's no cookie session to fall back on. Reconnects with Last-Event-ID on
 * drop; stops when the caller aborts `signal`.
 */
@Injectable({ providedIn: 'root' })
export class RunEventStreamService {
  private readonly configuration = inject(Configuration);
  private readonly authStore = inject(AuthStore);

  async listen(runId: string, onEvent: (event: RunStreamEvent) => void, signal: AbortSignal): Promise<void> {
    let lastEventId: string | undefined;
    while (!signal.aborted) {
      try {
        await this.connectOnce(
          runId,
          lastEventId,
          (event) => {
            if (event.id) {
              lastEventId = event.id;
            }
            onEvent(event);
          },
          signal,
        );
      } catch {
        // connection dropped or failed to open -- fall through and retry below unless aborted
      }
      if (signal.aborted) {
        return;
      }
      await new Promise((resolve) => setTimeout(resolve, 2000));
    }
  }

  private async connectOnce(
    runId: string,
    lastEventId: string | undefined,
    onEvent: (event: RunStreamEvent) => void,
    signal: AbortSignal,
  ): Promise<void> {
    const headers: Record<string, string> = {};
    const token = this.authStore.token();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    if (lastEventId) {
      headers['Last-Event-ID'] = lastEventId;
    }

    const response = await fetch(`${this.configuration.basePath}/runs/${runId}/events/stream`, {
      headers,
      signal,
    });
    if (!response.ok || !response.body) {
      throw new Error(`SSE connection failed: ${response.status}`);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    for (;;) {
      const { value, done } = await reader.read();
      if (done) {
        return;
      }
      buffer += decoder.decode(value, { stream: true });
      let boundary = buffer.indexOf('\n\n');
      while (boundary !== -1) {
        const parsed = parseSseFrame(buffer.slice(0, boundary));
        if (parsed) {
          onEvent(parsed);
        }
        buffer = buffer.slice(boundary + 2);
        boundary = buffer.indexOf('\n\n');
      }
    }
  }
}

function parseSseFrame(raw: string): RunStreamEvent | null {
  let type: RunStreamEvent['type'] | null = null;
  let id: string | undefined;
  const dataLines: string[] = [];
  for (const line of raw.split('\n')) {
    if (line.startsWith('event:')) {
      type = line.slice('event:'.length).trim() as RunStreamEvent['type'];
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trim());
    } else if (line.startsWith('id:')) {
      id = line.slice('id:'.length).trim();
    }
  }
  if (!type || dataLines.length === 0) {
    return null; // heartbeat comment or malformed frame
  }
  try {
    return { type, id, data: JSON.parse(dataLines.join('\n')) };
  } catch {
    return null;
  }
}
