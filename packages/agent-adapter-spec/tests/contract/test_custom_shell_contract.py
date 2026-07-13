# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: Apache-2.0

"""Section 7.2 shared adapter contract suite, run against CustomShellAdapter (the only adapter
implemented in Phase 6). When ClaudeCodeAdapter lands in Phase 7, lift these test bodies into a
suite parametrized over both adapters instead of duplicating them -- premature to generalize a
contract suite that only has one real implementation to check today.
"""

from __future__ import annotations

import asyncio
import os

import pytest

from talos_agent_adapter_spec import AgentEventType, AgentSessionRequest, CustomShellAdapter


def make_request(workspace_path, provider_home, prompt, timeout_seconds=30, env=None, run_id="test-run"):
    return AgentSessionRequest(
        run_id=run_id,
        workspace_path=str(workspace_path),
        prompt=prompt,
        env=env or {},
        auth_mode="api_key",
        provider_home=str(provider_home),
        timeout_seconds=timeout_seconds,
    )


async def collect_events(adapter):
    return [event async for event in adapter.events()]


# 1. start() followed by events() yields at least one event.
async def test_start_then_events_yields_at_least_one_event(tmp_path):
    workspace = tmp_path / "workspace"
    workspace.mkdir()
    provider_home = tmp_path / "provider-home"
    provider_home.mkdir()

    adapter = CustomShellAdapter()
    await adapter.start(make_request(workspace, provider_home, "echo hello-from-adapter"))
    events = await collect_events(adapter)

    assert len(events) >= 1
    assert any("hello-from-adapter" in e.message for e in events)
    result = await adapter.result()
    assert result.success is True


# 2. The adapter terminates within timeout_seconds and reports success=False on timeout.
async def test_timeout_terminates_and_reports_failure(tmp_path):
    workspace = tmp_path / "workspace"
    workspace.mkdir()
    provider_home = tmp_path / "provider-home"
    provider_home.mkdir()

    adapter = CustomShellAdapter()
    await adapter.start(make_request(workspace, provider_home, "sleep 30", timeout_seconds=1))
    events = await collect_events(adapter)
    result = await adapter.result()

    assert result.success is False
    assert any(e.type == AgentEventType.STATUS and e.message == "timeout" for e in events)


# 3. stop() leaves no child processes (verified via process group inspection).
async def test_stop_leaves_no_child_processes(tmp_path):
    workspace = tmp_path / "workspace"
    workspace.mkdir()
    provider_home = tmp_path / "provider-home"
    provider_home.mkdir()

    adapter = CustomShellAdapter()
    await adapter.start(make_request(workspace, provider_home, "sleep 30 & sleep 30 & wait", timeout_seconds=120))
    pid = adapter._process.pid
    pgid = os.getpgid(pid)

    await adapter.stop()

    async def group_is_gone() -> bool:
        try:
            os.killpg(pgid, 0)
            return False
        except ProcessLookupError:
            return True

    for _ in range(20):
        if await group_is_gone():
            return
        await asyncio.sleep(0.1)
    pytest.fail(f"process group {pgid} still has live members after stop()")


# 4. result().exit_code matches the underlying process exit code.
async def test_result_exit_code_matches_process_exit_code(tmp_path):
    workspace = tmp_path / "workspace"
    workspace.mkdir()
    provider_home = tmp_path / "provider-home"
    provider_home.mkdir()

    adapter = CustomShellAdapter()
    await adapter.start(make_request(workspace, provider_home, "exit 7"))
    await collect_events(adapter)
    result = await adapter.result()

    assert result.exit_code == 7
    assert result.success is False


# 5. The adapter writes nothing outside workspace_path and provider_home.
async def test_writes_nothing_outside_workspace_and_provider_home(tmp_path):
    sandbox = tmp_path / "sandbox"
    sandbox.mkdir()
    workspace = sandbox / "workspace"
    workspace.mkdir()
    provider_home = sandbox / "provider-home"
    provider_home.mkdir()

    adapter = CustomShellAdapter()
    await adapter.start(make_request(workspace, provider_home, "echo hi > output.txt"))
    await collect_events(adapter)
    await adapter.result()

    for path in sandbox.rglob("*"):
        if path.is_file():
            assert str(path).startswith((str(workspace), str(provider_home)))


# 6. No value from request.env ever appears in emitted events (masking check).
async def test_env_values_never_appear_in_emitted_events(tmp_path):
    workspace = tmp_path / "workspace"
    workspace.mkdir()
    provider_home = tmp_path / "provider-home"
    provider_home.mkdir()
    secret = "supersecretvalue12345"

    adapter = CustomShellAdapter()
    await adapter.start(
        make_request(workspace, provider_home, "echo $TALOS_TEST_SECRET", env={"TALOS_TEST_SECRET": secret})
    )
    events = await collect_events(adapter)

    for event in events:
        assert secret not in event.message
        assert secret not in str(event.metadata)
