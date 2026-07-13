# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

"""Diff capture (Section 8.1 step 8 / 8.3): changed-file summary + unified diff of a workspace's
uncommitted changes (agents do not commit -- Section 8.4), plus the diff.patch artifact.
"""

from __future__ import annotations

import subprocess
from dataclasses import dataclass
from pathlib import Path


@dataclass
class ChangedFile:
    file_path: str
    change_type: str  # ADDED | MODIFIED | DELETED | RENAMED
    additions: int
    deletions: int


def capture_diff(worktree_dir: Path) -> tuple[list[ChangedFile], str, str]:
    """Returns (files, unified diff text, diff.patch artifact path)."""
    # Intent-to-add only the untracked files: `git diff` already shows unstaged modifications and
    # deletions on its own. `git add -A` would additionally *stage* deletions, which removes them
    # from the unstaged diff entirely (index and working tree would agree the file is gone) --
    # exactly the bug this narrower form avoids.
    untracked = _run_git(["ls-files", "--others", "--exclude-standard"], cwd=worktree_dir)
    untracked_files = [line for line in untracked.splitlines() if line.strip()]
    if untracked_files:
        _run_git(["add", "-N", "--", *untracked_files], cwd=worktree_dir)

    numstat = _run_git(["diff", "--numstat"], cwd=worktree_dir)
    name_status = _run_git(["diff", "--name-status"], cwd=worktree_dir)
    diff_text = _run_git(["diff"], cwd=worktree_dir)

    files = _parse_changes(numstat, name_status)

    artifacts_dir = worktree_dir.parent / "artifacts"
    artifacts_dir.mkdir(parents=True, exist_ok=True)
    diff_path = artifacts_dir / "diff.patch"
    diff_path.write_text(diff_text)

    return files, diff_text, str(diff_path)


def _parse_changes(numstat: str, name_status: str) -> list[ChangedFile]:
    change_types: dict[str, str] = {}
    for line in name_status.splitlines():
        if not line.strip():
            continue
        parts = line.split("\t")
        code = parts[0]
        if code.startswith("R"):
            change_types[parts[2]] = "RENAMED"
        elif code.startswith("A"):
            change_types[parts[1]] = "ADDED"
        elif code.startswith("D"):
            change_types[parts[1]] = "DELETED"
        else:
            change_types[parts[1]] = "MODIFIED"

    files: list[ChangedFile] = []
    for line in numstat.splitlines():
        if not line.strip():
            continue
        additions_raw, deletions_raw, path = line.split("\t", 2)
        if " => " in path:
            path = path.split(" => ")[-1].rstrip("}").split("{")[-1]
        additions = 0 if additions_raw == "-" else int(additions_raw)
        deletions = 0 if deletions_raw == "-" else int(deletions_raw)
        change_type = change_types.get(path, "MODIFIED")
        files.append(ChangedFile(path, change_type, additions, deletions))
    return files


def _run_git(args: list[str], cwd: Path) -> str:
    result = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout
