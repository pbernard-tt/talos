"""CodexCliAdapter (Section 7.4 #4). Stub until Phase 12 -- fixed adapter order forbids
implementing this ahead of ClaudeCodeAdapter (Phase 7)."""

from __future__ import annotations

from typing import AsyncIterator

from talos_agent_adapter_spec.adapter import (
    AgentAdapter,
    AgentEvent,
    AgentResult,
    AgentSessionRequest,
    ProviderCapabilities,
)

_NOT_IMPLEMENTED = "CodexCliAdapter is implemented in Phase 12 (post-MVP additional adapters)."


class CodexCliAdapter(AgentAdapter):
    key = "codex-cli"

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
