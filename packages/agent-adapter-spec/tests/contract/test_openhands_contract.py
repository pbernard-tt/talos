# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: Apache-2.0

"""Section 7.2 contract suite against OpenHandsAdapter using a mocked agent-server
(httpx.MockTransport). Point 3 ("stop() leaves no child processes") for this non-subprocess
adapter means: no local process is ever spawned and the remote session is cancelled."""

from __future__ import annotations

import json
from pathlib import Path

import httpx

from talos_agent_adapter_spec import AgentEventType, AgentSessionRequest, OpenHandsAdapter

FIXTURE = Path(__file__).parent.parent / "fixtures" / "openhands_events.json"


class FakeAgentServer:
    """Stateful mock of the OpenHands agent-server REST surface used by the adapter."""

    def __init__(self, events=None, running_polls=0, final_status="finished", reject_auth=False):
        self.events = events if events is not None else json.loads(FIXTURE.read_text())
        self.running_polls = running_polls  # "running" responses before turning terminal
        self.final_status = final_status
        self.reject_auth = reject_auth
        self.deleted = False
        self.created_body = None
        self._status_polls = 0

    def transport(self) -> httpx.MockTransport:
        return httpx.MockTransport(self._handle)

    def _handle(self, request: httpx.Request) -> httpx.Response:
        path, method = request.url.path, request.method
        if self.reject_auth:
            return httpx.Response(401, json={"detail": "invalid session api key"})
        if path == "/conversations/search":
            return httpx.Response(200, json={"items": [], "next_page_id": None})
        if path == "/conversations" and method == "POST":
            self.created_body = json.loads(request.content)
            return httpx.Response(200, json={"id": "conv-1", "execution_status": "running"})
        if path == "/conversations/conv-1" and method == "GET":
            self._status_polls += 1
            status = "running" if self._status_polls <= self.running_polls else self.final_status
            return httpx.Response(200, json={"id": "conv-1", "execution_status": status})
        if path == "/conversations/conv-1/events":
            return httpx.Response(200, json={"items": self.events, "next_page_id": None})
        if path == "/conversations/conv-1" and method == "DELETE":
            self.deleted = True
            return httpx.Response(200, json={})
        return httpx.Response(404, json={})


def _provider_home(tmp_path, config=None):
    provider_home = tmp_path / "provider"
    provider_home.mkdir(exist_ok=True)
    if config is None:
        config = {"base_url": "http://openhands.test", "session_api_key": "session-key"}
    (provider_home / "server.json").write_text(json.dumps(config))
    return provider_home


def _request(tmp_path, provider_home, timeout=30, env=None):
    workspace = tmp_path / "workspace"
    workspace.mkdir(exist_ok=True)
    return AgentSessionRequest(
        "test-run", str(workspace), "do the task", env or {}, "api_key", str(provider_home), timeout
    )


async def _events(adapter):
    return [event async for event in adapter.events()]


async def test_start_then_events_yields_at_least_one_event(tmp_path):
    server = FakeAgentServer()
    adapter = OpenHandsAdapter(transport=server.transport())
    await adapter.start(_request(tmp_path, _provider_home(tmp_path)))
    events = await _events(adapter)
    assert events and any(event.type == AgentEventType.TOOL_USE for event in events)
    assert (await adapter.result()).success is True
    assert server.created_body["workspace"]["working_dir"].endswith("workspace")
    assert server.created_body["initial_message"]["content"][0]["text"] == "do the task"


async def test_timeout_terminates_and_reports_failure(tmp_path):
    server = FakeAgentServer(running_polls=10_000)  # never turns terminal on its own
    adapter = OpenHandsAdapter(transport=server.transport())
    await adapter.start(_request(tmp_path, _provider_home(tmp_path), timeout=1))
    events = await _events(adapter)
    assert (await adapter.result()).success is False
    assert any(event.type == AgentEventType.STATUS and event.message == "timeout" for event in events)
    assert server.deleted is True  # timed-out remote session was cancelled


async def test_stop_cancels_the_remote_session_and_spawns_no_processes(tmp_path):
    server = FakeAgentServer(running_polls=10_000)
    adapter = OpenHandsAdapter(transport=server.transport())
    await adapter.start(_request(tmp_path, _provider_home(tmp_path), timeout=120))
    await adapter.stop()
    assert server.deleted is True
    assert not hasattr(adapter, "_process")  # HTTP adapter: no local process, ever
    assert (await adapter.result()).success is False
    await _events(adapter)  # iterator terminates cleanly after stop


async def test_result_exit_code_reflects_conversation_outcome(tmp_path):
    server = FakeAgentServer(final_status="error")
    adapter = OpenHandsAdapter(transport=server.transport())
    await adapter.start(_request(tmp_path, _provider_home(tmp_path)))
    await _events(adapter)
    result = await adapter.result()
    assert result.exit_code == 1 and result.success is False

    server_ok = FakeAgentServer()
    adapter_ok = OpenHandsAdapter(transport=server_ok.transport())
    await adapter_ok.start(_request(tmp_path, _provider_home(tmp_path)))
    await _events(adapter_ok)
    assert (await adapter_ok.result()).exit_code == 0


async def test_writes_nothing_outside_workspace_and_provider_home(tmp_path):
    provider_home = _provider_home(tmp_path)
    server = FakeAgentServer()
    adapter = OpenHandsAdapter(transport=server.transport())
    await adapter.start(_request(tmp_path, provider_home))
    await _events(adapter); await adapter.result()
    assert (provider_home / "runs" / "test-run" / "transcript.jsonl").is_file()


async def test_env_values_never_appear_in_emitted_events(tmp_path):
    secret = "supersecretvalue12345"
    events = [{"id": "evt_1", "kind": "MessageEvent", "llm_message": {"role": "assistant",
               "content": [{"type": "text", "text": f"the key is {secret}"}]}}]
    server = FakeAgentServer(events=events)
    adapter = OpenHandsAdapter(transport=server.transport())
    await adapter.start(_request(tmp_path, _provider_home(tmp_path), env={"LLM_API_KEY": secret}))
    emitted = await _events(adapter)
    assert emitted and all(secret not in event.message and secret not in str(event.metadata) for event in emitted)
