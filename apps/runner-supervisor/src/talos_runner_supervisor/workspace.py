# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

"""Workspace manager (Section 8.3/8.5): clone-or-fetch, git worktree + branch naming, copy filter."""

from __future__ import annotations

import fnmatch
import re
import shutil
import subprocess
from pathlib import Path

from talos_runner_supervisor.config import Settings

# Section 8.5: ".env files are never copied into worktrees (copy-filter honors talos.yaml
# ignore_paths plus a built-in .env*/*.pem/id_* filter)".
BUILT_IN_IGNORE_PATTERNS = [".env", ".env.*", "*.pem", "id_*"]


class WorkspaceError(RuntimeError):
    pass


def slugify(text: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    return slug[:50] or "task"


def branch_name_for(task_id: str, task_title: str) -> str:
    return f"agent/task-{task_id}-{slugify(task_title)}"


def run_dir_for(settings: Settings, project_slug: str, run_id: str) -> Path:
    return Path(settings.workspaces_root) / project_slug / "runs" / run_id


def prepare_workspace(
    settings: Settings,
    run_id: str,
    task_id: str,
    task_title: str,
    project_slug: str,
    repo_url: str,
    default_branch: str,
    ignore_paths: list[str] | None = None,
) -> tuple[str, str]:
    project_dir = Path(settings.workspaces_root) / project_slug
    repo_dir = project_dir / "repo"

    if not (repo_dir / ".git").exists():
        repo_dir.parent.mkdir(parents=True, exist_ok=True)
        _run_git(["clone", repo_url, str(repo_dir)])
    else:
        _run_git(["fetch", "origin"], cwd=repo_dir)

    branch_name = branch_name_for(task_id, task_title)
    run_dir = run_dir_for(settings, project_slug, run_id)
    worktree_dir = run_dir / "worktree"
    run_dir.mkdir(parents=True, exist_ok=True)

    base_ref = _resolve_base_ref(repo_dir, default_branch)
    _run_git(["worktree", "add", "-B", branch_name, str(worktree_dir), base_ref], cwd=repo_dir)

    apply_copy_filter(worktree_dir, ignore_paths or [])

    return str(worktree_dir), branch_name


def apply_copy_filter(worktree_dir: Path, ignore_paths: list[str]) -> None:
    patterns = [*BUILT_IN_IGNORE_PATTERNS, *ignore_paths]
    for path in sorted(worktree_dir.rglob("*"), key=lambda p: len(p.parts), reverse=True):
        if ".git" in path.parts:
            continue
        if not path.exists():
            continue
        if _matches_any(path.relative_to(worktree_dir), patterns):
            if path.is_dir():
                shutil.rmtree(path, ignore_errors=True)
            else:
                path.unlink(missing_ok=True)


def _matches_any(rel_path: Path, patterns: list[str]) -> bool:
    rel_str = str(rel_path)
    for part in rel_path.parts:
        for pattern in patterns:
            if fnmatch.fnmatch(part, pattern) or fnmatch.fnmatch(rel_str, pattern):
                return True
    return False


def _resolve_base_ref(repo_dir: Path, default_branch: str) -> str:
    remote_ref = f"origin/{default_branch}"
    check = subprocess.run(
        ["git", "rev-parse", "--verify", remote_ref],
        cwd=repo_dir,
        capture_output=True,
        text=True,
    )
    if check.returncode == 0:
        return remote_ref
    return default_branch


def _run_git(args: list[str], cwd: Path | None = None) -> str:
    result = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True)
    if result.returncode != 0:
        raise WorkspaceError(f"git {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout
