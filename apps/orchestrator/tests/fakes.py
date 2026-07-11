"""Lightweight test doubles for ApiClient/RunnerClient/RunLock. Hand-written rather than
unittest.mock.AsyncMock because execute_run/run_tests are async generators, which AsyncMock
doesn't model cleanly.
"""

from __future__ import annotations

from typing import Any, AsyncIterator


class FakeApiClient:
    def __init__(
        self,
        context: dict[str, Any],
        reject_status_updates_to: set[str] | None = None,
        git_token: dict[str, Any] | None = None,
        pull_request: dict[str, Any] | None = None,
    ) -> None:
        self.context = context
        self.status_calls: list[dict[str, Any]] = []
        self.step_calls: list[tuple[str, str, str | None]] = []
        self.log_entries: list[dict[str, Any]] = []
        self.changes_calls: list[tuple[list[dict[str, Any]], str | None, str | None]] = []
        self.git_token = git_token or {
            "token": "ghp_test-token",
            "authMode": "pat",
            "repoUrl": "git@github.com:org/demo.git",
            "defaultBranch": "main",
        }
        self.pull_request = pull_request or {"id": "pr1", "prNumber": 7, "url": "https://github.com/org/demo/pull/7"}
        self.pull_request_calls: list[tuple[str, str, str]] = []
        # Simulates the API's 422 ILLEGAL_RUN_TRANSITION when the run is already terminal (e.g. a
        # concurrent /cancel already moved it to CANCELLED before the pipeline tries to report FAILED).
        self._reject_status_updates_to = reject_status_updates_to or set()
        self.retention_candidates: list[dict[str, Any]] = []
        self.retention_candidates_calls: list[int] = []
        self.memory_results: list[dict[str, Any]] = []
        self.memory_search_calls: list[dict[str, Any]] = []
        self.memory_ingest_calls: list[dict[str, Any]] = []
        self.memory_call_order: list[str] = []

    async def get_context(self, run_id: str) -> dict[str, Any]:
        return self.context

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
        input_tokens: int | None = None,
        output_tokens: int | None = None,
        cost_usd: float | None = None,
        cost_model: str | None = None,
    ) -> dict[str, Any]:
        if status in self._reject_status_updates_to:
            raise RuntimeError(f"422 ILLEGAL_RUN_TRANSITION: run already terminal, cannot move to {status}")
        self.status_calls.append(
            {
                "status": status,
                "errorMessage": error_message,
                "testStatus": test_status,
                "workspacePath": workspace_path,
                "branchName": branch_name,
                "prompt": prompt,
                "summary": summary,
                "exitCode": exit_code,
                "inputTokens": input_tokens,
                "outputTokens": output_tokens,
                "costUsd": cost_usd,
                "costModel": cost_model,
            }
        )
        return {}

    async def record_step(self, run_id: str, step_type: str, status: str, summary: str | None = None) -> dict[str, Any]:
        self.step_calls.append((step_type, status, summary))
        return {}

    async def ingest_logs(self, run_id: str, entries: list[dict[str, Any]]) -> None:
        self.log_entries.extend(entries)

    async def record_changes(
        self,
        run_id: str,
        files: list[dict[str, Any]],
        diff_artifact_ref: str | None = None,
        diff_patch: str | None = None,
    ) -> None:
        self.changes_calls.append((files, diff_artifact_ref, diff_patch))

    async def get_git_token(self, run_id: str) -> dict[str, Any]:
        return self.git_token

    async def create_pull_request(self, run_id: str, branch_name: str, commit_sha: str) -> dict[str, Any]:
        self.pull_request_calls.append((run_id, branch_name, commit_sha))
        return self.pull_request

    async def get_retention_candidates(self, max_age_days: int) -> list[dict[str, Any]]:
        self.retention_candidates_calls.append(max_age_days)
        return self.retention_candidates

    async def search_memory(
        self,
        project_id: str,
        query: str,
        *,
        limit: int = 8,
        budget_chars: int = 4000,
    ) -> list[dict[str, Any]]:
        self.memory_call_order.append("search")
        self.memory_search_calls.append(
            {"project_id": project_id, "query": query, "limit": limit, "budget_chars": budget_chars}
        )
        return self.memory_results

    async def ingest_memory_document(
        self,
        project_id: str,
        *,
        source_ref: str,
        title: str,
        content: str,
    ) -> None:
        self.memory_call_order.append("ingest")
        self.memory_ingest_calls.append(
            {"project_id": project_id, "source_ref": source_ref, "title": title, "content": content}
        )


class FakeRunnerClient:
    def __init__(
        self,
        prepare_result: dict[str, Any],
        execute_events: list[dict[str, Any]],
        test_events: list[dict[str, Any]],
        diff_result: dict[str, Any],
        push_result: dict[str, Any] | None = None,
    ) -> None:
        self.prepare_result = prepare_result
        self.execute_events = execute_events
        self.test_events = test_events
        self.diff_result = diff_result
        self.push_result = push_result or {"pushed": True, "needsRebase": False, "commitSha": "abc123def"}
        self.stop_calls: list[str] = []
        self.push_calls: list[tuple[Any, ...]] = []
        self.execute_run_calls: list[dict[str, Any]] = []
        self.cleanup_calls: list[tuple[str, list[str], int | None]] = []
        self.cleanup_result: dict[str, Any] = {"deletedRunIds": []}

    async def prepare_workspace(self, *args: Any, **kwargs: Any) -> dict[str, Any]:
        return self.prepare_result

    async def execute_run(self, *args: Any, **kwargs: Any) -> AsyncIterator[dict[str, Any]]:
        self.execute_run_calls.append(kwargs)
        for event in self.execute_events:
            yield event

    async def run_tests(self, *args: Any, **kwargs: Any) -> AsyncIterator[dict[str, Any]]:
        for event in self.test_events:
            yield event

    async def capture_diff(self, *args: Any, **kwargs: Any) -> dict[str, Any]:
        return self.diff_result

    async def stop(self, run_id: str) -> None:
        self.stop_calls.append(run_id)

    async def push(
        self,
        run_id: str,
        workspace_path: str,
        branch_name: str,
        default_branch: str,
        commit_message: str,
        token: str,
        repo_url: str,
    ) -> dict[str, Any]:
        self.push_calls.append((run_id, workspace_path, branch_name, default_branch, commit_message, token, repo_url))
        return self.push_result

    async def cleanup(self, project_slug: str, run_ids: list[str], max_age_days: int | None = None) -> dict[str, Any]:
        self.cleanup_calls.append((project_slug, run_ids, max_age_days))
        return self.cleanup_result


class FakeRunLock:
    def __init__(self, acquire_result: bool = True) -> None:
        self.acquire_result = acquire_result
        self.acquired: list[tuple[str, str, str]] = []
        self.released: list[tuple[str, str, str]] = []

    async def acquire(self, project_id: str, base_branch: str, run_id: str, ttl_seconds: int) -> bool:
        self.acquired.append((project_id, base_branch, run_id))
        return self.acquire_result

    async def release(self, project_id: str, base_branch: str, run_id: str) -> None:
        self.released.append((project_id, base_branch, run_id))
