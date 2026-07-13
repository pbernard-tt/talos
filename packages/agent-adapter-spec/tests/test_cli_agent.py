# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: Apache-2.0

"""Pre-launch capability check (Phase 12 Track A): CLI present, version compatible, credentials
present -- a failed check fails the run with a SYSTEM log line, never a mid-run crash."""

from __future__ import annotations

import os

from talos_agent_adapter_spec import (
    AgentEventType,
    AgentSessionRequest,
    CodexCliAdapter,
    ContainerConfig,
    OpenCodeAdapter,
)
from talos_agent_adapter_spec.cli_agent import parse_version


def _fake_cli(bin_dir, name, version):
    command = bin_dir / name
    command.write_text(
        "#!/bin/sh\n"
        f"if [ \"$1\" = \"--version\" ]; then echo '{version}'; exit 0; fi\n"
        "printf '%s\\n' '{\"type\":\"item.completed\",\"item\":{\"id\":\"i\",\"type\":\"agent_message\",\"text\":\"ok\"}}'\n"
    )
    command.chmod(0o755)


def _paths(tmp_path):
    workspace, provider_home, bin_dir = tmp_path / "workspace", tmp_path / "provider", tmp_path / "bin"
    workspace.mkdir(); provider_home.mkdir(); bin_dir.mkdir()
    return workspace, provider_home, bin_dir


def _request(workspace, provider_home, bin_dir, auth_mode="api_key", env=None, container=None):
    request_env = {"PATH": f"{bin_dir}:{os.environ['PATH']}"}
    request_env.update(env or {})
    return AgentSessionRequest(
        "test-run", str(workspace), "prompt", request_env, auth_mode, str(provider_home), 30, container=container
    )


def _plant_codex_auth(provider_home):
    auth = provider_home / ".codex" / "auth.json"
    auth.parent.mkdir(parents=True, exist_ok=True)
    auth.write_text("{}")


async def _failure(adapter, request):
    """Run start() and return (events, result) -- asserting no agent process was ever spawned."""
    await adapter.start(request)
    events = [event async for event in adapter.events()]
    result = await adapter.result()
    assert adapter._process is None
    return events, result


async def test_missing_cli_fails_run_with_system_log_line(tmp_path):
    workspace, provider_home, bin_dir = _paths(tmp_path)
    _plant_codex_auth(provider_home)  # credentials fine -- only the CLI is missing
    adapter = CodexCliAdapter()
    # PATH restricted to the (empty) fake bin dir: a codex really installed on the dev machine
    # must not satisfy the check.
    request = _request(workspace, provider_home, bin_dir)
    request.env["PATH"] = str(bin_dir)
    events, result = await _failure(adapter, request)

    errors = [event for event in events if event.type == AgentEventType.ERROR]
    assert errors and "capability check failed for codex-cli" in errors[0].message
    assert "not found on PATH" in errors[0].message
    assert "stream" not in errors[0].metadata  # -> persisted as a SYSTEM log line
    assert result.success is False and result.exit_code == -1
    assert "capability check failed" in (result.summary or "")
    assert (provider_home / "runs" / "test-run" / "transcript.jsonl").is_file()


async def test_version_below_minimum_fails(tmp_path):
    workspace, provider_home, bin_dir = _paths(tmp_path)
    _plant_codex_auth(provider_home)
    _fake_cli(bin_dir, "codex", "codex-cli 0.9.0")
    adapter = CodexCliAdapter()
    events, result = await _failure(adapter, _request(workspace, provider_home, bin_dir))

    assert result.success is False
    assert "0.9.0 is older than the minimum supported 0.144" in (result.summary or "")


async def test_missing_credentials_fails_before_spawning(tmp_path):
    workspace, provider_home, bin_dir = _paths(tmp_path)
    _fake_cli(bin_dir, "opencode", "1.17.17")  # CLI fine -- only credentials are missing
    adapter = OpenCodeAdapter()
    events, result = await _failure(adapter, _request(workspace, provider_home, bin_dir))

    assert result.success is False
    assert "no OpenCode credentials" in (result.summary or "")


