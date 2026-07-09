"""CustomShellAdapter (Section 7.4 #1, Phase 6): executes a configured deterministic script in the
workspace. No AI -- proves the run pipeline end to end and is reused for build/test/deploy steps.

Interim Phase 6 semantics (no prompt assembler exists until Phase 7 -- Section 7.3): the orchestrator
passes the literal shell command to run as ``request.prompt``. This is deliberate: CustomShellAdapter
is explicitly "No AI", so the generic "assembled instructions" field is repurposed as "the command to
execute" for this one adapter. Documented as a Phase 6 judgment call in docs/phase-reports/phase-6-report.md.
"""

from __future__ import annotations

import asyncio
import contextlib
import os
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
    ProviderCapabilities,
)

_SENTINEL = object()


def _now_iso() -> str:
    return datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%S.%fZ")


def _mask(text: str, env: dict[str, str]) -> str:
    """Contract test 7.2(6): no value from request.env may ever appear in emitted events."""
    for value in env.values():
        if value:
            text = text.replace(value, "***")
    return text


class CustomShellAdapter(AgentAdapter):
    key = "custom-shell"

    def __init__(self) -> None:
        self._request: AgentSessionRequest | None = None
        self._process: asyncio.subprocess.Process | None = None
        self._queue: asyncio.Queue = asyncio.Queue()
        self._timed_out = False
        self._exit_code: int | None = None
        self._transcript_path: Path | None = None
        self._transcript_lines: list[str] = []
        self._done = asyncio.Event()
        self._runner_task: asyncio.Task | None = None

    def capabilities(self) -> ProviderCapabilities:
        return ProviderCapabilities(
            supports_streaming=True,
            supports_subscription_auth=False,
            supports_api_key_auth=False,
            supports_headless_mode=True,
            supports_diff_output=False,
            supports_approval_hooks=False,
            default_timeout_seconds=1800,
        )

    async def start(self, request: AgentSessionRequest) -> None:
        self._request = request

        # Writes only within workspace_path or provider_home (contract test 7.2(5)); the transcript
        # is adapter bookkeeping, not agent output, so it belongs under provider_home, never inside
        # the worktree where it would otherwise pollute the run's git diff.
        self._transcript_path = Path(request.provider_home) / "runs" / request.run_id / "transcript.txt"
        self._transcript_path.parent.mkdir(parents=True, exist_ok=True)

        env = os.environ.copy()
        env.update(request.env)

        self._process = await asyncio.create_subprocess_shell(
            request.prompt,
            cwd=request.workspace_path,
            env=env,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
            start_new_session=True,  # own process group, so stop() can kill the whole tree
        )
        self._runner_task = asyncio.create_task(self._run())

    async def _run(self) -> None:
        assert self._request is not None
        assert self._process is not None
        request = self._request

        async def pump(stream: asyncio.StreamReader, stream_name: str) -> None:
            while True:
                line = await stream.readline()
                if not line:
                    return
                message = _mask(line.decode(errors="replace").rstrip("\n"), request.env)
                self._transcript_lines.append(f"[{stream_name}] {message}")
                await self._queue.put(
                    AgentEvent(
                        type=AgentEventType.LOG,
                        message=message,
                        timestamp=_now_iso(),
                        metadata={"stream": stream_name},
                    )
                )

        pumps = asyncio.gather(
            pump(self._process.stdout, "stdout"),
            pump(self._process.stderr, "stderr"),
        )

        try:
            await asyncio.wait_for(
                asyncio.gather(pumps, self._process.wait()),
                timeout=request.timeout_seconds,
            )
            self._exit_code = self._process.returncode
        except asyncio.TimeoutError:
            self._timed_out = True
            await self._kill_process_group()
            self._exit_code = self._process.returncode if self._process.returncode is not None else -1
            pumps.cancel()

        await self._queue.put(
            AgentEvent(
                type=AgentEventType.STATUS,
                message="timeout" if self._timed_out else "completed",
                timestamp=_now_iso(),
                metadata={"exit_code": self._exit_code},
            )
        )
        await self._queue.put(_SENTINEL)

        self._transcript_path.write_text("\n".join(self._transcript_lines) + "\n")
        self._done.set()

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
        if self._process is None or self._process.returncode is not None:
            return
        try:
            pgid = os.getpgid(self._process.pid)
        except ProcessLookupError:
            return
        try:
            os.killpg(pgid, signal.SIGTERM)
        except ProcessLookupError:
            return

        try:
            await asyncio.wait_for(self._process.wait(), timeout=10)
            return
        except asyncio.TimeoutError:
            pass

        try:
            os.killpg(pgid, signal.SIGKILL)
        except ProcessLookupError:
            return
        await self._process.wait()

    async def result(self) -> AgentResult:
        await self._done.wait()
        exit_code = self._exit_code if self._exit_code is not None else -1
        return AgentResult(
            exit_code=exit_code,
            success=(exit_code == 0 and not self._timed_out),
            summary="Timed out after {}s".format(self._request.timeout_seconds) if self._timed_out else None,
            raw_output_path=str(self._transcript_path),
        )
