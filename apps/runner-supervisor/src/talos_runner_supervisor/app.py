"""FastAPI app: talos-runner-supervisor HTTP surface (Section 6.4 / packages/contracts/runner-api.yaml)."""

from __future__ import annotations

import json
import shutil
import time
from pathlib import Path

from fastapi import Depends, FastAPI, HTTPException
from fastapi.responses import StreamingResponse

from talos_runner_supervisor.config import Settings, load_settings
from talos_runner_supervisor.diff_capture import capture_diff
from talos_runner_supervisor.execute import RunAlreadyExecutingError, execute_run
from talos_runner_supervisor.models import (
    CleanupRequest,
    CleanupResponse,
    DiffRequest,
    DiffResponse,
    ExecuteRunRequest,
    GitChange,
    PrepareWorkspaceRequest,
    PrepareWorkspaceResponse,
    RunTestsRequest,
)
from talos_runner_supervisor.run_registry import registry
from talos_runner_supervisor.test_command import stream_test_command
from talos_runner_supervisor.workspace import WorkspaceError, prepare_workspace

app = FastAPI(title="Talos Runner Supervisor")


def get_settings() -> Settings:
    return load_settings()


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/workspaces/prepare", response_model=PrepareWorkspaceResponse)
async def prepare(
    request: PrepareWorkspaceRequest, settings: Settings = Depends(get_settings)
) -> PrepareWorkspaceResponse:
    try:
        workspace_path, branch_name = prepare_workspace(
            settings,
            run_id=request.run_id,
            task_id=request.task_id,
            task_title=request.task_title,
            project_slug=request.project_slug,
            repo_url=request.repo_url,
            default_branch=request.default_branch,
            ignore_paths=request.ignore_paths,
        )
    except WorkspaceError as exc:
        raise HTTPException(status_code=422, detail={"error": {"code": "WORKSPACE_PREPARE_FAILED", "message": str(exc)}}) from exc
    return PrepareWorkspaceResponse(workspacePath=workspace_path, branchName=branch_name)


@app.post("/runs/{run_id}/execute")
async def execute(
    run_id: str, request: ExecuteRunRequest, settings: Settings = Depends(get_settings)
) -> StreamingResponse:
    try:
        event_stream = await execute_run(
            settings,
            registry,
            run_id,
            adapter_key=request.adapter_key,
            workspace_path=request.workspace_path,
            prompt=request.prompt,
            env=request.env,
            auth_mode=request.auth_mode,
            timeout_seconds=request.timeout_seconds,
        )
    except RunAlreadyExecutingError as exc:
        raise HTTPException(
            status_code=409, detail={"error": {"code": "RUN_ALREADY_EXECUTING", "message": str(exc)}}
        ) from exc
    return StreamingResponse(event_stream, media_type="application/x-ndjson")


@app.post("/runs/{run_id}/tests")
async def run_tests(run_id: str, request: RunTestsRequest) -> StreamingResponse:
    async def ndjson() -> None:
        async for item in stream_test_command(request.workspace_path, request.command, request.timeout_seconds):
            yield json.dumps(item) + "\n"

    return StreamingResponse(ndjson(), media_type="application/x-ndjson")


@app.post("/runs/{run_id}/diff", response_model=DiffResponse)
async def diff(run_id: str, request: DiffRequest) -> DiffResponse:
    worktree_dir = Path(request.workspace_path)
    if not worktree_dir.exists():
        raise HTTPException(status_code=404, detail={"error": {"code": "WORKSPACE_NOT_FOUND", "message": str(worktree_dir)}})
    files, diff_text, diff_artifact_path = capture_diff(worktree_dir)
    return DiffResponse(
        files=[GitChange(filePath=f.file_path, changeType=f.change_type, additions=f.additions, deletions=f.deletions) for f in files],
        diff=diff_text,
        diffArtifactPath=diff_artifact_path,
    )


@app.post("/runs/{run_id}/stop", status_code=204)
async def stop(run_id: str) -> None:
    adapter = registry.get(run_id)
    if adapter is None:
        raise HTTPException(status_code=404, detail={"error": {"code": "RUN_NOT_EXECUTING", "message": run_id}})
    await adapter.stop()


@app.post("/workspaces/cleanup", response_model=CleanupResponse)
async def cleanup(
    request: CleanupRequest | None = None, settings: Settings = Depends(get_settings)
) -> CleanupResponse:
    request = request or CleanupRequest()
    max_age_days = request.max_age_days if request.max_age_days is not None else settings.max_workspace_age_days
    cutoff = time.time() - max_age_days * 86400

    deleted: list[str] = []
    if not request.project_slug or not request.run_ids:
        return CleanupResponse(deletedRunIds=deleted)

    runs_root = Path(settings.workspaces_root) / request.project_slug / "runs"
    for run_id in request.run_ids:
        run_dir = runs_root / run_id
        if not run_dir.exists():
            continue
        if run_dir.stat().st_mtime <= cutoff:
            shutil.rmtree(run_dir, ignore_errors=True)
            deleted.append(run_id)

    return CleanupResponse(deletedRunIds=deleted)
