"""httpx client for the runner supervisor's HTTP API (Section 6.4 / packages/contracts/runner-api.yaml)."""

from __future__ import annotations

import json
from typing import Any, AsyncIterator

import httpx

from talos_orchestrator.config import Settings


class RunnerClient:
    def __init__(self, settings: Settings) -> None:
        self._client = httpx.AsyncClient(base_url=settings.runner_base_url, timeout=None)

    async def aclose(self) -> None:
        await self._client.aclose()

    async def prepare_workspace(
        self,
        run_id: str,
        task_id: str,
        task_title: str,
        project_slug: str,
        repo_url: str,
        default_branch: str,
        ignore_paths: list[str] | None = None,
    ) -> dict[str, Any]:
        response = await self._client.post(
            "/workspaces/prepare",
            json={
                "runId": run_id,
                "taskId": task_id,
                "taskTitle": task_title,
                "projectSlug": project_slug,
                "repoUrl": repo_url,
                "defaultBranch": default_branch,
                "ignorePaths": ignore_paths or [],
            },
        )
        response.raise_for_status()
        return response.json()

    async def execute_run(
        self,
        run_id: str,
        adapter_key: str,
        workspace_path: str,
        prompt: str,
        env: dict[str, str],
        auth_mode: str,
        timeout_seconds: int,
    ) -> AsyncIterator[dict[str, Any]]:
        body = {
            "adapterKey": adapter_key,
            "workspacePath": workspace_path,
            "prompt": prompt,
            "env": env,
            "authMode": auth_mode,
            "timeoutSeconds": timeout_seconds,
        }
        async with self._client.stream("POST", f"/runs/{run_id}/execute", json=body) as response:
            response.raise_for_status()
            async for line in response.aiter_lines():
                if line.strip():
                    yield json.loads(line)

    async def run_tests(
        self, run_id: str, workspace_path: str, command: str | None, timeout_seconds: int
    ) -> AsyncIterator[dict[str, Any]]:
        body = {"workspacePath": workspace_path, "command": command, "timeoutSeconds": timeout_seconds}
        async with self._client.stream("POST", f"/runs/{run_id}/tests", json=body) as response:
            response.raise_for_status()
            async for line in response.aiter_lines():
                if line.strip():
                    yield json.loads(line)

    async def capture_diff(self, run_id: str, workspace_path: str) -> dict[str, Any]:
        response = await self._client.post(f"/runs/{run_id}/diff", json={"workspacePath": workspace_path})
        response.raise_for_status()
        return response.json()

    async def stop(self, run_id: str) -> None:
        response = await self._client.post(f"/runs/{run_id}/stop")
        if response.status_code == 404:
            return  # already finished or never started -- nothing to stop
        response.raise_for_status()

    async def cleanup(self, project_slug: str, run_ids: list[str], max_age_days: int | None = None) -> dict[str, Any]:
        body: dict[str, Any] = {"projectSlug": project_slug, "runIds": run_ids}
        if max_age_days is not None:
            body["maxAgeDays"] = max_age_days
        response = await self._client.post("/workspaces/cleanup", json=body)
        response.raise_for_status()
        return response.json()
