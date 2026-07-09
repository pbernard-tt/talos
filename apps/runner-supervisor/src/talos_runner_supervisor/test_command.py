"""POST /runs/{id}/tests: runs the project's configured talos.yaml commands.test in the
workspace, streaming output. A null command (none configured) short-circuits with exit 0.
"""

from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from typing import Any

from talos_runner_supervisor.process_utils import kill_process_group


async def stream_test_command(workspace_path: str, command: str | None, timeout_seconds: int) -> AsyncIterator[dict[str, Any]]:
    if not command:
        yield {"type": "result", "exitCode": 0}
        return

    process = await asyncio.create_subprocess_shell(
        command,
        cwd=workspace_path,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.STDOUT,
        start_new_session=True,
    )

    loop = asyncio.get_event_loop()
    deadline = loop.time() + timeout_seconds
    timed_out = False

    while True:
        remaining = deadline - loop.time()
        if remaining <= 0:
            timed_out = True
            break
        try:
            line = await asyncio.wait_for(process.stdout.readline(), timeout=remaining)
        except asyncio.TimeoutError:
            timed_out = True
            break
        if not line:
            break
        yield {"type": "log", "message": line.decode(errors="replace").rstrip("\n")}

    if timed_out:
        await kill_process_group(process)
        yield {"type": "log", "message": "[timeout] test command exceeded timeout_seconds"}
        exit_code = process.returncode if process.returncode is not None else -1
    else:
        exit_code = await process.wait()

    yield {"type": "result", "exitCode": exit_code}
