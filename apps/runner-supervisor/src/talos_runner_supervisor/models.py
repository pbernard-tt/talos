"""Pydantic models mirroring packages/contracts/runner-api.yaml."""

from __future__ import annotations

from pydantic import BaseModel, Field


class PrepareWorkspaceRequest(BaseModel):
    run_id: str = Field(alias="runId")
    task_id: str = Field(alias="taskId")
    task_title: str = Field(alias="taskTitle")
    project_slug: str = Field(alias="projectSlug")
    repo_url: str = Field(alias="repoUrl")
    default_branch: str = Field(alias="defaultBranch")
    ignore_paths: list[str] = Field(default_factory=list, alias="ignorePaths")

    model_config = {"populate_by_name": True}


class PrepareWorkspaceResponse(BaseModel):
    workspace_path: str = Field(alias="workspacePath")
    branch_name: str = Field(alias="branchName")

    model_config = {"populate_by_name": True}


class ExecuteRunRequest(BaseModel):
    adapter_key: str = Field(alias="adapterKey")
    workspace_path: str = Field(alias="workspacePath")
    prompt: str
    env: dict[str, str] = Field(default_factory=dict)
    auth_mode: str = Field(alias="authMode")
    timeout_seconds: int = Field(alias="timeoutSeconds")

    model_config = {"populate_by_name": True}


class RunTestsRequest(BaseModel):
    workspace_path: str = Field(alias="workspacePath")
    command: str | None = None
    timeout_seconds: int = Field(alias="timeoutSeconds")

    model_config = {"populate_by_name": True}


class DiffRequest(BaseModel):
    workspace_path: str = Field(alias="workspacePath")

    model_config = {"populate_by_name": True}


class GitChange(BaseModel):
    file_path: str = Field(alias="filePath")
    change_type: str = Field(alias="changeType")
    additions: int
    deletions: int

    model_config = {"populate_by_name": True}


class DiffResponse(BaseModel):
    files: list[GitChange]
    diff: str
    diff_artifact_path: str = Field(alias="diffArtifactPath")

    model_config = {"populate_by_name": True}


class CleanupRequest(BaseModel):
    project_slug: str | None = Field(default=None, alias="projectSlug")
    run_ids: list[str] = Field(default_factory=list, alias="runIds")
    max_age_days: int | None = Field(default=None, alias="maxAgeDays")

    model_config = {"populate_by_name": True}


class CleanupResponse(BaseModel):
    deleted_run_ids: list[str] = Field(alias="deletedRunIds")

    model_config = {"populate_by_name": True}
