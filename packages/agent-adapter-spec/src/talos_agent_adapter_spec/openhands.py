"""OpenHands adapter (Section 7.4 #5, implemented in Phase 12 Track A).

The first non-subprocess adapter: an HTTP client against a locally deployed OpenHands
agent-server. Verified 2026-07-10 against the OpenHands Software Agent SDK repository (v1.x SDK;
the agent-server publishes no version handshake, so the capability check probes reachability and
auth instead of a version number). The REST surface used here:

- ``POST /conversations`` -- body per ``StartConversationRequest``: ``workspace.working_dir``
  (the Talos worktree; Phase 12 runs in the standard workspace model), ``initial_message``
  (the assembled prompt), ``max_iterations``, ``confirmation_policy: NeverConfirm`` (headless),
  plus an operator-supplied ``agent`` payload (LLM model/BYOK key config) passed through verbatim.
- ``GET /conversations/{id}`` -- execution status: idle/running/paused/waiting_for_confirmation/
  finished/error/stuck.
- ``GET /conversations/{id}/events?page_id=`` -- paged, ``kind``-discriminated events, polled and
  proxied into the standard AgentEvent iterator.
- ``DELETE /conversations/{id}`` -- ``stop()`` cancels the remote session.
- Optional ``Authorization: Bearer <session_api_key>``.

Deployment contract (documented, not enforced here): the OpenHands instance must see the shared
workspaces volume at the same absolute paths as talos-runner-supervisor, and the connection is
configured in ``<provider_home>/server.json`` -- ``{"base_url": ..., "session_api_key": ...,``
``"agent": {...}}``. ``request.container`` is ignored: execution happens inside the OpenHands
deployment (it sandboxes itself), never as a runner-supervisor subprocess.

Exit codes are synthesized (no underlying process): 0 for a ``finished`` conversation, 1 for
``error``/``stuck``/cancelled/timeout.

The ``initial_message`` payload follows the SDK's Message model (role + TextContent list); the
agent-server publishes its exact ``StartConversationRequest`` schema only at runtime (``/docs``),
so re-verify both against the deployed instance before first production use.
"""

from __future__ import annotations

import asyncio
import contextlib
import json
from datetime import UTC, datetime
from pathlib import Path
from typing import Any, AsyncIterator

import httpx

from talos_agent_adapter_spec.adapter import (
    AgentAdapter,
    AgentEvent,
    AgentEventType,
    AgentResult,
    AgentSessionRequest,
    ProviderCapabilities,
)

_SENTINEL = object()
_TERMINAL_STATUSES = {"finished", "error", "stuck"}
_POLL_INTERVAL_SECONDS = 1.0
_SERVER_CONFIG_FILE = "server.json"


def _now_iso() -> str:
    return datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%S.%fZ")


def _mask(text: str, env: dict[str, str]) -> str:
    for value in env.values():
        if value:
            text = text.replace(value, "***")
    return text


def _extract_text(event: dict[str, Any]) -> str:
    """Human-readable text of a kind-discriminated event, tolerant of shape drift."""
    for candidate in (event.get("text"), event.get("message"), event.get("content")):
        if isinstance(candidate, str) and candidate:
            return candidate
    llm_message = event.get("llm_message")
    if isinstance(llm_message, dict):
        content = llm_message.get("content")
        if isinstance(content, str):
            return content
        if isinstance(content, list):
            return "".join(str(block.get("text", "")) for block in content if isinstance(block, dict))
    return ""


