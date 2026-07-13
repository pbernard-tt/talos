# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: Apache-2.0

"""Recorded-stream parser tests (fixture drift; see tests/fixtures/README.md) plus the
provider-home permission-config merge; no OpenCode credential or live CLI is needed in CI."""

from __future__ import annotations

import json
from pathlib import Path

from talos_agent_adapter_spec import AgentEventType, AgentSessionRequest, OpenCodeAdapter

FIXTURE = Path(__file__).parent / "fixtures" / "opencode_run_stream.jsonl"


def _adapter(tmp_path, env=None):
    adapter = OpenCodeAdapter()
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


async def test_recorded_stream_maps_to_adapter_events(tmp_path):
    adapter = _adapter(tmp_path)
    for line in FIXTURE.read_text().splitlines():
        await adapter._handle_stdout_line(line)
    events = _drain(adapter)

    tool_events = [event for event in events if event.type == AgentEventType.TOOL_USE]
    assert tool_events and tool_events[0].message == "bash: echo talos-fixture-probe"
    assert tool_events[0].metadata == {"tool": "bash"}
    assert any(event.type == AgentEventType.LOG and event.message == "done" for event in events)
    assert adapter._summary == "done"
    step_events = [event for event in events if event.metadata.get("eventType") == "step_start"]
    assert step_events and step_events[0].type == AgentEventType.LOG


async def test_error_event_maps_to_error_and_summary(tmp_path):
    adapter = _adapter(tmp_path)
    await adapter._handle_stdout_line(json.dumps(
        {"type": "error", "timestamp": 1, "sessionID": "s", "error": "boom"}
    ))
    events = _drain(adapter)

    assert [event.type for event in events] == [AgentEventType.ERROR]
    assert events[0].message == "boom" and adapter._summary == "boom"


async def test_parser_masks_env_values(tmp_path):
    secret = "api-key-that-must-not-leak"
    adapter = _adapter(tmp_path, env={"OPENROUTER_API_KEY": secret})
    await adapter._handle_stdout_line(json.dumps({
        "type": "tool_use", "timestamp": 1, "sessionID": "s",
        "part": {"type": "tool", "tool": "bash", "state": {"status": "completed", "input": {"command": f"echo {secret}"}}},
    }))
    events = _drain(adapter)

    assert events and events[0].message == "bash: echo ***"


def test_prepare_provider_home_merges_deny_rules_without_discarding_config(tmp_path):
    provider_home = tmp_path / "provider"
    config_path = provider_home / ".config" / "opencode" / "opencode.json"
    config_path.parent.mkdir(parents=True)
    config_path.write_text(json.dumps({
        "model": "anthropic/claude-sonnet-5",
        "permission": {"bash": {"npm *": "ask"}},
    }))
    adapter = OpenCodeAdapter()
    adapter._prepare_provider_home(AgentSessionRequest(
        run_id="run", workspace_path=str(tmp_path), prompt="p", env={},
        auth_mode="api_key", provider_home=str(provider_home), timeout_seconds=30,
    ))
    config = json.loads(config_path.read_text())

    assert config["model"] == "anthropic/claude-sonnet-5"  # operator config preserved
    assert config["permission"]["bash"]["npm *"] == "ask"
    assert config["permission"]["bash"]["git push *"] == "deny"
    assert config["permission"]["bash"]["git commit *"] == "deny"
    assert config["permission"]["bash"]["*"] == "allow"
    assert config["permission"]["edit"] == "allow"
