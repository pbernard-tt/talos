# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: Apache-2.0

"""Adapter registry keyed by AgentAdapter.key (Section 7.4). Selection only -- instantiating a
not-yet-implemented adapter class succeeds; calling any of its methods raises NotImplementedError."""

from __future__ import annotations

from talos_agent_adapter_spec.adapter import AgentAdapter
from talos_agent_adapter_spec.claude_code import ClaudeCodeAdapter
from talos_agent_adapter_spec.codex_cli import CodexCliAdapter
from talos_agent_adapter_spec.custom_shell import CustomShellAdapter
from talos_agent_adapter_spec.gemini_cli import GeminiCliAdapter
from talos_agent_adapter_spec.opencode import OpenCodeAdapter
from talos_agent_adapter_spec.openhands import OpenHandsAdapter

ADAPTERS: dict[str, type[AgentAdapter]] = {
    CustomShellAdapter.key: CustomShellAdapter,
    ClaudeCodeAdapter.key: ClaudeCodeAdapter,
    OpenCodeAdapter.key: OpenCodeAdapter,
    CodexCliAdapter.key: CodexCliAdapter,
    OpenHandsAdapter.key: OpenHandsAdapter,
    GeminiCliAdapter.key: GeminiCliAdapter,
}


def get_adapter_class(agent_key: str) -> type[AgentAdapter]:
    try:
        return ADAPTERS[agent_key]
    except KeyError:
        raise ValueError(f"Unknown adapter key: {agent_key!r}. Registered: {sorted(ADAPTERS)}") from None
