"""Adapter selection from the registry in packages/agent-adapter-spec. The orchestrator only needs
an adapter's capabilities (e.g. default_timeout_seconds) to drive the pipeline -- actual execution
happens inside the runner supervisor (Section 7: "Adapters ... execute inside the runner
supervisor"), reached only via runner_client.execute_run().
"""

from __future__ import annotations

from talos_agent_adapter_spec import ProviderCapabilities, get_adapter_class


def capabilities_for(agent_key: str) -> ProviderCapabilities:
    """Raises ValueError for an unregistered key, NotImplementedError for a registered-but-stub
    adapter (Section 7.4's fixed order -- e.g. claude-code before Phase 7)."""
    adapter_cls = get_adapter_class(agent_key)
    return adapter_cls().capabilities()
