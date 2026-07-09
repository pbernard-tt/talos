import subprocess
from pathlib import Path

from talos_runner_supervisor.workspace import prepare_workspace


def _current_branch(worktree: str) -> str:
    result = subprocess.run(
        ["git", "branch", "--show-current"], cwd=worktree, capture_output=True, text=True, check=True
    )
    return result.stdout.strip()


def test_prepare_workspace_creates_worktree_on_named_branch(settings, origin_repo):
    workspace_path, branch_name = prepare_workspace(
        settings,
        run_id="run1",
        task_id="task1",
        task_title="Add hello endpoint",
        project_slug="demo",
        repo_url=str(origin_repo),
        default_branch="main",
    )

    assert branch_name == "agent/task-task1-add-hello-endpoint"
    assert Path(workspace_path).exists()
    assert _current_branch(workspace_path) == branch_name
    assert (Path(workspace_path) / "README.md").exists()


def test_prepare_workspace_copy_filter_deletes_builtin_patterns(settings, origin_repo):
    workspace_path, _ = prepare_workspace(
        settings,
        run_id="run2",
        task_id="task1",
        task_title="Add hello endpoint",
        project_slug="demo",
        repo_url=str(origin_repo),
        default_branch="main",
    )

    assert not (Path(workspace_path) / ".env").exists()
    assert not (Path(workspace_path) / "secrets" / "id_rsa").exists()
    assert (Path(workspace_path) / "README.md").exists()


def test_prepare_workspace_respects_configured_ignore_paths(settings, origin_repo):
    workspace_path, _ = prepare_workspace(
        settings,
        run_id="run3",
        task_id="task1",
        task_title="Add hello endpoint",
        project_slug="demo",
        repo_url=str(origin_repo),
        default_branch="main",
        ignore_paths=["target"],
    )

    assert not (Path(workspace_path) / "target").exists()


def test_prepare_workspace_second_run_fetches_existing_clone(settings, origin_repo):
    prepare_workspace(
        settings,
        run_id="run4",
        task_id="task1",
        task_title="First task",
        project_slug="demo",
        repo_url=str(origin_repo),
        default_branch="main",
    )
    workspace_path_2, branch_name_2 = prepare_workspace(
        settings,
        run_id="run5",
        task_id="task2",
        task_title="Second task",
        project_slug="demo",
        repo_url=str(origin_repo),
        default_branch="main",
    )

    repo_dir = Path(settings.workspaces_root) / "demo" / "repo"
    assert repo_dir.exists()
    assert Path(workspace_path_2).exists()
    assert branch_name_2 == "agent/task-task2-second-task"
