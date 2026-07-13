# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

"""Env var reference: Appendix A "talos-runner-supervisor" of the implementation plan."""

from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    workspaces_root: str
    provider_homes_root: str
    internal_api_token: str
    api_base_url: str
    max_workspace_age_days: int
    # Phase 11 (Section 8/12.1): per-run Docker execution. Volume/network names must match
    # infra/docker-compose.dev.yml's (and the prod compose's) explicit `name:` fields exactly --
    # talos-runner-supervisor references them by fixed name, not by Compose's project-name-prefixed
    # default, since dev and prod compose files live in different directories (see
    # docs/security-model.md).
    run_workspaces_volume: str
    run_provider_homes_volume: str
    run_network: str
    run_memory_limit: str
    run_cpu_limit: str
    run_pids_limit: str


def load_settings() -> Settings:
    return Settings(
        workspaces_root=os.environ.get("TALOS_WORKSPACES_ROOT", "/var/talos/workspaces"),
        provider_homes_root=os.environ.get("TALOS_PROVIDER_HOMES_ROOT", "/var/talos/provider-homes"),
        # Dual use: (a) authenticates this service's own HTTP surface -- every endpoint except
        # /health requires it as X-Talos-Internal-Token (app.require_internal_token, closing
        # Phase 11 deviation 1); (b) authenticates the direct runner -> talos-api artifact-post
        # call (Phase 16, artifact_client.py).
        internal_api_token=os.environ.get("TALOS_INTERNAL_API_TOKEN", ""),
        api_base_url=os.environ.get("TALOS_API_BASE_URL", "http://talos-api:8080"),
        max_workspace_age_days=int(os.environ.get("TALOS_MAX_WORKSPACE_AGE_DAYS", "7")),
        run_workspaces_volume=os.environ.get("TALOS_RUN_WORKSPACES_VOLUME", "talos_workspaces"),
        run_provider_homes_volume=os.environ.get("TALOS_RUN_PROVIDER_HOMES_VOLUME", "talos_provider_homes"),
        run_network=os.environ.get("TALOS_RUN_NETWORK", "talos_run_network"),
        run_memory_limit=os.environ.get("TALOS_RUN_MEMORY_LIMIT", "1g"),
        run_cpu_limit=os.environ.get("TALOS_RUN_CPU_LIMIT", "1"),
        run_pids_limit=os.environ.get("TALOS_RUN_PIDS_LIMIT", "256"),
    )
