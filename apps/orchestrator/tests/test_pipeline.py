from fakes import FakeApiClient, FakeRunLock, FakeRunnerClient

from talos_orchestrator.pipeline import RunPipeline

CONTEXT = {
    "run": {"status": "QUEUED"},
    "task": {"id": "task1", "title": "Add hello", "description": "echo hi > new.txt"},
    "project": {"id": "proj1", "slug": "demo", "defaultBranch": "main", "repoUrl": "git@github.com:org/demo.git"},
    "activeConfig": {"commands": {"test": "echo testing"}, "context": {"ignore_paths": []}},
}

PREPARE_RESULT = {"workspacePath": "/ws/demo/runs/run1/worktree", "branchName": "agent/task-task1-add-hello"}

SUCCESSFUL_EXECUTE_EVENTS = [
    {"type": "log", "message": "hi", "timestamp": "2026-07-09T12:00:00Z", "metadata": {"stream": "stdout"}},
    {"type": "result", "exit_code": 0, "success": True, "summary": None, "raw_output_path": "/ph/transcript.txt"},
]

PASSING_TEST_EVENTS = [
    {"type": "log", "message": "test ok"},
    {"type": "result", "exitCode": 0},
]

DIFF_RESULT = {
    "files": [{"filePath": "new.txt", "changeType": "ADDED", "additions": 1, "deletions": 0}],
    "diff": "diff --git a/new.txt ...",
    "diffArtifactPath": "/ws/demo/runs/run1/artifacts/diff.patch",
}

REQUEST_PAYLOAD = {
    "run_id": "run1",
    "task_id": "task1",
    "project_id": "proj1",
    "agent_key": "custom-shell",
    "auth_mode": "api_key",
}


def _pipeline(context=None, execute_events=None, test_events=None, diff_result=None, acquire_result=True):
    api_client = FakeApiClient(context or CONTEXT)
    runner_client = FakeRunnerClient(
        PREPARE_RESULT,
        execute_events if execute_events is not None else SUCCESSFUL_EXECUTE_EVENTS,
        test_events if test_events is not None else PASSING_TEST_EVENTS,
        diff_result or DIFF_RESULT,
    )
    run_lock = FakeRunLock(acquire_result=acquire_result)
    pipeline = RunPipeline(api_client, runner_client, run_lock)
    return pipeline, api_client, runner_client, run_lock


async def test_happy_path_walks_all_statuses_and_releases_lock():
    pipeline, api_client, runner_client, run_lock = _pipeline()

    await pipeline.handle_run_requested(REQUEST_PAYLOAD)

    assert [c["status"] for c in api_client.status_calls] == [
        "PREPARING_WORKSPACE",
        "RUNNING_AGENT",
        "RUNNING_TESTS",
        "REVIEWING",
        "WAITING_APPROVAL",
    ]
    assert run_lock.acquired == [("proj1", "main", "run1")]
    assert run_lock.released == [("proj1", "main", "run1")]
    assert api_client.changes_calls == [(DIFF_RESULT["files"], DIFF_RESULT["diffArtifactPath"])]
    assert any(step[0] == "WORKSPACE" and step[1] == "COMPLETED" for step in api_client.step_calls)
    assert any(step[0] == "AGENT" and step[1] == "COMPLETED" for step in api_client.step_calls)
    assert any(step[0] == "TESTS" and step[1] == "COMPLETED" for step in api_client.step_calls)
    assert any(step[0] == "REVIEW" and step[1] == "COMPLETED" for step in api_client.step_calls)
    assert api_client.log_entries  # the "hi" log line was batched and flushed


async def test_claude_run_stores_assembled_prompt_for_audit():
    payload = {**REQUEST_PAYLOAD, "agent_key": "claude-code"}
    context = {
        **CONTEXT,
        "activeConfig": {"commands": {"test": "echo testing"}, "rules": {"forbidden": [".env"]}, "context": {}},
    }
    pipeline, api_client, _, _ = _pipeline(context=context)

    await pipeline.handle_run_requested(payload)

    running = next(call for call in api_client.status_calls if call["status"] == "RUNNING_AGENT")
    assert "isolated branch of demo" in running["prompt"]
    assert "Task title: Add hello" in running["prompt"]


