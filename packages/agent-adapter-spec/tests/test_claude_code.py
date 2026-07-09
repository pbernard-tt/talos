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
