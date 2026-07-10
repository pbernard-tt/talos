"""Codex CLI adapter (Section 7.4 #4, implemented in Phase 12 Track A).

Verified 2026-07-10 against a locally installed **codex-cli 0.144.0** (``codex exec --help``) and
a live recorded run (tests/fixtures/codex_exec_stream.jsonl). ``codex exec <prompt> --json`` runs
non-interactively and prints JSONL events: ``thread.started``, ``turn.started``,
``item.started``/``item.completed`` (items typed ``command_execution`` with
``command``/``aggregated_output``/``exit_code``, ``agent_message`` with ``text``, ``file_change``
with ``changes``), and ``turn.completed`` carrying token ``usage`` (surfaced in event metadata for
Phase 14 cost tracking).

Sandboxing depends on the execution mode: as a bare subprocess codex keeps its own
``-s workspace-write`` sandbox; inside the Phase 11 per-run container that sandbox cannot start
(bwrap needs user namespaces the hardened container forbids), so there -- and only there -- the
adapter passes ``--dangerously-bypass-approvals-and-sandbox``, which codex documents as intended
solely for externally sandboxed environments. Sessions and config live under
``CODEX_HOME=<provider_home>/.codex``. ``provider_auth_mode=api_key``
(``codex login --api-key`` in the provider home, or an injected ``OPENAI_API_KEY``) is the
automation path; ``subscription_local`` (ChatGPT sign-in) is personal use only and never exposed
to other users (Section 13) -- that policy is enforced at the API layer, both modes execute here.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from talos_agent_adapter_spec.adapter import (
    AgentEventType,
    AgentSessionRequest,
    ProviderCapabilities,
)
from talos_agent_adapter_spec.cli_agent import CliAgentAdapter


class CodexCliAdapter(CliAgentAdapter):
    key = "codex-cli"
    cli = "codex"
    # 0.144.0 is the version the `--json` thread/turn/item JSONL schema was verified against;
    # older CLIs used a different (since-removed) event shape.
    min_cli_version = (0, 144)

    def capabilities(self) -> ProviderCapabilities:
        return ProviderCapabilities(
            supports_streaming=True,
            supports_subscription_auth=True,
            supports_api_key_auth=True,
            supports_headless_mode=True,
            supports_diff_output=False,
            supports_approval_hooks=False,
            default_timeout_seconds=1800,
        )

    def _cli_args(self, request: AgentSessionRequest, home: str) -> list[str]:
        # No --skip-git-repo-check: the workspace is always a Talos-prepared worktree, so the
        # repo check acts as a cheap guard against ever executing outside one.
        args = ["codex", "exec", request.prompt, "--json"]
        if request.container is not None:
            # Codex's own sandbox is bwrap-based and cannot start inside the Phase 11 per-run
            # container (cap-drop ALL + no-new-privileges block the user namespaces bwrap needs;
            # verified live 2026-07-10: every workspace-write file edit failed with "bwrap is
            # unavailable"). Codex documents this flag as intended solely for environments that
            # are externally sandboxed -- the Phase 11 container is exactly that boundary.
            args.append("--dangerously-bypass-approvals-and-sandbox")
        else:
            args.extend(["-s", "workspace-write"])
        return args

    def _extra_env(self, request: AgentSessionRequest, home: str) -> dict[str, str]:
        return {"CODEX_HOME": f"{home}/.codex"}

    def _prepare_provider_home(self, request: AgentSessionRequest) -> None:
        # config.toml inside is operator-managed (model, provider overrides); Talos only
        # guarantees the directory exists so CODEX_HOME resolves.
        (Path(request.provider_home) / ".codex").mkdir(parents=True, exist_ok=True)

    def _credentials_missing(self, request: AgentSessionRequest) -> str | None:
        if request.auth_mode not in {"api_key", "subscription_local"}:
            return f"auth_mode {request.auth_mode!r} is not supported (api_key or subscription_local)"
        auth_json = Path(request.provider_home) / ".codex" / "auth.json"
        if auth_json.is_file():
            return None
        if request.auth_mode == "api_key" and "OPENAI_API_KEY" in request.env:
            return None
        if request.auth_mode == "subscription_local":
            return "no Codex subscription credentials: run `codex login` inside the provider home"
        return (
            "no Codex credentials in the provider home: run `codex login --api-key` there "
            "or inject OPENAI_API_KEY"
        )

    async def _handle_stdout_line(self, raw: str) -> None:
        assert self._request is not None
        try:
            event = json.loads(raw)
        except json.JSONDecodeError:
            event = None
        if not isinstance(event, dict):
            await self._emit(AgentEventType.LOG, raw, {"stream": "stdout"})
            return

        event_type = event.get("type")
        item = event.get("item") if isinstance(event.get("item"), dict) else {}
        item_type = item.get("type")

        if event_type == "item.started" and item_type == "command_execution":
            await self._emit(
                AgentEventType.TOOL_USE,
                f"command_execution: {item.get('command', '')}",
                {"tool": "command_execution"},
            )
        elif event_type == "item.completed" and item_type == "command_execution":
            for line in str(item.get("aggregated_output") or "").splitlines():
                await self._emit(AgentEventType.LOG, line, {"stream": "stdout"})
            await self._emit(
                AgentEventType.LOG,
                f"command exited {item.get('exit_code')}: {item.get('command', '')}",
                {"tool": "command_execution", "exit_code": item.get("exit_code")},
            )
        elif event_type == "item.completed" and item_type == "agent_message":
            text = str(item.get("text") or "")
            if text.strip():
                self._summary = text
            await self._emit(AgentEventType.LOG, text, {"stream": "stdout"})
        elif event_type == "item.completed" and item_type == "file_change":
            changes = item.get("changes") if isinstance(item.get("changes"), list) else []
            paths = ", ".join(str(change.get("path", "?")) for change in changes if isinstance(change, dict))
            await self._emit(AgentEventType.TOOL_USE, f"file_change: {paths or raw}", {"tool": "file_change"})
        elif event_type in {"error", "turn.failed"}:
            message = _error_message(event)
            self._summary = message
            await self._emit(AgentEventType.ERROR, message, {})
        elif event_type == "turn.completed":
            usage = event.get("usage") if isinstance(event.get("usage"), dict) else {}
            summary = ", ".join(f"{name}={value}" for name, value in sorted(usage.items()))
            # No "stream" key -> persisted as a SYSTEM log line; metadata keeps raw usage counts.
            await self._emit(AgentEventType.LOG, f"turn completed ({summary})", {"eventType": event_type, "usage": usage})
        else:
            await self._emit(AgentEventType.LOG, raw, {"stream": "stdout", "eventType": event_type})


def _error_message(event: dict[str, Any]) -> str:
    error = event.get("error")
    if isinstance(error, dict) and error.get("message"):
        return str(error["message"])
    if isinstance(error, str) and error:
        return error
    return event.get("message") or json.dumps(event, sort_keys=True)
