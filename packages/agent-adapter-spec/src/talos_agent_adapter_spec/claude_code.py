"""Claude Code adapter (Section 7.4 #2, implemented in Phase 7).

Verified against Anthropic's CLI reference on 2026-07-09.  Claude Code supports print mode,
``--output-format stream-json``, ``--verbose``, ``--max-turns``, and ``--permission-mode``.
The adapter deliberately uses the documented ``acceptEdits`` permission mode rather than the
unsafe ``--dangerously-skip-permissions`` flag.  Its provider-home settings add native deny rules
for operations Talos must never delegate to an agent (committing, pushing, destructive resets).

Phase 11: when ``request.container`` is set, `claude` runs inside a per-run Docker container
(Section 8/12.1) instead of directly as a talos-runner-supervisor subprocess -- see container.py.
"""

from __future__ import annotations

import asyncio
import contextlib
import json
import os
import signal
from datetime import UTC, datetime
from pathlib import Path
from typing import Any, AsyncIterator

from talos_agent_adapter_spec.adapter import (
    AgentAdapter,
    AgentEvent,
    AgentEventType,
    AgentResult,
    AgentSessionRequest,
    ProviderCapabilities,
)
from talos_agent_adapter_spec.container import (
    PROVIDER_HOME_MOUNT_TARGET,
    build_docker_run_args,
    kill_container,
    write_env_file,
)

_SENTINEL = object()
_NATIVE_DENY_RULES = [
    "Bash(git commit:*)",
    "Bash(git push:*)",
    "Bash(git reset --hard:*)",
    "Bash(rm -rf:*)",
]


def _now_iso() -> str:
    return datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%S.%fZ")


def _mask(text: str, env: dict[str, str]) -> str:
    for value in env.values():
        if value:
            text = text.replace(value, "***")
    return text


