# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: Apache-2.0

"""Real per-run Docker execution (Phase 11): exercises CustomShellAdapter with request.container
set against a real Docker daemon and a real named volume with `volume-subpath` isolation -- not
mocked, mirroring the contract suite's "test real behavior when possible" philosophy. Skipped
automatically when Docker isn't available (e.g. a constrained CI sandbox)."""

from __future__ import annotations

import shutil
import subprocess
import uuid

import pytest

from talos_agent_adapter_spec import AgentSessionRequest, ContainerConfig, CustomShellAdapter, ensure_network

pytestmark = pytest.mark.skipif(shutil.which("docker") is None, reason="Docker not available")

_TEST_IMAGE = "busybox:latest"


def _docker(*args: str, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(["docker", *args], capture_output=True, text=True, check=check)


@pytest.fixture
def workspaces_volume():
    name = f"talos-test-workspaces-{uuid.uuid4().hex[:8]}"
    _docker("volume", "create", name)
    yield name
    _docker("volume", "rm", "-f", name, check=False)


@pytest.fixture
def provider_homes_volume():
    # volume-subpath mounts require the subpath to already exist (it does not auto-create it, unlike
    # a plain bind-mount) -- in production this is guaranteed by execute.py's own
    # Path(provider_home).mkdir(parents=True, exist_ok=True) before the container is ever spawned.
    name = f"talos-test-provider-homes-{uuid.uuid4().hex[:8]}"
    _docker("volume", "create", name)
    _docker(
        "run", "--rm",
        "--mount", f"type=volume,source={name},target=/seed",
        _TEST_IMAGE, "mkdir", "-p", "/seed/custom-shell",
    )
    yield name
    _docker("volume", "rm", "-f", name, check=False)


def _seed(volume: str, subpath: str, filename: str, content: str) -> None:
    _docker(
        "run", "--rm",
        "--mount", f"type=volume,source={volume},target=/seed",
        _TEST_IMAGE, "sh", "-c",
        f"mkdir -p /seed/{subpath} && echo '{content}' > /seed/{subpath}/{filename}",
    )


async def _run_adapter(request: AgentSessionRequest) -> tuple[str, int]:
    adapter = CustomShellAdapter()
    await adapter.start(request)
    lines = []
    async for event in adapter.events():
        lines.append(event.message)
    result = await adapter.result()
    return "\n".join(lines), result.exit_code


async def test_ensure_network_is_idempotent_and_creates_a_real_network():
    name = f"talos-test-net-{uuid.uuid4().hex[:8]}"
    inspect_before = _docker("network", "inspect", name, check=False)
    assert inspect_before.returncode != 0

    await ensure_network(name)
    await ensure_network(name)  # second call must not raise even though it already exists

    inspect_after = _docker("network", "inspect", name, check=False)
    assert inspect_after.returncode == 0
    _docker("network", "rm", name, check=False)


async def test_containerized_run_can_read_its_own_workspace_subpath(workspaces_volume, provider_homes_volume):
    run_id = f"run-{uuid.uuid4().hex[:8]}"
    _seed(workspaces_volume, f"demo/runs/{run_id}/worktree", "file.txt", "hello-from-this-run")
    config = ContainerConfig(
        image=_TEST_IMAGE,
        workspace_volume=workspaces_volume,
        workspace_subpath=f"demo/runs/{run_id}/worktree",
        provider_homes_volume=provider_homes_volume,
        provider_home_subpath="custom-shell",
        network="bridge",
    )
    request = AgentSessionRequest(
        run_id=run_id,
        workspace_path="/workspace",
        prompt="cat file.txt",
        env={},
        auth_mode="api_key",
        provider_home=f"/tmp/talos-test-provider-home-{run_id}",
        timeout_seconds=30,
        container=config,
    )
    output, exit_code = await _run_adapter(request)
    assert exit_code == 0
    assert "hello-from-this-run" in output


async def test_containerized_run_cannot_see_a_sibling_runs_planted_env_file(workspaces_volume, provider_homes_volume):
    run_id = f"run-{uuid.uuid4().hex[:8]}"
    other_run_id = f"run-{uuid.uuid4().hex[:8]}"
    _seed(workspaces_volume, f"demo/runs/{run_id}/worktree", "file.txt", "mine")
    _seed(workspaces_volume, f"demo/runs/{other_run_id}/worktree", ".env", "SECRET=leaked")
    config = ContainerConfig(
        image=_TEST_IMAGE,
        workspace_volume=workspaces_volume,
        workspace_subpath=f"demo/runs/{run_id}/worktree",
        provider_homes_volume=provider_homes_volume,
        provider_home_subpath="custom-shell",
        network="bridge",
    )
    request = AgentSessionRequest(
        run_id=run_id,
        workspace_path="/workspace",
        # The sibling run's directory simply doesn't exist in this container's filesystem at all --
        # not a permissions test, an existence test. (A recursive `grep -r /` here would also hang
        # on /proc's virtual files in some environments; this is the more precise check anyway.)
        prompt="ls /demo 2>&1; find / -xdev -name .env 2>/dev/null; true",
        env={},
        auth_mode="api_key",
        provider_home=f"/tmp/talos-test-provider-home-{run_id}",
        timeout_seconds=30,
        container=config,
    )
    output, exit_code = await _run_adapter(request)
    assert exit_code == 0
    assert "SECRET=leaked" not in output
    assert ".env" not in output


async def test_containerized_run_has_no_docker_socket_or_cli(workspaces_volume, provider_homes_volume):
    run_id = f"run-{uuid.uuid4().hex[:8]}"
    _seed(workspaces_volume, f"demo/runs/{run_id}/worktree", "file.txt", "x")
    config = ContainerConfig(
        image=_TEST_IMAGE,
        workspace_volume=workspaces_volume,
        workspace_subpath=f"demo/runs/{run_id}/worktree",
        provider_homes_volume=provider_homes_volume,
        provider_home_subpath="custom-shell",
        network="bridge",
    )
    request = AgentSessionRequest(
        run_id=run_id,
        workspace_path="/workspace",
        prompt="test -S /var/run/docker.sock && echo SOCKET_PRESENT; which docker || echo NO_DOCKER_CLI",
        env={},
        auth_mode="api_key",
        provider_home=f"/tmp/talos-test-provider-home-{run_id}",
        timeout_seconds=30,
        container=config,
    )
    output, exit_code = await _run_adapter(request)
    assert exit_code == 0
    assert "SOCKET_PRESENT" not in output
    assert "NO_DOCKER_CLI" in output


async def test_containerized_run_masks_secrets_in_emitted_events(workspaces_volume, provider_homes_volume):
    run_id = f"run-{uuid.uuid4().hex[:8]}"
    _seed(workspaces_volume, f"demo/runs/{run_id}/worktree", "file.txt", "x")
    config = ContainerConfig(
        image=_TEST_IMAGE,
        workspace_volume=workspaces_volume,
        workspace_subpath=f"demo/runs/{run_id}/worktree",
        provider_homes_volume=provider_homes_volume,
        provider_home_subpath="custom-shell",
        network="bridge",
    )
    request = AgentSessionRequest(
        run_id=run_id,
        workspace_path="/workspace",
        prompt="echo $MY_SECRET",
        env={"MY_SECRET": "sk-super-secret-value"},
        auth_mode="api_key",
        provider_home=f"/tmp/talos-test-provider-home-{run_id}",
        timeout_seconds=30,
        container=config,
    )
    output, exit_code = await _run_adapter(request)
    assert exit_code == 0
    assert "sk-super-secret-value" not in output
    assert "***" in output


async def test_containerized_run_is_hard_killed_at_timeout(workspaces_volume, provider_homes_volume):
    """Phase 11 acceptance: "run hard-killed at timeout" -- must hold once execution moved into a
    container, not just for the pre-Phase-11 local-subprocess case."""
    run_id = f"run-{uuid.uuid4().hex[:8]}"
    _seed(workspaces_volume, f"demo/runs/{run_id}/worktree", "file.txt", "x")
    config = ContainerConfig(
        image=_TEST_IMAGE,
        workspace_volume=workspaces_volume,
        workspace_subpath=f"demo/runs/{run_id}/worktree",
        provider_homes_volume=provider_homes_volume,
        provider_home_subpath="custom-shell",
        network="bridge",
    )
    request = AgentSessionRequest(
        run_id=run_id,
        workspace_path="/workspace",
        prompt="sleep 300",
        env={},
        auth_mode="api_key",
        provider_home=f"/tmp/talos-test-provider-home-{run_id}",
        timeout_seconds=2,
        container=config,
    )
    adapter = CustomShellAdapter()
    await adapter.start(request)
    async for _event in adapter.events():
        pass
    result = await adapter.result()

    assert result.success is False
    assert "Timed out" in (result.summary or "")
    inspect = _docker("inspect", "talos-run-" + run_id, check=False)
    assert inspect.returncode != 0, "container should have been removed (--rm) after being killed"
