"""Recorded stream-json parser tests; no Claude credential or live CLI is needed in CI."""

from __future__ import annotations

import json

from talos_agent_adapter_spec import AgentEventType, AgentSessionRequest, ClaudeCodeAdapter


async def test_stream_json_parser_emits_tool_use_and_masked_logs(tmp_path):
    secret = "api-key-that-must-not-leak"
    adapter = ClaudeCodeAdapter()
    adapter._request = AgentSessionRequest(
        run_id="run", workspace_path=str(tmp_path), prompt="prompt", env={"ANTHROPIC_API_KEY": secret},
        auth_mode="api_key", provider_home=str(tmp_path / "provider"), timeout_seconds=30,
    )
    await adapter._emit_stream_json(json.dumps({
        "type": "assistant",
        "message": {"content": [
            {"type": "tool_use", "name": "Bash", "input": {"command": f"echo {secret}"}},
            {"type": "text", "text": f"Used {secret}"},
        ]},
    }))
    events = [adapter._queue.get_nowait(), adapter._queue.get_nowait()]

    assert [event.type for event in events] == [AgentEventType.TOOL_USE, AgentEventType.LOG]
    assert all(secret not in event.message for event in events)
    assert events[0].message == "Bash: echo ***"


async def test_stream_json_parser_records_result_summary(tmp_path):
    adapter = ClaudeCodeAdapter()
    adapter._request = AgentSessionRequest(
        run_id="run", workspace_path=str(tmp_path), prompt="prompt", env={}, auth_mode="api_key",
        provider_home=str(tmp_path / "provider"), timeout_seconds=30,
    )
    await adapter._emit_stream_json(json.dumps({"type": "result", "subtype": "success", "result": "done"}))

    assert adapter._summary == "done"
    assert adapter._queue.get_nowait().type == AgentEventType.LOG


async def test_result_event_normalizes_usage_and_cost_onto_agent_result(tmp_path):
    adapter = ClaudeCodeAdapter()
    adapter._request = AgentSessionRequest(
        run_id="run", workspace_path=str(tmp_path), prompt="prompt", env={}, auth_mode="api_key",
        provider_home=str(tmp_path / "provider"), timeout_seconds=30,
    )
    await adapter._emit_stream_json(json.dumps({
        "type": "system", "subtype": "init", "model": "claude-sonnet-5",
    }))
    adapter._queue.get_nowait()  # the init event's own LOG line
    await adapter._emit_stream_json(json.dumps({
        "type": "result", "subtype": "success", "result": "done",
        "total_cost_usd": 0.0421, "usage": {"input_tokens": 1200, "output_tokens": 340},
    }))
    adapter._queue.get_nowait()  # the result event's own LOG line

    adapter._exit_code = 0
    adapter._done.set()
    result = await adapter.result()

    assert result.input_tokens == 1200
    assert result.output_tokens == 340
    assert result.total_cost_usd == 0.0421
    assert result.model == "claude-sonnet-5"


async def test_result_event_with_no_usage_degrades_gracefully(tmp_path):
    adapter = ClaudeCodeAdapter()
    adapter._request = AgentSessionRequest(
        run_id="run", workspace_path=str(tmp_path), prompt="prompt", env={}, auth_mode="api_key",
        provider_home=str(tmp_path / "provider"), timeout_seconds=30,
    )
    await adapter._emit_stream_json(json.dumps({"type": "result", "subtype": "success", "result": "done"}))
    adapter._queue.get_nowait()

    adapter._exit_code = 0
    adapter._done.set()
    result = await adapter.result()

    assert result.input_tokens is None
    assert result.output_tokens is None
    assert result.total_cost_usd is None
    assert result.model is None