async def test_opencode_rejects_subscription_auth_mode(tmp_path):
    workspace, provider_home, bin_dir = _paths(tmp_path)
    _fake_cli(bin_dir, "opencode", "1.17.17")
    adapter = OpenCodeAdapter()
    events, result = await _failure(adapter, _request(workspace, provider_home, bin_dir, auth_mode="subscription_local"))

    assert result.success is False
    assert "api_key only" in (result.summary or "")


async def test_codex_api_key_env_satisfies_credential_check(tmp_path):
    workspace, provider_home, bin_dir = _paths(tmp_path)
    _fake_cli(bin_dir, "codex", "codex-cli 0.144.0")
    adapter = CodexCliAdapter()
    await adapter.start(_request(workspace, provider_home, bin_dir, env={"OPENAI_API_KEY": "sk-test"}))
    events = [event async for event in adapter.events()]

    assert (await adapter.result()).success is True
    assert adapter._summary == "ok"


async def test_codex_subscription_requires_auth_json(tmp_path):
    workspace, provider_home, bin_dir = _paths(tmp_path)
    _fake_cli(bin_dir, "codex", "codex-cli 0.144.0")
    adapter = CodexCliAdapter()
    events, result = await _failure(
        adapter, _request(workspace, provider_home, bin_dir, auth_mode="subscription_local")
    )

    assert result.success is False
    assert "codex login" in (result.summary or "")


async def test_container_mode_probes_cli_via_docker_and_runs(tmp_path, monkeypatch):
    """Container preflight probes `docker run --rm --network none <image> <cli> --version`; a fake
    docker answers the probe and then stands in for the real containerized run."""
    workspace, provider_home, bin_dir = _paths(tmp_path)
    _plant_codex_auth(provider_home)
    fake_docker = bin_dir / "docker"
    fake_docker.write_text(
        "#!/bin/sh\n"
        "for arg in \"$@\"; do\n"
        "  if [ \"$arg\" = \"--version\" ]; then echo 'codex-cli 0.144.0'; exit 0; fi\n"
        "done\n"
        "printf '%s\\n' '{\"type\":\"item.completed\",\"item\":{\"id\":\"i\",\"type\":\"agent_message\",\"text\":\"ok-from-container\"}}'\n"
    )
    fake_docker.chmod(0o755)
    monkeypatch.setenv("PATH", f"{bin_dir}:{os.environ['PATH']}")

    container = ContainerConfig(
        image="workers/base-agent-runner", workspace_volume="ws", workspace_subpath="sub",
        provider_homes_volume="ph", provider_home_subpath="codex-cli", network="talos_run_network",
    )
    adapter = CodexCliAdapter()
    await adapter.start(_request(workspace, provider_home, bin_dir, container=container))
    events = [event async for event in adapter.events()]

    assert (await adapter.result()).success is True
    assert adapter._summary == "ok-from-container"


async def test_container_mode_missing_cli_in_image_fails(tmp_path, monkeypatch):
    workspace, provider_home, bin_dir = _paths(tmp_path)
    _plant_codex_auth(provider_home)
    fake_docker = bin_dir / "docker"
    fake_docker.write_text("#!/bin/sh\nexit 127\n")  # e.g. image lacks the CLI entirely
    fake_docker.chmod(0o755)
    monkeypatch.setenv("PATH", f"{bin_dir}:{os.environ['PATH']}")

    container = ContainerConfig(
        image="workers/base-agent-runner", workspace_volume="ws", workspace_subpath="sub",
        provider_homes_volume="ph", provider_home_subpath="codex-cli", network="talos_run_network",
    )
    adapter = CodexCliAdapter()
    events, result = await _failure(adapter, _request(workspace, provider_home, bin_dir, container=container))

    assert result.success is False
    assert "CLI missing from image" in (result.summary or "")


def test_parse_version():
    assert parse_version("codex-cli 0.144.0") == (0, 144, 0)
    assert parse_version("1.17.17") == (1, 17, 17)
    assert parse_version("no digits here") is None
