import os
import stat
from pathlib import Path

from talos_agent_adapter_spec.container import (
    ContainerConfig,
    build_docker_run_args,
    container_name,
    write_env_file,
)


def _config(**overrides) -> ContainerConfig:
    defaults = dict(
        image="workers/base-agent-runner:latest",
        workspace_volume="talos_workspaces",
        workspace_subpath="demo/runs/run1/worktree",
        provider_homes_volume="talos_provider_homes",
        provider_home_subpath="claude-code",
        network="talos_run_network",
    )
    defaults.update(overrides)
    return ContainerConfig(**defaults)


def test_container_name_is_deterministic_and_prefixed():
    assert container_name("run1") == "talos-run-run1"


def test_write_env_file_writes_key_value_lines_with_owner_only_permissions():
    path = write_env_file({"ANTHROPIC_API_KEY": "sk-secret", "FOO": "bar"})
    try:
        content = Path(path).read_text()
        assert "ANTHROPIC_API_KEY=sk-secret" in content
        assert "FOO=bar" in content
        mode = stat.S_IMODE(os.stat(path).st_mode)
        assert mode == stat.S_IRUSR | stat.S_IWUSR
    finally:
        os.unlink(path)


def test_build_docker_run_args_never_includes_docker_socket():
    args = build_docker_run_args("run1", _config(), "/tmp/env-file", ["echo", "hi"])
    joined = " ".join(args)
    assert "docker.sock" not in joined
    assert "-v" not in args  # no bind-mount flag at all -- only --mount with named volumes


def test_build_docker_run_args_mounts_only_the_runs_own_subpath():
    args = build_docker_run_args("run1", _config(), "/tmp/env-file", ["echo", "hi"])
    joined = " ".join(args)
    assert "source=talos_workspaces,target=/workspace,volume-subpath=demo/runs/run1/worktree" in joined
    assert "source=talos_provider_homes,target=/provider-home,volume-subpath=claude-code" in joined


def test_build_docker_run_args_drops_capabilities_and_sets_resource_limits():
    args = build_docker_run_args("run1", _config(memory_limit="2g", cpu_limit="2", pids_limit="512"),
                                  "/tmp/env-file", ["echo", "hi"])
    assert "--cap-drop" in args and args[args.index("--cap-drop") + 1] == "ALL"
    assert "--security-opt" in args and args[args.index("--security-opt") + 1] == "no-new-privileges"
    assert "--memory" in args and args[args.index("--memory") + 1] == "2g"
    assert "--cpus" in args and args[args.index("--cpus") + 1] == "2"
    assert "--pids-limit" in args and args[args.index("--pids-limit") + 1] == "512"


def test_build_docker_run_args_uses_env_file_not_dash_e_for_secrets():
    args = build_docker_run_args("run1", _config(), "/tmp/env-file", ["echo", "hi"])
    assert "--env-file" in args and args[args.index("--env-file") + 1] == "/tmp/env-file"
    assert "-e" not in args


def test_build_docker_run_args_uses_rm_and_deterministic_name():
    args = build_docker_run_args("run1", _config(), "/tmp/env-file", ["echo", "hi"])
    assert "--rm" in args
    assert "--name" in args and args[args.index("--name") + 1] == "talos-run-run1"


def test_build_docker_run_args_appends_image_then_command_in_order():
    args = build_docker_run_args("run1", _config(), "/tmp/env-file", ["echo", "hi", "there"])
    assert args[-4:] == ["workers/base-agent-runner:latest", "echo", "hi", "there"]
