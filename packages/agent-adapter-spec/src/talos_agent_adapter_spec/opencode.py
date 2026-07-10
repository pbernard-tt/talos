"""OpenCode adapter (Section 7.4 #3, implemented in Phase 12 Track A).

Verified 2026-07-10 against the OpenCode CLI reference (opencode.ai/docs/cli) and the ``run``
command source on the provider's repository; current release at verification time: **v1.17.17**.
``opencode run <message> --format json`` emits one JSON object per line shaped
``{"type", "timestamp", "sessionID", ...data}`` with types ``tool_use``/``text``/``step_start``/
``error``, where ``tool_use``/``text`` carry a ``part`` (``part.tool`` + ``part.state`` for tools,
``part.text`` for text). ``--print-logs`` keeps diagnostics on stderr so stdout stays pure JSON.

Auth is ``api_key`` mode only (Section 16 Phase 12): credentials come from
``<provider_home>/.local/share/opencode/auth.json`` (written by ``opencode auth login``), a
``provider`` section in the provider-home config, or an injected ``*_API_KEY`` env var. Model and
provider selection live in ``<provider_home>/.config/opencode/opencode.json`` -- Talos stays
provider-flexible without new schema. The adapter merges non-bypassable ``permission`` deny rules
into that config (mirroring ClaudeCodeAdapter's native deny rules) and deliberately does not use
``--dangerously-skip-permissions``.
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

_NATIVE_DENY_RULES = {
    "git commit *": "deny",
    "git push *": "deny",
    "git reset --hard *": "deny",
    "rm -rf *": "deny",
}


class OpenCodeAdapter(CliAgentAdapter):
    key = "opencode"
    cli = "opencode"
    # The {"type", "timestamp", "sessionID", ...} JSON event envelope is the 1.x `run --format
    # json` format; docs were verified against v1.17.17.
    min_cli_version = (1, 0)

    def capabilities(self) -> ProviderCapabilities:
        return ProviderCapabilities(
            supports_streaming=True,
            supports_subscription_auth=False,
            supports_api_key_auth=True,
            supports_headless_mode=True,
            supports_diff_output=False,
            supports_approval_hooks=True,  # native permission rules in opencode.json (below)
            default_timeout_seconds=1800,
        )

    def _cli_args(self, request: AgentSessionRequest, home: str) -> list[str]:
        return ["opencode", "run", request.prompt, "--format", "json", "--print-logs"]

    def _extra_env(self, request: AgentSessionRequest, home: str) -> dict[str, str]:
        # OpenCode resolves config/data through the XDG base dirs. Pin them under the provider
        # home so a host-level XDG_* value can never redirect writes outside it (contract 7.2(5)).
        return {
            "XDG_CONFIG_HOME": f"{home}/.config",
            "XDG_DATA_HOME": f"{home}/.local/share",
            "XDG_STATE_HOME": f"{home}/.local/state",
            "XDG_CACHE_HOME": f"{home}/.cache",
        }

    def _prepare_provider_home(self, request: AgentSessionRequest) -> None:
        """Merge Talos' non-bypassable deny rules into the provider-home opencode.json without
        discarding operator-owned model/provider config."""
        config_path = Path(request.provider_home) / ".config" / "opencode" / "opencode.json"
        config_path.parent.mkdir(parents=True, exist_ok=True)
        try:
            config: dict[str, Any] = json.loads(config_path.read_text()) if config_path.exists() else {}
        except json.JSONDecodeError:
            # Keep malformed operator-owned config intact: OpenCode will report its configuration
            # error in the run transcript instead of Talos silently destroying provider config.
            return
        permission = config.setdefault("permission", {})
        bash = permission.get("bash")
        if not isinstance(bash, dict):
            bash = {"*": bash} if isinstance(bash, str) else {"*": "allow"}
            permission["bash"] = bash
        bash.setdefault("*", "allow")  # headless: unlisted commands must not stall on "ask"
        bash.update(_NATIVE_DENY_RULES)
        permission.setdefault("edit", "allow")
        config_path.write_text(json.dumps(config, indent=2) + "\n")

    def _credentials_missing(self, request: AgentSessionRequest) -> str | None:
        if request.auth_mode != "api_key":
            return f"auth_mode {request.auth_mode!r} is not supported -- OpenCode runs are api_key only"
        provider_home = Path(request.provider_home)
        if (provider_home / ".local" / "share" / "opencode" / "auth.json").is_file():
            return None
        config_path = provider_home / ".config" / "opencode" / "opencode.json"
        try:
            if config_path.is_file() and json.loads(config_path.read_text()).get("provider"):
                return None  # operator-configured provider (keys inline or via {env:...})
        except json.JSONDecodeError:
            pass
        if any(name.endswith("_API_KEY") for name in request.env):
            return None
        return (
            "no OpenCode credentials in the provider home: run `opencode auth login` there, "
            "add a 'provider' section to opencode.json, or inject an *_API_KEY env var"
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
        part = event.get("part") if isinstance(event.get("part"), dict) else {}
        if event_type == "tool_use":
            tool = part.get("tool") or "tool"
            state = part.get("state") if isinstance(part.get("state"), dict) else {}
            tool_input = state.get("input") if isinstance(state.get("input"), dict) else {}
            detail = tool_input.get("command") or state.get("title") or json.dumps(tool_input, sort_keys=True)
            await self._emit(AgentEventType.TOOL_USE, f"{tool}: {detail}", {"tool": tool})
        elif event_type == "text":
            text = str(part.get("text") or "")
            if text.strip():
                self._summary = text
            await self._emit(AgentEventType.LOG, text, {"stream": "stdout"})
        elif event_type == "error":
            error = event.get("error")
            message = error if isinstance(error, str) else json.dumps(error, sort_keys=True)
            self._summary = message
            await self._emit(AgentEventType.ERROR, message, {})
        else:
            await self._emit(AgentEventType.LOG, raw, {"stream": "stdout", "eventType": event_type})