class ClaudeCodeAdapter(AgentAdapter):
    key = "claude-code"

    def __init__(self) -> None:
        self._request: AgentSessionRequest | None = None
        self._process: asyncio.subprocess.Process | None = None
        self._queue: asyncio.Queue = asyncio.Queue()
        self._runner_task: asyncio.Task | None = None
        self._done = asyncio.Event()
        self._timed_out = False
        self._exit_code: int | None = None
        self._summary: str | None = None
        self._transcript_path: Path | None = None
        self._transcript_lines: list[str] = []
        self._env_file_path: str | None = None

    def capabilities(self) -> ProviderCapabilities:
        return ProviderCapabilities(
            supports_streaming=True,
            supports_subscription_auth=True,
            supports_api_key_auth=True,
            supports_headless_mode=True,
            supports_diff_output=False,
            supports_approval_hooks=True,
            default_timeout_seconds=1800,
        )

    async def start(self, request: AgentSessionRequest) -> None:
        if request.auth_mode not in {"api_key", "subscription_local"}:
            raise ValueError("ClaudeCodeAdapter auth_mode must be 'api_key' or 'subscription_local'")
        self._request = request
        # Talos' own settings.json write happens on the host-visible provider_home path regardless
        # of containerization -- this runs in the supervisor's own process, before the container
        # (if any) exists, and is exactly why provider_home is mounted at a fixed container path
        # below rather than requiring the adapter to know a container-relative equivalent up front.
        provider_home = Path(request.provider_home)
        claude_config = provider_home / ".claude"
        claude_config.mkdir(parents=True, exist_ok=True)
        self._configure_native_permissions(claude_config / "settings.json")
        self._transcript_path = provider_home / "runs" / request.run_id / "transcript.jsonl"
        self._transcript_path.parent.mkdir(parents=True, exist_ok=True)

        args = [
            "claude", "-p", request.prompt,
            "--output-format", "stream-json",
            "--verbose",
            "--max-turns", "50",
            "--permission-mode", "acceptEdits",
        ]

        if request.container is not None:
            container_home = PROVIDER_HOME_MOUNT_TARGET
            env = dict(request.env)
            env["HOME"] = container_home
            env["CLAUDE_CONFIG_DIR"] = f"{container_home}/.claude"
            self._env_file_path = write_env_file(env)
            docker_args = build_docker_run_args(request.run_id, request.container, self._env_file_path, args)
            self._process = await asyncio.create_subprocess_exec(
                *docker_args,
                env=os.environ.copy(),  # no secrets here -- request.env travels via --env-file only
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
                start_new_session=True,
            )
            # NOT deleted here -- see custom_shell.py's identical comment; `docker run`'s own
            # --env-file read races this coroutine resuming. Removed once the process exits instead.
            self._runner_task = asyncio.create_task(self._run())
            return

        env = os.environ.copy()
        env.update(request.env)
        env["HOME"] = str(provider_home)
        env["CLAUDE_CONFIG_DIR"] = str(claude_config)
        self._process = await asyncio.create_subprocess_exec(
            *args,
            cwd=request.workspace_path,
            env=env,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
            start_new_session=True,
        )
        self._runner_task = asyncio.create_task(self._run())

    def _configure_native_permissions(self, settings_path: Path) -> None:
        """Merge Talos' non-bypassable provider-side deny rules without discarding login settings."""
        try:
            settings: dict[str, Any] = json.loads(settings_path.read_text()) if settings_path.exists() else {}
        except json.JSONDecodeError:
            # Keep malformed user-owned settings intact: Claude will report its configuration error
            # in the run transcript instead of Talos silently destroying provider credentials/config.
            return
        permissions = settings.setdefault("permissions", {})
        denied = permissions.setdefault("deny", [])
        for rule in _NATIVE_DENY_RULES:
            if rule not in denied:
                denied.append(rule)
        settings_path.write_text(json.dumps(settings, indent=2) + "\n")

    async def _run(self) -> None:
        assert self._request is not None and self._process is not None and self._transcript_path is not None
        request = self._request

        async def pump_stdout() -> None:
            assert self._process is not None and self._process.stdout is not None
            while line := await self._process.stdout.readline():
                raw = line.decode(errors="replace").rstrip("\n")
                masked = _mask(raw, request.env)
                self._transcript_lines.append(masked)
                await self._emit_stream_json(raw)

        async def pump_stderr() -> None:
            assert self._process is not None and self._process.stderr is not None
            while line := await self._process.stderr.readline():
                message = _mask(line.decode(errors="replace").rstrip("\n"), request.env)
                self._transcript_lines.append(f"[stderr] {message}")
                await self._queue.put(AgentEvent(AgentEventType.LOG, message, _now_iso(), {"stream": "stderr"}))

        pumps = asyncio.gather(pump_stdout(), pump_stderr())
        try:
            await asyncio.wait_for(asyncio.gather(pumps, self._process.wait()), timeout=request.timeout_seconds)
            self._exit_code = self._process.returncode
        except asyncio.TimeoutError:
            self._timed_out = True
            self._summary = f"Timed out after {request.timeout_seconds}s"
            await self._kill_process_group()
            self._exit_code = self._process.returncode if self._process.returncode is not None else -1
            pumps.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await pumps

        await self._queue.put(AgentEvent(
            AgentEventType.STATUS,
            "timeout" if self._timed_out else "completed",
            _now_iso(), {"exit_code": self._exit_code},
        ))
        self._transcript_path.write_text("\n".join(self._transcript_lines) + "\n")
        if self._env_file_path is not None:
            with contextlib.suppress(FileNotFoundError):
                os.unlink(self._env_file_path)
            self._env_file_path = None
        await self._queue.put(_SENTINEL)
        self._done.set()

    async def _emit_stream_json(self, raw: str) -> None:
        assert self._request is not None
        try:
            event = json.loads(raw)
        except json.JSONDecodeError:
            await self._queue.put(AgentEvent(AgentEventType.LOG, _mask(raw, self._request.env), _now_iso(), {"stream": "stdout"}))
            return

        if event.get("type") == "result":
            self._summary = event.get("result") or event.get("subtype") or self._summary
        content = (event.get("message") or {}).get("content", [])
        emitted = False
        for block in content if isinstance(content, list) else []:
            if not isinstance(block, dict):
                continue
            if block.get("type") == "tool_use":
                name = block.get("name", "tool")
                tool_input = block.get("input", {})
                detail = tool_input.get("command") if isinstance(tool_input, dict) else None
                message = f"{name}: {detail if detail is not None else json.dumps(tool_input, sort_keys=True)}"
                await self._queue.put(AgentEvent(AgentEventType.TOOL_USE, _mask(message, self._request.env), _now_iso(), {"tool": name}))
                emitted = True
            elif block.get("type") == "text":
                await self._queue.put(AgentEvent(AgentEventType.LOG, _mask(str(block.get("text", "")), self._request.env), _now_iso(), {"stream": "stdout"}))
                emitted = True
        if not emitted:
            await self._queue.put(AgentEvent(AgentEventType.LOG, _mask(raw, self._request.env), _now_iso(), {"stream": "stdout", "eventType": event.get("type")}))

    async def events(self) -> AsyncIterator[AgentEvent]:
        while True:
            item = await self._queue.get()
            if item is _SENTINEL:
                return
            yield item

    async def stop(self) -> None:
        await self._kill_process_group()
        if self._runner_task is not None:
            with contextlib.suppress(asyncio.TimeoutError):
                await asyncio.wait_for(self._runner_task, timeout=15)

    async def _kill_process_group(self) -> None:
        try:
            if self._process is not None and self._process.returncode is None:
                try:
                    pgid = os.getpgid(self._process.pid)
                    os.killpg(pgid, signal.SIGTERM)  # docker run's default --sig-proxy forwards this
                    try:
                        await asyncio.wait_for(self._process.wait(), timeout=10)
                    except asyncio.TimeoutError:
                        with contextlib.suppress(ProcessLookupError):
                            os.killpg(pgid, signal.SIGKILL)
                        await self._process.wait()
                except ProcessLookupError:
                    pass
        finally:
            if self._request is not None and self._request.container is not None:
                # Safety net (see container.kill_container's docstring): guarantees the container is
                # gone even if the `docker run` client above was killed before it could proxy the
                # signal in. Idempotent -- harmless if --rm already cleaned it up.
                await kill_container(self._request.run_id)

    async def result(self) -> AgentResult:
        await self._done.wait()
        exit_code = self._exit_code if self._exit_code is not None else -1
        return AgentResult(
            exit_code=exit_code,
            success=(exit_code == 0 and not self._timed_out),
            summary=self._summary,
            raw_output_path=str(self._transcript_path),
        )
