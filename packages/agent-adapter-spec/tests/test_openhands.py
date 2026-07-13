# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: Apache-2.0

"""OpenHands event-mapping (fixture drift; see tests/fixtures/README.md) and capability-check
tests; no live agent-server is needed in CI."""

from __future__ import annotations

import json
from pathlib import Path

import httpx
import pytest

from talos_agent_adapter_spec import AgentEventType, AgentSessionRequest, OpenHandsAdapter

FIXTURE = Path(__file__).parent / "fixtures" / "openhands_events.json"


def _adapter(tmp_path, env=None):
    adapter = OpenHandsAdapter()
    adapter._request = AgentSessionRequest(
        run_id="run", workspace_path=str(tmp_path), prompt="prompt", env=env or {},
        auth_mode="api_key", provider_home=str(tmp_path / "provider"), timeout_seconds=30,
    )
    return adapter


def _drain(adapter):
    events = []
    while not adapter._queue.empty():
        events.append(adapter._queue.get_nowait())
    return events


async def test_fixture_events_map_to_adapter_events(tmp_path):
    adapter = _adapter(tmp_path)
    for event in json.loads(FIXTURE.read_text()):
        await adapter._emit_remote_event(event)
    events = _drain(adapter)

    tool_events = [event for event in events if event.type == AgentEventType.TOOL_USE]
    assert tool_events and tool_events[0].message == 'execute_bash: {"command": "echo talos-fixture-probe"}'
    assert tool_events[0].metadata["tool"] == "execute_bash"
    assert any(event.type == AgentEventType.LOG and "talos-fixture-probe" in event.message for event in events)
    assert adapter._summary == "done"


async def test_agent_error_event_maps_to_error(tmp_path):
    adapter = _adapter(tmp_path)
    await adapter._emit_remote_event({"id": "evt_9", "kind": "AgentErrorEvent", "error": "boom"})
    events = _drain(adapter)

    assert [event.type for event in events] == [AgentEventType.ERROR]
    assert events[0].message == "boom" and adapter._summary == "boom"


async def test_missing_server_config_fails_with_system_log_line(tmp_path):
    workspace = tmp_path / "workspace"; workspace.mkdir()
    provider_home = tmp_path / "provider"; provider_home.mkdir()  # no server.json
    adapter = OpenHandsAdapter()
    await adapter.start(AgentSessionRequest(
        "test-run", str(workspace), "prompt", {}, "api_key", str(provider_home), 30
    ))
    events = [event async for event in adapter.events()]
    result = await adapter.result()

    errors = [event for event in events if event.type == AgentEventType.ERROR]
    assert errors and "capability check failed for openhands" in errors[0].message
    assert "no OpenHands server config" in errors[0].message
    assert "stream" not in errors[0].metadata  # -> persisted as a SYSTEM log line
    assert result.success is False


async def test_unreachable_server_fails_capability_check(tmp_path):
    workspace = tmp_path / "workspace"; workspace.mkdir()
    provider_home = tmp_path / "provider"; provider_home.mkdir()
    (provider_home / "server.json").write_text(json.dumps({"base_url": "http://openhands.test"}))

    def refuse(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("connection refused")

    adapter = OpenHandsAdapter(transport=httpx.MockTransport(refuse))
    await adapter.start(AgentSessionRequest(
        "test-run", str(workspace), "prompt", {}, "api_key", str(provider_home), 30
    ))
    events = [event async for event in adapter.events()]
    result = await adapter.result()

    assert result.success is False
    assert "unreachable" in (result.summary or "")


async def test_rejected_session_api_key_fails_capability_check(tmp_path):
    workspace = tmp_path / "workspace"; workspace.mkdir()
    provider_home = tmp_path / "provider"; provider_home.mkdir()
    (provider_home / "server.json").write_text(json.dumps(
        {"base_url": "http://openhands.test", "session_api_key": "wrong"}
    ))
    adapter = OpenHandsAdapter(transport=httpx.MockTransport(
        lambda request: httpx.Response(401, json={"detail": "nope"})
    ))
    await adapter.start(AgentSessionRequest(
        "test-run", str(workspace), "prompt", {}, "api_key", str(provider_home), 30
    ))
    [event async for event in adapter.events()]
    result = await adapter.result()

    assert result.success is False
    assert "rejected the session_api_key" in (result.summary or "")


async def test_subscription_auth_mode_is_rejected(tmp_path):
    adapter = OpenHandsAdapter()
    with pytest.raises(ValueError, match="api_key"):
        await adapter.start(AgentSessionRequest(
            "test-run", str(tmp_path), "prompt", {}, "subscription_local", str(tmp_path / "p"), 30
        ))