class OpenHandsAdapter(AgentAdapter):
    key = "openhands"

    def __init__(self, transport: httpx.AsyncBaseTransport | None = None) -> None:
        self._transport = transport  # tests inject httpx.MockTransport; None means real HTTP
        self._request: AgentSessionRequest | None = None
        self._client: httpx.AsyncClient | None = None
        self._conversation_id: str | None = None
        self._queue: asyncio.Queue = asyncio.Queue()
        self._runner_task: asyncio.Task | None = None
        self._done = asyncio.Event()
        self._stopped = False
        self._timed_out = False
        self._final_status: str | None = None
        self._summary: str | None = None
        self._transcript_path: Path | None = None
        self._transcript_lines: list[str] = []
        self._seen_event_keys: set[str] = set()

    def capabilities(self) -> ProviderCapabilities:
        return ProviderCapabilities(
            supports_streaming=True,
            supports_subscription_auth=False,
            supports_api_key_auth=True,  # BYOK LLM config in the server.json agent payload
            supports_headless_mode=True,
            supports_diff_output=False,
            supports_approval_hooks=False,
            default_timeout_seconds=1800,
        )

    # --- configuration -----------------------------------------------------------------

    def _load_server_config(self, request: AgentSessionRequest) -> dict[str, Any] | str:
        """The parsed provider-home server.json, or a failure reason string."""
        config_path = Path(request.provider_home) / _SERVER_CONFIG_FILE
        if not config_path.is_file():
            return (
                f"no OpenHands server config: create {_SERVER_CONFIG_FILE} in the provider home "
                "with at least {\"base_url\": ...}"
            )
        try:
            config = json.loads(config_path.read_text())
        except json.JSONDecodeError as exc:
            return f"{_SERVER_CONFIG_FILE} in the provider home is not valid JSON: {exc}"
        if not isinstance(config, dict) or not config.get("base_url"):
            return f"{_SERVER_CONFIG_FILE} in the provider home has no 'base_url'"
        return config

    # --- lifecycle ---------------------------------------------------------------------

    async def start(self, request: AgentSessionRequest) -> None:
        if request.auth_mode != "api_key":
            raise ValueError("OpenHandsAdapter auth_mode must be 'api_key' (BYOK LLM config)")
        self._request = request
        provider_home = Path(request.provider_home)
        provider_home.mkdir(parents=True, exist_ok=True)
        self._transcript_path = provider_home / "runs" / request.run_id / "transcript.jsonl"
        self._transcript_path.parent.mkdir(parents=True, exist_ok=True)

        config = self._load_server_config(request)
        if isinstance(config, str):
            await self._fail_before_launch(config)
            return

        headers = {}
        if config.get("session_api_key"):
            headers["Authorization"] = f"Bearer {config['session_api_key']}"
        self._client = httpx.AsyncClient(
            base_url=str(config["base_url"]).rstrip("/"), headers=headers, timeout=30.0,
            transport=self._transport,
        )

        # Capability check (Phase 12 Track A): server reachable and the key accepted -- probe a
        # documented endpoint rather than crashing mid-run on the first poll.
        try:
            probe = await self._client.get("/conversations/search", params={"limit": 1})
        except httpx.HTTPError as exc:
            await self._fail_before_launch(f"OpenHands server unreachable at {config['base_url']}: {exc}")
            return
        if probe.status_code in {401, 403}:
            await self._fail_before_launch("OpenHands server rejected the session_api_key")
            return

        body: dict[str, Any] = {
            "workspace": {"working_dir": request.workspace_path},
            "initial_message": {"role": "user", "content": [{"type": "text", "text": request.prompt}]},
            "confirmation_policy": {"kind": "NeverConfirm"},
        }
        if isinstance(config.get("agent"), dict):
            body["agent"] = config["agent"]
        if isinstance(config.get("max_iterations"), int):
            body["max_iterations"] = config["max_iterations"]

        try:
            response = await self._client.post("/conversations", json=body)
            response.raise_for_status()
            info = response.json()
        except (httpx.HTTPError, json.JSONDecodeError) as exc:
            await self._fail_before_launch(f"could not start an OpenHands conversation: {exc}")
            return
        self._conversation_id = str(info.get("id") or info.get("conversation_id") or "")
        if not self._conversation_id:
            await self._fail_before_launch(f"OpenHands returned no conversation id: {info}")
            return
        self._runner_task = asyncio.create_task(self._run())

    async def _fail_before_launch(self, reason: str) -> None:
        assert self._request is not None and self._transcript_path is not None
        message = _mask(f"capability check failed for {self.key}: {reason}", self._request.env)
        self._summary = message
        self._transcript_lines.append(message)
        self._transcript_path.write_text("\n".join(self._transcript_lines) + "\n")
        # No "stream" in metadata -> the orchestrator persists these as SYSTEM log lines.
        await self._queue.put(AgentEvent(AgentEventType.ERROR, message, _now_iso(), {"check": "capability"}))
        await self._queue.put(AgentEvent(AgentEventType.STATUS, "failed", _now_iso(), {"exit_code": 1}))
        await self._queue.put(_SENTINEL)
        self._final_status = "error"
        self._done.set()
        if self._client is not None:
            await self._client.aclose()
            self._client = None

    # --- event proxying ------------------------------------------------------------------

    async def _run(self) -> None:
        assert self._request is not None and self._client is not None
        try:
            await asyncio.wait_for(self._poll_until_terminal(), timeout=self._request.timeout_seconds)
        except asyncio.TimeoutError:
            self._timed_out = True
            self._summary = f"Timed out after {self._request.timeout_seconds}s"
            await self._cancel_remote()
        finally:
            await self._finish()

    async def _poll_until_terminal(self) -> None:
        assert self._client is not None and self._conversation_id is not None
        next_page_id: str | None = None
        while True:
            next_page_id = await self._drain_events(next_page_id)
            try:
                response = await self._client.get(f"/conversations/{self._conversation_id}")
                status = str(response.json().get("execution_status") or "").lower()
            except (httpx.HTTPError, json.JSONDecodeError):
                status = ""
            if status in _TERMINAL_STATUSES:
                self._final_status = status
                await self._drain_events(next_page_id)  # trailing events emitted at completion
                return
            await asyncio.sleep(_POLL_INTERVAL_SECONDS)

    async def _drain_events(self, page_id: str | None) -> str | None:
        """Emit any not-yet-seen events; returns the page to resume from on the next poll (the
        last page is re-fetched each time because it may still be growing -- ``_seen_event_keys``
        keeps the re-fetch from double-emitting)."""
        assert self._client is not None and self._conversation_id is not None and self._request is not None
        while True:
            params = {"page_id": page_id} if page_id else {}
            try:
                response = await self._client.get(f"/conversations/{self._conversation_id}/events", params=params)
                page = response.json()
            except (httpx.HTTPError, json.JSONDecodeError):
                return page_id
            for event in page.get("items") or []:
                if not isinstance(event, dict):
                    continue
                key = str(event.get("id") or json.dumps(event, sort_keys=True))
                if key in self._seen_event_keys:
                    continue
                self._seen_event_keys.add(key)
                self._transcript_lines.append(_mask(json.dumps(event, sort_keys=True), self._request.env))
                await self._emit_remote_event(event)
            next_page_id = page.get("next_page_id")
            if not next_page_id or next_page_id == page_id:
                return page_id
            page_id = next_page_id

    async def _emit_remote_event(self, event: dict[str, Any]) -> None:
        """Map a kind-discriminated agent-server event onto the Section 7.1 event types."""
        assert self._request is not None
        kind = str(event.get("kind") or "")
        if "Action" in kind:
            tool = str(event.get("tool_name") or event.get("tool") or kind)
            action = event.get("action")
            detail = json.dumps(action, sort_keys=True) if isinstance(action, dict) else str(action or "")
            await self._emit(AgentEventType.TOOL_USE, f"{tool}: {detail}", {"tool": tool, "kind": kind})
        elif "Error" in kind:
            message = str(event.get("error") or event.get("message") or json.dumps(event, sort_keys=True))
            self._summary = message
            await self._emit(AgentEventType.ERROR, message, {"kind": kind})
        else:
            message = _extract_text(event) or json.dumps(event, sort_keys=True)
            if kind == "MessageEvent" and message.strip():
                self._summary = message
            await self._emit(AgentEventType.LOG, message, {"stream": "stdout", "kind": kind})

    async def _emit(self, event_type: AgentEventType, message: str, metadata: dict) -> None:
        assert self._request is not None
        await self._queue.put(AgentEvent(event_type, _mask(message, self._request.env), _now_iso(), metadata))

    async def _finish(self) -> None:
        assert self._request is not None and self._transcript_path is not None
        exit_code = 0 if (self._final_status == "finished" and not self._timed_out) else 1
        await self._queue.put(AgentEvent(
            AgentEventType.STATUS,
            "timeout" if self._timed_out else "completed",
            _now_iso(), {"exit_code": exit_code, "conversationStatus": self._final_status},
        ))
        self._transcript_path.write_text("\n".join(self._transcript_lines) + "\n")
        await self._queue.put(_SENTINEL)
        self._done.set()
        if self._client is not None:
            await self._client.aclose()
            self._client = None

    async def _cancel_remote(self) -> None:
        if self._client is not None and self._conversation_id is not None:
            with contextlib.suppress(httpx.HTTPError):
                await self._client.delete(f"/conversations/{self._conversation_id}")

    async def events(self) -> AsyncIterator[AgentEvent]:
        while True:
            item = await self._queue.get()
            if item is _SENTINEL:
                return
            yield item

    async def stop(self) -> None:
        """Cancels the remote session (DELETE) -- there is no local process group to signal."""
        self._stopped = True
        await self._cancel_remote()
        if self._runner_task is not None and not self._runner_task.done():
            self._runner_task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await self._runner_task
            if not self._done.is_set():
                await self._finish()

    async def result(self) -> AgentResult:
        await self._done.wait()
        exit_code = 0 if (self._final_status == "finished" and not self._timed_out and not self._stopped) else 1
        return AgentResult(
            exit_code=exit_code,
            success=(exit_code == 0),
            summary=self._summary,
            raw_output_path=str(self._transcript_path),
        )
