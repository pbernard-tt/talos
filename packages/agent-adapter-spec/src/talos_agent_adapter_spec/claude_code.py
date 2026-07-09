"""ClaudeCodeAdapter (Section 7.4 #2). Stub until Phase 7 -- do not implement ahead of the fixed
adapter order (CustomShellAdapter Phase 6 -> ClaudeCodeAdapter Phase 7)."""

from __future__ import annotations

from typing import AsyncIterator

from talos_agent_adapter_spec.adapter import (
    AgentAdapter,
    AgentEvent,
    AgentResult,
    AgentSessionRequest,
    ProviderCapabilities,
)

_NOT_IMPLEMENTED = "ClaudeCodeAdapter is implemented in Phase 7 (First real agent adapter)."


class ClaudeCodeAdapter(AgentAdapter):
    key = "claude-code"

    def capabilities(self) -> ProviderCapabilities:
        raise NotImplementedError(_NOT_IMPLEMENTED)

    async def start(self, request: AgentSessionRequest) -> None:
        raise NotImplementedError(_NOT_IMPLEMENTED)

    def events(self) -> AsyncIterator[AgentEvent]:
        raise NotImplementedError(_NOT_IMPLEMENTED)

    async def stop(self) -> None:
        raise NotImplementedError(_NOT_IMPLEMENTED)

    async def result(self) -> AgentResult:
        raise NotImplementedError(_NOT_IMPLEMENTED)
