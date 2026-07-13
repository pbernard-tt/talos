# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

"""In-memory registry of actively-executing adapters, keyed by run id. The runner supervisor has
no database (Section 6.2: talos-api is the only writer to PostgreSQL) -- this registry is the only
place it tracks a run's live process, and it does not survive a restart (Section 6.3's crash
recovery lives in the orchestrator, which treats a run not in QUEUED as poisoned).
"""

from __future__ import annotations

from talos_agent_adapter_spec import AgentAdapter


class RunRegistry:
    def __init__(self) -> None:
        self._adapters: dict[str, AgentAdapter] = {}

    def register(self, run_id: str, adapter: AgentAdapter) -> None:
        self._adapters[run_id] = adapter

    def get(self, run_id: str) -> AgentAdapter | None:
        return self._adapters.get(run_id)

    def is_active(self, run_id: str) -> bool:
        return run_id in self._adapters

    def release(self, run_id: str) -> None:
        self._adapters.pop(run_id, None)


registry = RunRegistry()
