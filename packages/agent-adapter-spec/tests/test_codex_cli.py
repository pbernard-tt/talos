"""Recorded-JSONL parser tests (fixture drift; see tests/fixtures/README.md); no Codex
credential or live CLI is needed in CI."""

from __future__ import annotations

import json
from pathlib import Path

from talos_agent_adapter_spec import AgentEventType, AgentSessionRequest, CodexCliAdapter

FIXTURE = Path(__file__).parent / "fixtures" / "codex_exec_stream.jsonl"


def _adapter(tmp_path, env=None):
    adapter = CodexCliAdapter()
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
    assert tool_events and tool_events[0].message == "command_execution: /bin/bash -lc 'echo talos-fixture-probe'"
    assert any(event.type == AgentEventType.LOG and event.message == "talos-fixture-probe" for event in events)
    assert adapter._summary == "done"


async def test_recorded_usage_surfaces_as_system_line_with_metadata(tmp_path):
    adapter = _adapter(tmp_path)
    for line in FIXTURE.read_text().splitlines():
        await adapter._handle_stdout_line(line)
    usage_events = [event for event in _drain(adapter) if event.metadata.get("eventType") == "turn.completed"]

    assert usage_events and usage_events[0].metadata["usage"]["input_tokens"] == 27279
    # No "stream" key -> the orchestrator persists this as a SYSTEM log line.
    assert "stream" not in usage_events[0].metadata


async def test_recorded_usage_normalizes_onto_agent_result(tmp_path):
    adapter = _adapter(tmp_path)
    for line in FIXTURE.read_text().splitlines():
        await adapter._handle_stdout_line(line)
    _drain(adapter)

    adapter._exit_code = 0
    adapter._done.set()
    result = await adapter.result()

    assert result.input_tokens == 27279
    assert result.output_tokens == 177
    # Codex's usage never carries a dollar amount -- never fabricate one.
    assert result.total_cost_usd is None


async def test_turn_failed_maps_to_error_event_and_summary(tmp_path):
    adapter = _adapter(tmp_path)
    await adapter._handle_stdout_line(json.dumps({"type": "turn.failed", "error": {"message": "boom"}}))
    events = _drain(adapter)

    assert [event.type for event in events] == [AgentEventType.ERROR]
    assert events[0].message == "boom" and adapter._summary == "boom"


async def test_file_change_item_maps_to_tool_use(tmp_path):
    adapter = _adapter(tmp_path)
    await adapter._handle_stdout_line(json.dumps({
        "type": "item.completed",
        "item": {"id": "item_2", "type": "file_change", "status": "completed",
                 "changes": [{"path": "src/app.py", "kind": "update"}]},
    }))
    events = _drain(adapter)

    assert [event.type for event in events] == [AgentEventType.TOOL_USE]
    assert events[0].message == "file_change: src/app.py"


def test_sandbox_flag_depends_on_execution_mode(tmp_path):
    from talos_agent_adapter_spec import ContainerConfig

    adapter = CodexCliAdapter()
    local = AgentSessionRequest("r", str(tmp_path), "p", {}, "api_key", str(tmp_path / "h"), 30)
    assert "-s" in adapter._cli_args(local, str(tmp_path / "h"))
    assert "--dangerously-bypass-approvals-and-sandbox" not in adapter._cli_args(local, str(tmp_path / "h"))

    containerized = AgentSessionRequest(
        "r", str(tmp_path), "p", {}, "api_key", str(tmp_path / "h"), 30,
        container=ContainerConfig("img", "ws", "sub", "ph", "codex-cli", "net"),
    )
    args = adapter._cli_args(containerized, "/provider-home")
    assert "--dangerously-bypass-approvals-and-sandbox" in args and "-s" not in args


async def test_parser_masks_env_values(tmp_path):
    secret = "api-key-that-must-not-leak"
    adapter = _adapter(tmp_path, env={"OPENAI_API_KEY": secret})
    await adapter._handle_stdout_line(json.dumps({
        "type": "item.completed",
        "item": {"id": "item_0", "type": "command_execution", "command": f"echo {secret}",
                 "aggregated_output": f"{secret}\n", "exit_code": 0, "status": "completed"},
    }))
    events = _drain(adapter)

    assert events and all(secret not in event.message for event in events)
