import subprocess
from pathlib import Path

import pytest

from talos_runner_supervisor.git_push import GitPushError, push
from talos_runner_supervisor.workspace import prepare_workspace


def _git(args: list[str], cwd: Path) -> subprocess.CompletedProcess:
    return subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, check=True)


def test_push_commitsUncommittedChanges_andPushesNewBranch(settings, origin_repo):
    workspace_path, branch_name = prepare_workspace(
        settings,
        run_id="run1",
        task_id="task1",
        task_title="Add hello endpoint",
        project_slug="demo",
        repo_url=str(origin_repo),
        default_branch="main",
    )
    (Path(workspace_path) / "new-file.txt").write_text("hello\n")

    result = push(
        Path(workspace_path),
        branch_name=branch_name,
        default_branch="main",
        commit_message="talos: Add hello endpoint (task task1, run run1)",
        token="unused-for-a-local-path-remote",
        repo_url=str(origin_repo),
    )

    assert result.pushed is True
    assert result.needs_rebase is False
    assert result.commit_sha is not None

    log = _git(["log", branch_name, "--format=%an <%ae> %s"], cwd=origin_repo).stdout
    assert "Talos Agent <talos@local>" in log
    assert "talos: Add hello endpoint" in log


def test_push_preservesAgentCommit_whenNothingLeftToStage(settings, origin_repo):
    workspace_path, branch_name = prepare_workspace(
        settings,
        run_id="run2",
        task_id="task2",
        task_title="Add hello endpoint",
        project_slug="demo",
        repo_url=str(origin_repo),
        default_branch="main",
    )
    (Path(workspace_path) / "agent-file.txt").write_text("agent work\n")
    _git(["add", "-A"], cwd=Path(workspace_path))
    _git(["-c", "user.name=Agent", "-c", "user.email=agent@local",
          "commit", "-m", "agent's own commit", "--author=Agent <agent@local>"], cwd=Path(workspace_path))
    head_before = _git(["rev-parse", "HEAD"], cwd=Path(workspace_path)).stdout.strip()

    result = push(
        Path(workspace_path),
        branch_name=branch_name,
        default_branch="main",
        commit_message="talos: Add hello endpoint (task task2, run run2)",
        token="unused-for-a-local-path-remote",
        repo_url=str(origin_repo),
    )

    assert result.pushed is True
    assert result.commit_sha == head_before

    log = _git(["log", branch_name, "-1", "--format=%an"], cwd=origin_repo).stdout.strip()
    assert log == "Agent"


def test_push_nonFastForwardRejection_setsNeedsRebase(settings, origin_repo):
    workspace_path, branch_name = prepare_workspace(
        settings,
        run_id="run3",
        task_id="task3",
        task_title="Add hello endpoint",
        project_slug="demo",
        repo_url=str(origin_repo),
        default_branch="main",
    )

    # Simulate the remote already holding a divergent commit under this branch name (e.g. a
    # stale prior push) -- our local HEAD does not descend from it, so the push must be rejected.
    _git(["checkout", "-b", branch_name], cwd=origin_repo)
    (origin_repo / "remote-only.txt").write_text("remote change\n")
    _git(["add", "-A"], cwd=origin_repo)
    _git(["commit", "-m", "remote-only commit"], cwd=origin_repo)
    _git(["checkout", "main"], cwd=origin_repo)

    (Path(workspace_path) / "local-file.txt").write_text("local change\n")

    result = push(
        Path(workspace_path),
        branch_name=branch_name,
        default_branch="main",
        commit_message="talos: Add hello endpoint (task task3, run run3)",
        token="unused-for-a-local-path-remote",
        repo_url=str(origin_repo),
    )

    assert result.pushed is False
    assert result.needs_rebase is True
    assert result.reason


def test_push_refusesToPushDefaultBranch(origin_repo):
    with pytest.raises(GitPushError):
        push(
            origin_repo,
            branch_name="main",
            default_branch="main",
            commit_message="x",
            token="x",
            repo_url=str(origin_repo),
        )
