"""Shared subprocess plumbing for CLI-based coding-agent adapters (Phase 12, Track A).

Everything OpenCodeAdapter and CodexCliAdapter have in common lives here: local/containerized
spawn, stdout/stderr pumps, timeout handling, process-group kill, transcript capture, masking,
and the pre-launch capability check (Section 16, Phase 12 Track A: CLI present, version
compatible, credentials present in the provider home -- a failed check fails the run with a
clear SYSTEM log line instead of a mid-run crash; events without ``stream`` metadata map to
SYSTEM in the orchestrator's log fan-out).

ClaudeCodeAdapter (Phase 7) and CustomShellAdapter (Phase 6) predate this module and keep their
own identical plumbing -- refactoring already-tested MVP adapters mid-phase was deliberately
avoided. Folding them onto this base is a candidate follow-up ticket.
"""

from __future__ import annotations

import asyncio
import contextlib
import os
import re
import shutil
import signal
from datetime import UTC, datetime
from pathlib import Path
from typing import AsyncIterator

from talos_agent_adapter_spec.adapter import (
    AgentAdapter,
    AgentEvent,
    AgentEventType,
    AgentResult,
    AgentSessionRequest,
)
from talos_agent_adapter_spec.container import (
    PROVIDER_HOME_MOUNT_TARGET,
    build_docker_run_args,
    kill_container,
    write_env_file,
)

_SENTINEL = object()
_VERSION_PROBE_TIMEOUT_SECONDS = 60


def _now_iso() -> str:
    return datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%S.%fZ")


def _mask(text: str, env: dict[str, str]) -> str:
    """Contract test 7.2(6): no value from request.env may ever appear in emitted events."""
    for value in env.values():
        if value:
            text = text.replace(value, "***")
    return text


def parse_version(output: str) -> tuple[int, ...] | None:
    """First dotted number in a ``--version`` output, e.g. "codex-cli 0.144.0" -> (0, 144, 0)."""
    match = re.search(r"(\d+(?:\.\d+)+)", output)
    return tuple(int(part) for part in match.group(1).split(".")) if match else None


async def _capture(args: list[str], env: dict[str, str]) -> str | None:
    """Combined stdout+stderr of a short probe command, or None on nonzero exit / spawn failure."""
    try:
        process = await asyncio.create_subprocess_exec(
            *args, env=env, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.STDOUT
        )
        stdout, _ = await asyncio.wait_for(process.communicate(), timeout=_VERSION_PROBE_TIMEOUT_SECONDS)
    except (OSError, asyncio.TimeoutError):
        return None
    return stdout.decode(errors="replace") if process.returncode == 0 else None


class CliAgentAdapter(AgentAdapter):
    """Base for adapters that wrap a coding-agent CLI in non-interactive mode.

    Subclasses set ``key``, ``cli``, ``min_cli_version`` and implement ``capabilities()`` plus the
    four hooks: ``_cli_args``, ``_extra_env``, ``_prepare_provider_home``, ``_handle_stdout_line``,
    and ``_credentials_missing``. ``home`` passed to hooks is the HOME path as seen by the agent
    process: the host provider_home locally, the fixed mount target inside a container.
    """

    cli: str
    min_cli_version: tuple[int, ...]

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

    # --- subclass hooks ---------------------------------------------------------------

    def _cli_args(self, request: AgentSessionRequest, home: str) -> list[str]:
        raise NotImplementedError

    def _extra_env(self, request: AgentSessionRequest, home: str) -> dict[str, str]:
        return {}

    def _prepare_provider_home(self, request: AgentSessionRequest) -> None:
        """Runs in the supervisor's own process on the host-visible provider_home path, before
        the (possibly containerized) agent process exists."""

    async def _handle_stdout_line(self, raw: str) -> None:
        raise NotImplementedError

    def _credentials_missing(self, request: AgentSessionRequest) -> str | None:
        """Failure reason if the provider home lacks usable credentials, else None."""
        raise NotImplementedError

    # --- lifecycle ---------------------------------------------------------------------

    async def start(self, request: AgentSessionRequest) -> None:
        self._request = request
        provider_home = Path(request.provider_home)
        provider_home.mkdir(parents=True, exist_ok=True)
        self._prepare_provider_home(request)
        self._transcript_path = provider_home / "runs" / request.run_id / "transcript.jsonl"
        self._transcript_path.parent.mkdir(parents=True, exist_ok=True)

        failure = await self._preflight(request)
        if failure is not None:
            await self._fail_before_launch(failure)
            return

        if request.container is not None:
            home = PROVIDER_HOME_MOUNT_TARGET
            env = dict(request.env)
            env["HOME"] = home
            env.update(self._extra_env(request, home))
            self._env_file_path = write_env_file(env)
            args = build_docker_run_args(
                request.run_id, request.container, self._env_file_path, self._cli_args(request, home)
            )
            self._process = await asyncio.create_subprocess_exec(
                *args,
                env=os.environ.copy(),  # no secrets here -- request.env travels via --env-file only
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
                start_new_session=True,
            )
            # env file NOT deleted here -- see custom_shell.py's identical comment; `docker run`'s
            # own --env-file read races this coroutine resuming. Removed once the process exits.
            self._runner_task = asyncio.create_task(self._run())
            return

        home = str(provider_home)
        env = os.environ.copy()
        env.update(request.env)
        env["HOME"] = home
        env.update(self._extra_env(request, home))
        self._process = await asyncio.create_subprocess_exec(
            *self._cli_args(request, home),
            cwd=request.workspace_path,
            env=env,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
            start_new_session=True,
        )
        self._runner_task = asyncio.create_task(self._run())

    # --- capability check ----------------------------------------------------------------

    async def _preflight(self, request: AgentSessionRequest) -> str | None:
        """Section 16 Phase 12 Track A: CLI present (locally or in the run image), version at
        least ``min_cli_version``, credentials present. Returns the failure reason or None."""
        reason = self._credentials_missing(request)
        if reason is not None:
            return reason

        if request.container is not None:
            output = await _capture(
                ["docker", "run", "--rm", "--network", "none", request.container.image, self.cli, "--version"],
                env=os.environ.copy(),
            )
            if output is None:
                return f"'{self.cli} --version' failed in image {request.container.image} -- CLI missing from image?"
        else:
            path = {**os.environ, **request.env}.get("PATH", "")
            executable = shutil.which(self.cli, path=path)
            if executable is None:
                return f"{self.cli} CLI not found on PATH"
            env = os.environ.copy()
            env.update(request.env)
            output = await _capture([executable, "--version"], env=env)
            if output is None:
                return f"'{self.cli} --version' failed"

        version = parse_version(output)
        if version is None:
            return f"could not parse a {self.cli} version from {output.strip()!r}"
        if version < self.min_cli_version:
            minimum = ".".join(str(part) for part in self.min_cli_version)
            found = ".".join(str(part) for part in version)
            return f"{self.cli} {found} is older than the minimum supported {minimum}"
        return None

    async def _fail_before_launch(self, reason: str) -> None:
        assert self._request is not None and self._transcript_path is not None
        message = _mask(f"capability check failed for {self.key}: {reason}", self._request.env)
        self._summary = message
        self._exit_code = -1
        self._transcript_lines.append(message)
        self._transcript_path.write_text("\n".join(self._transcript_lines) + "\n")
        # No "stream" in metadata -> the orchestrator persists these as SYSTEM log lines.
        await self._queue.put(AgentEvent(AgentEventType.ERROR, message, _now_iso(), {"check": "capability"}))
        await self._queue.put(AgentEvent(AgentEventType.STATUS, "failed", _now_iso(), {"exit_code": -1}))
        await self._queue.put(_SENTINEL)
        self._done.set()

    # --- event pumps ---------------------------------------------------------------------

    async def _run(self) -> None:
        assert self._request is not None and self._process is not None and self._transcript_path is not None
        request = self._request

        async def pump_stdout() -> None:
            assert self._process is not None and self._process.stdout is not None
            while line := await self._process.stdout.readline():
                raw = line.decode(errors="replace").rstrip("\n")
                self._transcript_lines.append(_mask(raw, request.env))
                await self._handle_stdout_line(raw)

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

    async def _emit(self, event_type: AgentEventType, message: str, metadata: dict) -> None:
        assert self._request is not None
        await self._queue.put(AgentEvent(event_type, _mask(message, self._request.env), _now_iso(), metadata))

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
