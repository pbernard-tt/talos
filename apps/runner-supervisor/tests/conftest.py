import subprocess
from pathlib import Path

import pytest

from talos_runner_supervisor.config import Settings


def _git(args: list[str], cwd: Path) -> None:
    subprocess.run(["git", *args], cwd=cwd, check=True, capture_output=True, text=True)


@pytest.fixture
def origin_repo(tmp_path: Path) -> Path:
    repo = tmp_path / "origin"
    repo.mkdir()
    _git(["init", "--initial-branch=main"], repo)
    _git(["config", "user.email", "test@talos.local"], repo)
    _git(["config", "user.name", "Talos Test"], repo)

    (repo / "README.md").write_text("# Example project\n")
    (repo / ".env").write_text("SECRET=leaked\n")
    secrets_dir = repo / "secrets"
    secrets_dir.mkdir()
    (secrets_dir / "id_rsa").write_text("not-a-real-key\n")
    target_dir = repo / "target"
    target_dir.mkdir()
    (target_dir / "output.txt").write_text("build output\n")

    _git(["add", "-A"], repo)
    _git(["commit", "-m", "initial commit"], repo)
    return repo


@pytest.fixture
def settings(tmp_path: Path) -> Settings:
    return Settings(
        workspaces_root=str(tmp_path / "workspaces"),
        provider_homes_root=str(tmp_path / "provider-homes"),
        internal_api_token="",
        max_workspace_age_days=7,
        run_workspaces_volume="talos_workspaces",
        run_provider_homes_volume="talos_provider_homes",
        run_network="talos_run_network",
        run_memory_limit="1g",
        run_cpu_limit="1",
        run_pids_limit="256",
    )
