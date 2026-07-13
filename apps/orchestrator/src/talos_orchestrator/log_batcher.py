# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

"""Log flow (Section 6.2 core-services table / "Log flow" narrative): the orchestrator batches log
lines (50 lines or 2s) before POSTing them to /internal/v1/runs/{id}/logs.
"""

from __future__ import annotations

import time
from typing import Any

from talos_orchestrator.api_client import ApiClient


class LogBatcher:
    def __init__(self, api_client: ApiClient, run_id: str, flush_size: int = 50, flush_interval: float = 2.0) -> None:
        self._api_client = api_client
        self._run_id = run_id
        self._flush_size = flush_size
        self._flush_interval = flush_interval
        self._buffer: list[dict[str, Any]] = []
        self._last_flush = time.monotonic()
        self._sequence = 0

    async def add(self, stream: str, message: str, timestamp: str) -> None:
        self._sequence += 1
        self._buffer.append({"stream": stream, "sequence": self._sequence, "message": message, "timestamp": timestamp})
        if len(self._buffer) >= self._flush_size or (time.monotonic() - self._last_flush) >= self._flush_interval:
            await self.flush()

    async def flush(self) -> None:
        self._last_flush = time.monotonic()
        if not self._buffer:
            return
        entries, self._buffer = self._buffer, []
        await self._api_client.ingest_logs(self._run_id, entries)
