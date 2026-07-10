"""Section 7.2 contract suite against OpenCodeAdapter using a deterministic fake CLI."""

from __future__ import annotations

import asyncio
import os

import pytest

from talos_agent_adapter_spec import AgentEventType, AgentSessionRequest, OpenCodeAdapter


def _fake_opencode(bin_dir):
    command = bin_dir / "opencode"
    command.write_text(
        "#!/bin/sh\n"
        "if [ \"$1\" = \"--version\" ]; then echo 1.17.17; exit 0; fi\n"
        "case \"$2\" in\n"
        "  sleep) sleep 30 ;;\n"
        "  children) sleep 30 & sleep 30 & wait ;;\n"
        "  fail) printf '%s\\n' '{\"type\":\"error\",\"timestamp\":1,\"sessionID\":\"s\",\"error\":\"boom\"}'; exit 7 ;;\n"
        "  *) printf '%s\\n' '{\"type\":\"tool_use\",\"timestamp\":1,\"sessionID\":\"s\",\"part\":{\"type\":\"tool\",\"tool\":\"bash\",\"state\":{\"status\":\"completed\",\"input\":{\"command\":\"echo hello\"},\"title\":\"echo hello\"}}}'; printf '%s\\n' '{\"type\":\"text\",\"timestamp\":2,\"sessionID\":\"s\",\"part\":{\"type\":\"text\",\"text\":\"hello-from-opencode\"}}' ;;\n"
        "esac\n"
    )
    command.chmod(0o755)


def _plant_credentials(provider_home):
    auth = provider_home / ".local" / "share" / "opencode" / "auth.json"
    auth.parent.mkdir(parents=True, exist_ok=True)
    auth.write_text("{}")


def _request(workspace, provider_home, bin_dir, prompt="ok", timeout=30, env=None):
    request_env = {"PATH": f"{bin_dir}:{os.environ['PATH']}"}
    request_env.update(env or {})
    return AgentSessionRequest("test-run", str(workspace), prompt, request_env, "api_key", str(provider_home), timeout)


def _paths(tmp_path):
    workspace, provider_home, bin_dir = tmp_path / "workspace", tmp_path / "provider", tmp_path / "bin"
    workspace.mkdir(); provider_home.mkdir(); bin_dir.mkdir()
    _fake_opencode(bin_dir); _plant_credentials(provider_home)
    return workspace, provider_home, bin_dir


async def _events(adapter):
    return [event async for event in adapter.events()]


async def test_start_then_events_yields_at_least_one_event(tmp_path):
    workspace, provider_home, bin_dir = _paths(tmp_path)
    adapter = OpenCodeAdapter()
    await adapter.start(_request(workspace, provider_home, bin_dir))
    events = await _events(adapter)
    assert events and any(event.type == AgentEventType.TOOL_USE for event in events)
    assert (await adapter.result()).success is True


async def test_timeout_terminates_and_reports_failure(tmp_path):
    workspace, provider_home, bin_dir = _paths(tmp_path)
    adapter = OpenCodeAdapter()
    await adapter.start(_request(workspace, provider_home, bin_dir, prompt="sleep", timeout=1))
    events = await _events(adapter)
    assert (await adapter.result()).success is False
    assert any(event.type == AgentEventType.STATUS and event.message == "timeout" for event in events)


async def test_stop_leaves_no_child_processes(tmp_path):
    workspace, provider_home, bin_dir = _paths(tmp_path)
    adapter = OpenCodeAdapter()
    await adapter.start(_request(workspace, provider_home, bin_dir, prompt="children", timeout=120))
    pgid = os.getpgid(adapter._process.pid)
    await adapter.stop()
    for _ in range(20):
        try:
            os.killpg(pgid, 0)
        except ProcessLookupError:
            return
        await asyncio.sleep(0.1)
    pytest.fail(f"process group {pgid} still has live members after stop()")


async def test_result_exit_code_matches_process_exit_code(tmp_path):
    workspace, provider_home, bin_dir = _paths(tmp_path)
    adapter = OpenCodeAdapter()
    await adapter.start(_request(workspace, provider_home, bin_dir, prompt="fail"))
    await _events(adapter)
    result = await adapter.result()
    assert result.exit_code == 7 and result.success is False


async def test_writes_nothing_outside_workspace_and_provider_home(tmp_path):
    sandbox = tmp_path / "sandbox"; sandbox.mkdir()
    workspace, provider_home, bin_dir = sandbox / "workspace", sandbox / "provider", sandbox / "bin"
    workspace.mkdir(); provider_home.mkdir(); bin_dir.mkdir()
    _fake_opencode(bin_dir); _plant_credentials(provider_home)
    adapter = OpenCodeAdapter()
    await adapter.start(_request(workspace, provider_home, bin_dir))
    await _events(adapter); await adapter.result()
    assert (provider_home / ".config" / "opencode" / "opencode.json").is_file()
    assert (provider_home / "runs" / "test-run" / "transcript.jsonl").is_file()


async def test_env_values_never_appear_in_emitted_events(tmp_path):
    workspace, provider_home, bin_dir = _paths(tmp_path)
    secret = "supersecretvalue12345"
    adapter = OpenCodeAdapter()
    await adapter.start(_request(workspace, provider_home, bin_dir, env={"OPENROUTER_API_KEY": secret}))
    events = await _events(adapter)
    assert all(secret not in event.message and secret not in str(event.metadata) for event in events)
