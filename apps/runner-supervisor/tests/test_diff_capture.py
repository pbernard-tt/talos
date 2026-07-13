# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

import subprocess
from pathlib import Path

from talos_runner_supervisor.diff_capture import capture_diff


def _git(args: list[str], cwd: Path) -> None:
    subprocess.run(["git", *args], cwd=cwd, check=True, capture_output=True, text=True)


def _make_repo(root: Path) -> Path:
    repo = root / "run" / "worktree"
    repo.mkdir(parents=True)
    _git(["init", "--initial-branch=main"], repo)
    _git(["config", "user.email", "test@talos.local"], repo)
    _git(["config", "user.name", "Talos Test"], repo)
    (repo / "existing.txt").write_text("line one\n")
    (repo / "to-delete.txt").write_text("bye\n")
    _git(["add", "-A"], repo)
    _git(["commit", "-m", "baseline"], repo)
    return repo


def test_capture_diff_reports_added_modified_deleted_files(tmp_path):
    worktree = _make_repo(tmp_path)

    (worktree / "existing.txt").write_text("line one\nline two\n")
    (worktree / "new-file.txt").write_text("brand new\n")
    (worktree / "to-delete.txt").unlink()

    files, diff_text, diff_artifact_path = capture_diff(worktree)

    by_path = {f.file_path: f for f in files}
    assert by_path["existing.txt"].change_type == "MODIFIED"
    assert by_path["existing.txt"].additions == 1
    assert by_path["new-file.txt"].change_type == "ADDED"
    assert by_path["to-delete.txt"].change_type == "DELETED"

    assert "new-file.txt" in diff_text
    assert Path(diff_artifact_path).exists()
    assert Path(diff_artifact_path).read_text() == diff_text
    assert Path(diff_artifact_path).parent == worktree.parent / "artifacts"


def test_capture_diff_no_changes_returns_empty(tmp_path):
    worktree = _make_repo(tmp_path)

    files, diff_text, _ = capture_diff(worktree)

    assert files == []
    assert diff_text == ""
