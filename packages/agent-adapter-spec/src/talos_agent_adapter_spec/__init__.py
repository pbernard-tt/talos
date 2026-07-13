# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: Apache-2.0

from talos_agent_adapter_spec.adapter import (
    AgentAdapter,
    AgentEvent,
    AgentEventType,
    AgentResult,
    AgentSessionRequest,
    ProviderCapabilities,
)
from talos_agent_adapter_spec.container import ContainerConfig, ensure_network
from talos_agent_adapter_spec.custom_shell import CustomShellAdapter
from talos_agent_adapter_spec.claude_code import ClaudeCodeAdapter
from talos_agent_adapter_spec.codex_cli import CodexCliAdapter
from talos_agent_adapter_spec.opencode import OpenCodeAdapter
from talos_agent_adapter_spec.openhands import OpenHandsAdapter
from talos_agent_adapter_spec.registry import ADAPTERS, get_adapter_class

__all__ = [
    "ADAPTERS",
    "AgentAdapter",
    "AgentEvent",
    "AgentEventType",
    "AgentResult",
    "AgentSessionRequest",
    "CodexCliAdapter",
    "ContainerConfig",
    "CustomShellAdapter",
    "ClaudeCodeAdapter",
    "OpenCodeAdapter",
    "OpenHandsAdapter",
    "ProviderCapabilities",
    "ensure_network",
    "get_adapter_class",
]