async def test_lock_contention_rejects_concurrent_run():
    pipeline, api_client, runner_client, run_lock = _pipeline(acquire_result=False)

    await pipeline.handle_run_requested(REQUEST_PAYLOAD)

    assert api_client.status_calls == [
        {
            "status": "FAILED",
            "errorMessage": "CONCURRENT_RUN",
            "testStatus": None,
            "workspacePath": None,
            "branchName": None,
            "prompt": None,
            "summary": None,
            "exitCode": None,
        }
    ]
    assert run_lock.released == []  # never acquired, so nothing to release


async def test_agent_failure_marks_run_failed_and_still_releases_lock():
    failed_events = [
        {"type": "log", "message": "boom", "timestamp": "2026-07-09T12:00:00Z", "metadata": {"stream": "stderr"}},
        {"type": "result", "exit_code": 1, "success": False, "summary": "adapter blew up", "raw_output_path": "/x"},
    ]
    pipeline, api_client, runner_client, run_lock = _pipeline(execute_events=failed_events)

    await pipeline.handle_run_requested(REQUEST_PAYLOAD)

    statuses = [c["status"] for c in api_client.status_calls]
    assert statuses[-1] == "FAILED"
    assert api_client.status_calls[-1]["errorMessage"] == "adapter blew up"
    assert ("AGENT", "FAILED", "agent execution did not succeed") in api_client.step_calls
    assert run_lock.released == [("proj1", "main", "run1")]
    # never reached the tests/review stages
    assert "RUNNING_TESTS" not in statuses


async def test_poisoned_run_not_queued_marks_orphaned_and_skips_lock():
    context = {**CONTEXT, "run": {"status": "RUNNING_AGENT"}}
    pipeline, api_client, runner_client, run_lock = _pipeline(context=context)

    await pipeline.handle_run_requested(REQUEST_PAYLOAD)

    assert [c["status"] for c in api_client.status_calls] == ["FAILED"]
    assert api_client.status_calls[0]["errorMessage"] == "ORPHANED_BY_RESTART"
    assert run_lock.acquired == []
    assert run_lock.released == []


async def test_no_test_command_configured_reports_not_run():
    context = {**CONTEXT, "activeConfig": {"context": {"ignore_paths": []}}}  # no commands.test
    pipeline, api_client, runner_client, run_lock = _pipeline(context=context)

    await pipeline.handle_run_requested(REQUEST_PAYLOAD)

    reviewing_call = next(c for c in api_client.status_calls if c["status"] == "REVIEWING")
    assert reviewing_call["testStatus"] == "NOT_RUN"


async def test_agent_failure_after_run_already_cancelled_does_not_raise():
    """Reproduces a live-verified race: /cancel moves the run to CANCELLED (terminal) while the
    agent is mid-execution; stop() makes execute_run's result report success=False, which _run()
    turns into an exception. Reporting FAILED then hits the API's terminal-state guard (422) --
    the pipeline must swallow that, not crash the message handler."""
    stopped_events = [
        {"type": "log", "message": "killed", "timestamp": "2026-07-09T12:00:00Z", "metadata": {"stream": "stderr"}},
        {"type": "result", "exit_code": -1, "success": False, "summary": "timeout", "raw_output_path": "/x"},
    ]
    api_client = FakeApiClient(CONTEXT, reject_status_updates_to={"FAILED"})
    runner_client = FakeRunnerClient(PREPARE_RESULT, stopped_events, PASSING_TEST_EVENTS, DIFF_RESULT)
    run_lock = FakeRunLock()
    pipeline = RunPipeline(api_client, runner_client, run_lock)

    await pipeline.handle_run_requested(REQUEST_PAYLOAD)  # must not raise

    assert run_lock.released == [("proj1", "main", "run1")]


async def test_cancel_forwards_stop_to_runner():
    pipeline, api_client, runner_client, run_lock = _pipeline()

    await pipeline.handle_cancel_requested({"run_id": "run1"})

    assert runner_client.stop_calls == ["run1"]
