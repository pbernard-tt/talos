"""Env var reference: Appendix A "talos-runner-supervisor" of the implementation plan."""

from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    workspaces_root: str
    provider_homes_root: str
    internal_api_token: str
    max_workspace_age_days: int


def load_settings() -> Settings:
    return Settings(
        workspaces_root=os.environ.get("TALOS_WORKSPACES_ROOT", "/var/talos/workspaces"),
        provider_homes_root=os.environ.get("TALOS_PROVIDER_HOMES_ROOT", "/var/talos/provider-homes"),
        # Not used to authenticate this service's own HTTP surface in Phase 6 (runner-api.yaml is
        # unauthenticated -- it's reachable only on the internal Docker network); provisioned here
        # for a possible future direct runner -> talos-api call (Appendix A lists it for
        # "artifact/log posts", which today are relayed through the orchestrator instead).
        internal_api_token=os.environ.get("TALOS_INTERNAL_API_TOKEN", ""),
        max_workspace_age_days=int(os.environ.get("TALOS_MAX_WORKSPACE_AGE_DAYS", "7")),
    )
