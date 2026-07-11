"""Git push (Section 8.4): stages, commits (preserving anything the agent already committed), and
pushes the run's branch after approval. The token is delivered only as a transient GIT_ASKPASS
environment variable for the single push subprocess call -- never embedded in the remote URL,
never written into the workspace, never logged (Section 8.4's explicit credential-handling rules).
"""

from __future__ import annotations

import os
import stat
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path

_TALOS_AUTHOR = "Talos Agent <talos@local>"
# The runner has no global git identity (and must not depend on one); set the committer
# explicitly on the commit invocation itself.
_TALOS_IDENT = ["-c", "user.name=Talos Agent", "-c", "user.email=talos@local"]
_NEEDS_REBASE_MARKERS = ("non-fast-forward", "rejected", "fetch first", "stale info")


class GitPushError(RuntimeError):
    pass


@dataclass
class PushResult:
    pushed: bool
    needs_rebase: bool
    commit_sha: str | None
    reason: str | None = None


def push(
    worktree_dir: Path,
    branch_name: str,
    default_branch: str,
    commit_message: str,
    token: str,
    repo_url: str,
) -> PushResult:
    # Defensive: branch_name is always agent/task-* by construction (workspace.py), but 8.4
    # requires this hard-blocked regardless -- never push to the default branch.
    if branch_name == default_branch:
        raise GitPushError(f"refusing to push to the default branch ({default_branch})")

    _run_git(["add", "-A"], cwd=worktree_dir)
    staged = _run_git(["diff", "--cached", "--name-only"], cwd=worktree_dir)
    if staged.strip():
        _run_git([*_TALOS_IDENT, "commit", f"--author={_TALOS_AUTHOR}", "-m", commit_message], cwd=worktree_dir)

    commit_sha = _run_git(["rev-parse", "HEAD"], cwd=worktree_dir).strip()

    askpass_path = _write_askpass_script()
    try:
        env = os.environ.copy()
        env["GIT_ASKPASS"] = str(askpass_path)
        env["TALOS_GIT_TOKEN"] = token
        env["GIT_TERMINAL_PROMPT"] = "0"
        result = subprocess.run(
            ["git", "push", repo_url, f"HEAD:refs/heads/{branch_name}"],
            cwd=worktree_dir,
            capture_output=True,
            text=True,
            env=env,
        )
    finally:
        askpass_path.unlink(missing_ok=True)

    if result.returncode != 0:
        stderr = result.stderr
        needs_rebase = any(marker in stderr for marker in _NEEDS_REBASE_MARKERS)
        return PushResult(pushed=False, needs_rebase=needs_rebase, commit_sha=commit_sha, reason=stderr.strip())

    return PushResult(pushed=True, needs_rebase=False, commit_sha=commit_sha)


def _write_askpass_script() -> Path:
    fd, path = tempfile.mkstemp(prefix="talos-git-askpass-", suffix=".sh")
    with os.fdopen(fd, "w") as f:
        f.write('#!/bin/sh\necho "$TALOS_GIT_TOKEN"\n')
    os.chmod(path, stat.S_IRWXU)
    return Path(path)


def _run_git(args: list[str], cwd: Path) -> str:
    result = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True)
    if result.returncode != 0:
        raise GitPushError(f"git {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout
