"""The run pipeline (Section 6.3): one class, one run at a time per message. Drives a run through
Section 8.2's state machine by calling the runner supervisor for workspace prep and adapter
execution and posting results back to talos-api's /internal/v1.
"""

from __future__ import annotations

import logging
from datetime import UTC, datetime
from typing import Any

from talos_orchestrator.adapters import capabilities_for
from talos_orchestrator.api_client import ApiClient
from talos_orchestrator.log_batcher import LogBatcher
from talos_orchestrator.locks import RunLock
from talos_orchestrator.prompt_assembler import assemble_prompt
from talos_orchestrator.runner_client import RunnerClient

logger = logging.getLogger(__name__)

_DEFAULT_TIMEOUT_SECONDS = 1800


class RunPipeline:
    def __init__(self, api_client: ApiClient, runner_client: RunnerClient, run_lock: RunLock) -> None:
        self._api_client = api_client
        self._runner_client = runner_client
        self._run_lock = run_lock

    async def handle_run_requested(self, payload: dict[str, Any]) -> None:
        run_id = payload["run_id"]
        task_id = payload["task_id"]
        agent_key = payload["agent_key"]
        auth_mode = payload["auth_mode"]

        context = await self._api_client.get_context(run_id)
        run = context["run"]

        # Crash recovery (Section 6.3): a redelivered message for a run no longer in QUEUED means
        # this orchestrator process died mid-run last time. The run is poisoned; fail it and move on.
        if run["status"] != "QUEUED":
            logger.warning("run %s redelivered but status is %s, not QUEUED -- marking ORPHANED_BY_RESTART", run_id, run["status"])
            await self._api_client.update_status(run_id, "FAILED", "ORPHANED_BY_RESTART")
            return

        task = context["task"]
        project = context["project"]
        active_config = context.get("activeConfig") or {}

        project_id = project["id"]
        project_slug = project["slug"]
        base_branch = project["defaultBranch"]

        try:
            timeout_seconds = capabilities_for(agent_key).default_timeout_seconds
        except Exception:
            timeout_seconds = _DEFAULT_TIMEOUT_SECONDS

        acquired = await self._run_lock.acquire(project_id, base_branch, run_id, ttl_seconds=timeout_seconds)
        if not acquired:
            logger.info("run %s rejected: lock held for %s:%s", run_id, project_id, base_branch)
            await self._api_client.update_status(run_id, "FAILED", "CONCURRENT_RUN")
            return

        try:
            await self._run(run_id, task, project, project_slug, active_config, agent_key, auth_mode, timeout_seconds)
        except Exception as exc:  # noqa: BLE001 -- Section 6.3: any exception fails the run, message is not retried
            logger.exception("run %s failed", run_id)
            try:
                await self._api_client.update_status(run_id, "FAILED", str(exc))
            except Exception:
                # The run may already be terminal (e.g. a concurrent /cancel already moved it to
                # CANCELLED while the agent was mid-execution -- stop() intentionally makes the
                # adapter report success=False, which _run() surfaces as this same exception path).
                # The API already holds the true final state; reporting FAILED here is best-effort.
                logger.warning("run %s: could not record FAILED (likely already terminal)", run_id)
        finally:
            await self._run_lock.release(project_id, base_branch, run_id)

    async def handle_cancel_requested(self, payload: dict[str, Any]) -> None:
        # The run row is already CANCELLED (the API set it before publishing this event) -- our
        # only job is to kill the process tree if we're the orchestrator currently running it.
        # runner_client.stop() is a no-op (404-swallowed) if this run isn't executing here.
        run_id = payload["run_id"]
        await self._runner_client.stop(run_id)

    async def _run(
        self,
        run_id: str,
        task: dict[str, Any],
        project: dict[str, Any],
        project_slug: str,
        active_config: dict[str, Any],
        agent_key: str,
        auth_mode: str,
        timeout_seconds: int,
    ) -> None:
        log_batcher = LogBatcher(self._api_client, run_id)

        # --- workspace prep -------------------------------------------------------------
        await self._api_client.update_status(run_id, "PREPARING_WORKSPACE")
        await self._api_client.record_step(run_id, "WORKSPACE", "RUNNING")

        ignore_paths = (active_config.get("context") or {}).get("ignore_paths", [])
        prep = await self._runner_client.prepare_workspace(
            run_id,
            task["id"],
            task["title"],
            project_slug,
            project["repoUrl"],
            project["defaultBranch"],
            ignore_paths=ignore_paths,
        )
        workspace_path = prep["workspacePath"]
        branch_name = prep["branchName"]

        await self._api_client.record_step(
            run_id, "WORKSPACE", "COMPLETED", summary=f"worktree ready at {workspace_path} on {branch_name}"
        )

        # --- agent execution --------------------------------------------------------------
        # CustomShellAdapter retains its documented Phase 6 command semantics so the deterministic
        # smoke flow remains executable. Every real coding adapter receives the Section 7.3 prompt.
        prompt = task.get("description") or "" if agent_key == "custom-shell" else assemble_prompt(
            task, project, active_config, workspace_path
        )
        await self._api_client.update_status(
            run_id, "RUNNING_AGENT", workspace_path=workspace_path, branch_name=branch_name, prompt=prompt
        )
        await self._api_client.record_step(run_id, "AGENT", "RUNNING")

        agent_result: dict[str, Any] | None = None
        async for event in self._runner_client.execute_run(
            run_id, agent_key, workspace_path, prompt, env={}, auth_mode=auth_mode, timeout_seconds=timeout_seconds
        ):
            if event.get("type") == "result":
                agent_result = event
            else:
                await log_batcher.add(
                    _stream_for(event.get("metadata") or {}), event.get("message", ""), event.get("timestamp") or _now_iso()
                )
        await log_batcher.flush()

        if agent_result is None or not agent_result.get("success"):
            await self._api_client.record_step(run_id, "AGENT", "FAILED", summary="agent execution did not succeed")
            raise RuntimeError((agent_result or {}).get("summary") or "agent execution did not succeed")
        await self._api_client.record_step(run_id, "AGENT", "COMPLETED")

        # --- tests ---------------------------------------------------------------------
        await self._api_client.update_status(run_id, "RUNNING_TESTS", exit_code=agent_result.get("exit_code"))
        await self._api_client.record_step(run_id, "TESTS", "RUNNING")

        test_command = (active_config.get("commands") or {}).get("test")
        test_exit_code = 0
        async for event in self._runner_client.run_tests(run_id, workspace_path, test_command, timeout_seconds):
            if event.get("type") == "result":
                test_exit_code = event.get("exitCode", 0)
            else:
                await log_batcher.add("STDOUT", event.get("message", ""), _now_iso())
        await log_batcher.flush()

        test_status = "NOT_RUN" if not test_command else ("PASSED" if test_exit_code == 0 else "FAILED")
        await self._api_client.record_step(
            run_id, "TESTS", "COMPLETED" if test_exit_code == 0 else "FAILED", summary=f"exit code {test_exit_code}"
        )

        # --- review / diff capture ------------------------------------------------------
        await self._api_client.update_status(run_id, "REVIEWING", test_status=test_status)
        diff = await self._runner_client.capture_diff(run_id, workspace_path)
        await self._api_client.record_changes(run_id, diff["files"], diff.get("diffArtifactPath"), diff.get("diff"))
        summary = f"{len(diff['files'])} file(s) changed"
        await self._api_client.record_step(run_id, "REVIEW", "COMPLETED", summary=summary)

        # --- waiting for human approval --------------------------------------------------
        await self._api_client.update_status(run_id, "WAITING_APPROVAL", summary=summary)


def _stream_for(metadata: dict[str, Any]) -> str:
    stream = metadata.get("stream")
    if stream == "stdout":
        return "STDOUT"
    if stream == "stderr":
        return "STDERR"
    return "SYSTEM"


def _now_iso() -> str:
    return datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%S.%fZ")
