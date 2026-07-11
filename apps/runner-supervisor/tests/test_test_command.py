from dataclasses import replace
from pathlib import Path

from talos_runner_supervisor.test_command import stream_test_command


async def _collect(settings, run_id, workspace_path, command, timeout_seconds=10):
    return [item async for item in stream_test_command(settings, run_id, workspace_path, command, timeout_seconds)]


async def test_null_command_short_circuits_with_no_report_file(settings, tmp_path: Path):
    workspace = tmp_path / "workspaces" / "demo" / "runs" / "r1" / "worktree"
    workspace.mkdir(parents=True)

    items = await _collect(settings, "r1", str(workspace), None)

    assert items == [{"type": "result", "exitCode": 0}]
    assert not (workspace.parent / "artifacts" / "test-report.log").exists()


async def test_command_output_is_captured_to_report_file(settings, tmp_path: Path):
    workspace = tmp_path / "workspaces" / "demo" / "runs" / "r2" / "worktree"
    workspace.mkdir(parents=True)

    items = await _collect(settings, "r2", str(workspace), "printf 'line1\\nline2\\n'")

    result = items[-1]
    assert result == {"type": "result", "exitCode": 0}
    log_lines = [item["message"] for item in items if item["type"] == "log"]
    assert log_lines == ["line1", "line2"]

    report_path = workspace.parent / "artifacts" / "test-report.log"
    assert report_path.exists()
    assert report_path.read_text() == "line1\nline2\n"


async def test_failing_command_still_writes_report_and_exit_code(settings, tmp_path: Path):
    workspace = tmp_path / "workspaces" / "demo" / "runs" / "r3" / "worktree"
    workspace.mkdir(parents=True)

    items = await _collect(settings, "r3", str(workspace), "echo boom && exit 7")

    assert items[-1] == {"type": "result", "exitCode": 7}
    report_path = workspace.parent / "artifacts" / "test-report.log"
    assert report_path.read_text() == "boom\n"
