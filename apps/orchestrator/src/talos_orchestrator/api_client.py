"""httpx client for talos-api's /internal/v1 namespace (Section 10.4). This is the *only* way the
orchestrator touches durable state -- it never opens a database connection (Section 6.2)."""

from __future__ import annotations

from typing import Any

import httpx

from talos_orchestrator.config import Settings


class ApiClient:
    def __init__(self, settings: Settings) -> None:
        self._client = httpx.AsyncClient(
            base_url=settings.api_base_url,
            headers={"X-Talos-Internal-Token": settings.internal_api_token},
            timeout=30.0,
        )

    async def aclose(self) -> None:
        await self._client.aclose()

    async def get_context(self, run_id: str) -> dict[str, Any]:
        response = await self._client.get(f"/internal/v1/runs/{run_id}/context")
        response.raise_for_status()
        return response.json()

    async def update_status(
        self,
        run_id: str,
        status: str,
        error_message: str | None = None,
        *,
        test_status: str | None = None,
        workspace_path: str | None = None,
        branch_name: str | None = None,
        prompt: str | None = None,
        summary: str | None = None,
        exit_code: int | None = None,
    ) -> dict[str, Any]:
        body: dict[str, Any] = {"status": status}
        if error_message is not None:
            body["errorMessage"] = error_message
        if test_status is not None:
            body["testStatus"] = test_status
        if workspace_path is not None:
            body["workspacePath"] = workspace_path
        if branch_name is not None:
            body["branchName"] = branch_name
        if prompt is not None:
            body["prompt"] = prompt
        if summary is not None:
            body["summary"] = summary
        if exit_code is not None:
            body["exitCode"] = exit_code
        response = await self._client.post(f"/internal/v1/runs/{run_id}/status", json=body)
        response.raise_for_status()
        return response.json()

    async def record_step(self, run_id: str, step_type: str, status: str, summary: str | None = None) -> dict[str, Any]:
        body: dict[str, Any] = {"stepType": step_type, "status": status}
        if summary is not None:
            body["summary"] = summary
        response = await self._client.post(f"/internal/v1/runs/{run_id}/steps", json=body)
        response.raise_for_status()
        return response.json()

    async def ingest_logs(self, run_id: str, entries: list[dict[str, Any]]) -> None:
        if not entries:
            return
        response = await self._client.post(f"/internal/v1/runs/{run_id}/logs", json={"entries": entries})
        response.raise_for_status()

    async def record_changes(
        self,
        run_id: str,
        files: list[dict[str, Any]],
        diff_artifact_ref: str | None = None,
        diff_patch: str | None = None,
    ) -> None:
        body: dict[str, Any] = {"files": files}
        if diff_artifact_ref is not None:
            body["diffArtifactRef"] = diff_artifact_ref
        if diff_patch is not None:
            body["diffPatch"] = diff_patch
        response = await self._client.post(f"/internal/v1/runs/{run_id}/changes", json=body)
        response.raise_for_status()

    async def get_git_token(self, run_id: str) -> dict[str, Any]:
        """Section 8.4's push credential flow (Phase 9). 409s unless the run is APPROVED."""
        response = await self._client.get(f"/internal/v1/runs/{run_id}/git-token")
        response.raise_for_status()
        return response.json()

    async def get_retention_candidates(self, max_age_days: int) -> list[dict[str, Any]]:
        """Phase 11 (Section 8.3): terminal runs older than max_age_days with no OPEN pull request --
        the runner supervisor's own workspace directories for these, not the database rows."""
        response = await self._client.get(
            "/internal/v1/runs/retention-candidates", params={"maxAgeDays": max_age_days}
        )
        response.raise_for_status()
        return response.json()["candidates"]

    async def create_pull_request(self, run_id: str, branch_name: str, commit_sha: str) -> dict[str, Any]:
        """Opens the PR and completes the run server-side (APPROVED -> COMPLETED, Section 8.2)."""
        response = await self._client.post(
            f"/internal/v1/runs/{run_id}/pull-request",
            json={"branchName": branch_name, "commitSha": commit_sha},
        )
        response.raise_for_status()
        return response.json()
