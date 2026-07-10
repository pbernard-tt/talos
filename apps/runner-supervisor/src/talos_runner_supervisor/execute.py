"""POST /runs/{id}/execute: run the selected AgentAdapter and stream its events as ndjson."""

from __future__ import annotations

import dataclasses
import json
from collections.abc import AsyncIterator
from pathlib import Path, PurePosixPath

from talos_agent_adapter_spec import AgentSessionRequest, ContainerConfig, ensure_network, get_adapter_class

from talos_runner_supervisor.config import Settings
from talos_runner_supervisor.run_registry import RunRegistry


class RunAlreadyExecutingError(RuntimeError):
    pass


def _volume_subpath(root: str, absolute_path: str) -> str:
    """The path relative to a mounted volume's root, for --mount ...,volume-subpath=... (Phase 11).
    E.g. root=/var/talos/workspaces, absolute_path=/var/talos/workspaces/demo/runs/r1/worktree
    -> "demo/runs/r1/worktree"."""
    return str(PurePosixPath(absolute_path).relative_to(PurePosixPath(root)))


async def execute_run(
    settings: Settings,
    registry: RunRegistry,
    run_id: str,
    adapter_key: str,
    workspace_path: str,
    prompt: str,
    env: dict[str, str],
    auth_mode: str,
    timeout_seconds: int,
    container_image: str | None = None,
) -> AsyncIterator[str]:
    if registry.is_active(run_id):
        raise RunAlreadyExecutingError(run_id)

    provider_home = str(Path(settings.provider_homes_root) / adapter_key)
    Path(provider_home).mkdir(parents=True, exist_ok=True)

    container = None
    if container_image is not None:
        container = ContainerConfig(
            image=container_image,
            workspace_volume=settings.run_workspaces_volume,
            workspace_subpath=_volume_subpath(settings.workspaces_root, workspace_path),
            provider_homes_volume=settings.run_provider_homes_volume,
            provider_home_subpath=adapter_key,
            network=settings.run_network,
            memory_limit=settings.run_memory_limit,
            cpu_limit=settings.run_cpu_limit,
            pids_limit=settings.run_pids_limit,
        )
        await ensure_network(container.network)

    adapter_cls = get_adapter_class(adapter_key)
    adapter = adapter_cls()
    request = AgentSessionRequest(
        run_id=run_id,
        workspace_path=workspace_path,
        prompt=prompt,
        env=env,
        auth_mode=auth_mode,
        provider_home=provider_home,
        timeout_seconds=timeout_seconds,
        container=container,
    )

    await adapter.start(request)
    registry.register(run_id, adapter)

    async def generator() -> AsyncIterator[str]:
        try:
            async for event in adapter.events():
                yield json.dumps(dataclasses.asdict(event)) + "\n"
            result = await adapter.result()
            yield json.dumps({"type": "result", **dataclasses.asdict(result)}) + "\n"
        finally:
            registry.release(run_id)

    return generator()
