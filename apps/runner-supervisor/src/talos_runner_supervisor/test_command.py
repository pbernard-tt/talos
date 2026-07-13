# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

"""POST /runs/{id}/tests: runs the project's configured talos.yaml commands.test in the
workspace, streaming output. A null command (none configured) short-circuits with exit 0.
"""

from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from pathlib import Path
from typing import Any

from talos_runner_supervisor.artifact_client import post_artifact
from talos_runner_supervisor.config import Settings
from talos_runner_supervisor.process_utils import kill_process_group


async def stream_test_command(
    settings: Settings, run_id: str, workspace_path: str, command: str | None, timeout_seconds: int
) -> AsyncIterator[dict[str, Any]]:
    if not command:
        yield {"type": "result", "exitCode": 0}
        return

    artifacts_dir = Path(workspace_path).parent / "artifacts"
    artifacts_dir.mkdir(parents=True, exist_ok=True)
    report_path = artifacts_dir / "test-report.log"

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

    with report_path.open("w") as report:
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
            message = line.decode(errors="replace").rstrip("\n")
            report.write(message + "\n")
            yield {"type": "log", "message": message}

        if timed_out:
            await kill_process_group(process)
            timeout_message = "[timeout] test command exceeded timeout_seconds"
            report.write(timeout_message + "\n")
            yield {"type": "log", "message": timeout_message}
            exit_code = process.returncode if process.returncode is not None else -1
        else:
            exit_code = await process.wait()

    await post_artifact(settings, run_id, "TEST_REPORT", "test-report.log", report_path, "text/plain")
    yield {"type": "result", "exitCode": exit_code}
