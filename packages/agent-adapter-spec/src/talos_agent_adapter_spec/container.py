# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: Apache-2.0

"""Per-run Docker execution (Phase 11, Section 8/12.1): wraps an adapter's command in `docker run`
so the agent process is isolated in its own container instead of sharing talos-runner-supervisor's
own filesystem/process namespace. talos-runner-supervisor holds the Docker socket to launch these;
the containers built here never do -- no docker.sock mount appears anywhere below, by construction.

Each container mounts only *its own* run's worktree subpath (not the whole shared workspaces
volume) via `--mount ...,volume-subpath=...` (Docker 25+), so one run's agent cannot traverse into
another run's workspace even though they share the same underlying named volume.
"""

from __future__ import annotations

import asyncio
import os
import stat
import tempfile
from dataclasses import dataclass


@dataclass
class ContainerConfig:
    image: str
    workspace_volume: str
    workspace_subpath: str  # e.g. "demo/runs/<run_id>/worktree", relative to the workspaces volume root
    provider_homes_volume: str
    provider_home_subpath: str  # e.g. "claude-code" -- shared across runs of the same agent, not per-run
    network: str
    memory_limit: str = "1g"
    cpu_limit: str = "1"
    pids_limit: str = "256"


WORKSPACE_MOUNT_TARGET = "/workspace"
PROVIDER_HOME_MOUNT_TARGET = "/provider-home"


def container_name(run_id: str) -> str:
    return f"talos-run-{run_id}"


def write_env_file(env: dict[str, str]) -> str:
    """Secrets reach the container via --env-file, never -e argv (which `ps` on the host can see).
    Caller deletes the returned path once `docker run` has started the container -- the file is
    read once at container-creation time, not re-read afterward."""
    fd, path = tempfile.mkstemp(prefix="talos-run-env-", suffix=".env")
    with os.fdopen(fd, "w") as f:
        for key, value in env.items():
            f.write(f"{key}={value}\n")
    os.chmod(path, stat.S_IRUSR | stat.S_IWUSR)
    return path


def build_docker_run_args(run_id: str, config: ContainerConfig, env_file_path: str, command: list[str]) -> list[str]:
    return [
        "docker",
        "run",
        "--rm",
        "--name",
        container_name(run_id),
        "--network",
        config.network,
        "--memory",
        config.memory_limit,
        "--cpus",
        config.cpu_limit,
        "--pids-limit",
        config.pids_limit,
        "--cap-drop",
        "ALL",
        "--security-opt",
        "no-new-privileges",
        "--mount",
        f"type=volume,source={config.workspace_volume},target={WORKSPACE_MOUNT_TARGET},"
        f"volume-subpath={config.workspace_subpath}",
        "--mount",
        f"type=volume,source={config.provider_homes_volume},target={PROVIDER_HOME_MOUNT_TARGET},"
        f"volume-subpath={config.provider_home_subpath}",
        "--env-file",
        env_file_path,
        "-w",
        WORKSPACE_MOUNT_TARGET,
        config.image,
        *command,
    ]


async def ensure_network(network: str) -> None:
    """Idempotently create the isolated per-run network if it doesn't already exist yet. Compose
    never creates `talos_run_network` itself (deliberately -- see infra/docker-compose.dev.yml's
    comment: attaching talos-runner-supervisor's own network interface to it so Compose would create
    it would let per-run containers reach the supervisor's own unauthenticated HTTP API, a worse
    leak than the isolation this network provides). talos-runner-supervisor already holds the Docker
    socket, so it owns this instead. Safe to call on every containerized run: a nonzero exit here
    almost always just means the network already exists."""
    proc = await asyncio.create_subprocess_exec(
        "docker",
        "network",
        "create",
        network,
        stdout=asyncio.subprocess.DEVNULL,
        stderr=asyncio.subprocess.DEVNULL,
    )
    await proc.wait()


async def kill_container(run_id: str) -> None:
    """Best-effort extra safety net: --rm plus the adapter's own process-group kill of the `docker
    run` client usually suffice, but if that client process was itself killed before it could proxy
    the signal into the container, the container could be orphaned running. This guarantees it dies,
    keyed by the deterministic name -- safe to call even if the container has already exited."""
    proc = await asyncio.create_subprocess_exec(
        "docker",
        "kill",
        container_name(run_id),
        stdout=asyncio.subprocess.DEVNULL,
        stderr=asyncio.subprocess.DEVNULL,
    )
    await proc.wait()
