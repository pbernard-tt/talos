from fakes import FakeApiClient, FakeRunLock, FakeRunnerClient

from talos_orchestrator.config import Settings
from talos_orchestrator.pipeline import RunPipeline

SETTINGS = Settings(
    api_base_url="http://api",
    internal_api_token="",
    rabbitmq_url="amqp://x",
    redis_url="redis://x",
    runner_base_url="http://runner",
    max_active_runs=1,
    run_timeout_minutes=60,
    worker_image_base="workers/base-agent-runner:latest",
    worker_image_java="workers/java-runner:latest",
    worker_image_node="workers/node-runner:latest",
    worker_image_python="workers/python-runner:latest",
    retention_max_age_days=7,
    retention_interval_seconds=21600,
)

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


def _pipeline(
    context=None,
    execute_events=None,
    test_events=None,
    diff_result=None,
    prepare_result=None,
    acquire_result=True,
    git_token=None,
    pull_request=None,
    push_result=None,
):
    api_client = FakeApiClient(context or CONTEXT, git_token=git_token, pull_request=pull_request)
    runner_client = FakeRunnerClient(
        prepare_result or PREPARE_RESULT,
        execute_events if execute_events is not None else SUCCESSFUL_EXECUTE_EVENTS,
        test_events if test_events is not None else PASSING_TEST_EVENTS,
        diff_result or DIFF_RESULT,
        push_result=push_result,
    )
    run_lock = FakeRunLock(acquire_result=acquire_result)
    pipeline = RunPipeline(api_client, runner_client, run_lock, SETTINGS)
    return pipeline, api_client, runner_client, run_lock


APPROVED_CONTEXT = {
    "run": {"status": "APPROVED", "workspacePath": "/ws/demo/runs/run1/worktree",
            "branchName": "agent/task-task1-add-hello"},
    "task": {"id": "task1", "title": "Add hello", "description": "echo hi > new.txt"},
    "project": {"id": "proj1", "slug": "demo", "defaultBranch": "main", "repoUrl": "git@github.com:org/demo.git"},
    "activeConfig": {"commands": {"test": "echo testing"}, "context": {"ignore_paths": []}},
}

APPROVAL_DECIDED_PAYLOAD = {
    "approval_id": "approval1",
    "run_id": "run1",
    "status": "APPROVED",
    "decided_by": "user1",
}


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
    assert api_client.changes_calls == [(DIFF_RESULT["files"], DIFF_RESULT["diffArtifactPath"], DIFF_RESULT["diff"])]
    assert any(step[0] == "WORKSPACE" and step[1] == "COMPLETED" for step in api_client.step_calls)
    assert any(step[0] == "AGENT" and step[1] == "COMPLETED" for step in api_client.step_calls)
    assert any(step[0] == "TESTS" and step[1] == "COMPLETED" for step in api_client.step_calls)
    assert any(step[0] == "REVIEW" and step[1] == "COMPLETED" for step in api_client.step_calls)
    assert api_client.log_entries  # the "hi" log line was batched and flushed


async def test_execute_run_resolves_container_image_from_project_stack_type():
    context = {**CONTEXT, "project": {**CONTEXT["project"], "stackType": "python"}}
    pipeline, api_client, runner_client, run_lock = _pipeline(context=context)

    await pipeline.handle_run_requested(REQUEST_PAYLOAD)

    assert runner_client.execute_run_calls[-1]["container_image"] == "workers/python-runner:latest"


async def test_execute_run_falls_back_to_base_image_for_unknown_stack_type():
    context = {**CONTEXT, "project": {**CONTEXT["project"], "stackType": "rust"}}
    pipeline, api_client, runner_client, run_lock = _pipeline(context=context)

    await pipeline.handle_run_requested(REQUEST_PAYLOAD)

    assert runner_client.execute_run_calls[-1]["container_image"] == "workers/base-agent-runner:latest"


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


async def test_claude_run_injects_project_memory_when_enabled():
    payload = {**REQUEST_PAYLOAD, "agent_key": "claude-code"}
    context = {
        **CONTEXT,
        "activeConfig": {
            "commands": {"test": "echo testing"},
            "memory": {"enabled": True, "prompt_budget_chars": 1234},
            "context": {},
        },
    }
    pipeline, api_client, _, _ = _pipeline(context=context)
    api_client.memory_results = [
        {"title": "Prior run", "sourceRef": "run-1", "content": "Prefer the existing AccountService."}
    ]

    await pipeline.handle_run_requested(payload)

    running = next(call for call in api_client.status_calls if call["status"] == "RUNNING_AGENT")
    assert "Relevant project memory" in running["prompt"]
    assert "Prefer the existing AccountService." in running["prompt"]
    assert api_client.memory_search_calls == [
        {"project_id": "proj1", "query": "Add hello\necho hi > new.txt", "limit": 8, "budget_chars": 1234}
    ]


async def test_claude_run_ingests_context_docs_before_memory_search(tmp_path):
    (tmp_path / "docs").mkdir()
    (tmp_path / "docs" / "architecture.md").write_text("Use AccountService for invoices.")
    payload = {**REQUEST_PAYLOAD, "agent_key": "claude-code"}
    context = {
        **CONTEXT,
        "activeConfig": {
            "commands": {"test": "echo testing"},
            "context": {"docs": ["docs/architecture.md"]},
            "memory": {"enabled": True},
        },
    }
    pipeline, api_client, _, _ = _pipeline(
        context=context,
        prepare_result={"workspacePath": str(tmp_path), "branchName": "agent/task-task1-add-hello"},
    )

    await pipeline.handle_run_requested(payload)

    assert api_client.memory_ingest_calls == [
        {
            "project_id": "proj1",
            "source_ref": "docs/architecture.md",
            "title": "context docs: docs/architecture.md",
            "content": "Use AccountService for invoices.",
        }
    ]
    assert api_client.memory_call_order == ["ingest", "search"]
    assert api_client.memory_search_calls


async def test_memory_disabled_keeps_pre_phase_13_prompt_byte_identical():
    payload = {**REQUEST_PAYLOAD, "agent_key": "claude-code"}
    context = {
        **CONTEXT,
        "activeConfig": {
            "commands": {"test": "echo testing"},
            "rules": {"forbidden": [".env"]},
            "context": {},
            "memory": {"enabled": False},
        },
    }
    pipeline, api_client, _, _ = _pipeline(context=context)

    await pipeline.handle_run_requested(payload)

    running = next(call for call in api_client.status_calls if call["status"] == "RUNNING_AGENT")
    assert running["prompt"] == (
        "You are working in an isolated branch of demo. Do not modify files matching: .env. "
        "Do not run destructive commands. Stop when the task is complete.\n\n"
        "Task title: Add hello\nTask description:\necho hi > new.txt\n\n"
        "Make the necessary code changes. Do not commit; Talos handles commits."
    )
    assert api_client.memory_search_calls == []
    assert api_client.memory_ingest_calls == []


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
    pipeline = RunPipeline(api_client, runner_client, run_lock, SETTINGS)

    await pipeline.handle_run_requested(REQUEST_PAYLOAD)  # must not raise

    assert run_lock.released == [("proj1", "main", "run1")]


async def test_cancel_forwards_stop_to_runner():
    pipeline, api_client, runner_client, run_lock = _pipeline()

    await pipeline.handle_cancel_requested({"run_id": "run1"})

    assert runner_client.stop_calls == ["run1"]


async def test_approval_decided_rejected_status_is_a_noop():
    pipeline, api_client, runner_client, run_lock = _pipeline(context=APPROVED_CONTEXT)

    await pipeline.handle_approval_decided({**APPROVAL_DECIDED_PAYLOAD, "status": "REJECTED"})

    assert runner_client.push_calls == []
    assert api_client.pull_request_calls == []


async def test_approval_decided_non_approved_run_status_skips_push():
    """Race guard mirroring the QUEUED check in handle_run_requested: the run may no longer be
    APPROVED by the time this event is processed."""
    context = {**APPROVED_CONTEXT, "run": {**APPROVED_CONTEXT["run"], "status": "CANCELLED"}}
    pipeline, api_client, runner_client, run_lock = _pipeline(context=context)

    await pipeline.handle_approval_decided(APPROVAL_DECIDED_PAYLOAD)

    assert runner_client.push_calls == []
    assert api_client.pull_request_calls == []
    assert api_client.status_calls == []


async def test_approval_decided_pushesAndOpensPr_recordingPushAndPrSteps():
    pipeline, api_client, runner_client, run_lock = _pipeline(context=APPROVED_CONTEXT)

    await pipeline.handle_approval_decided(APPROVAL_DECIDED_PAYLOAD)

    assert runner_client.push_calls == [
        (
            "run1",
            "/ws/demo/runs/run1/worktree",
            "agent/task-task1-add-hello",
            "main",
            "talos: Add hello (task task1, run run1)",
            "ghp_test-token",
            "git@github.com:org/demo.git",
        )
    ]
    assert api_client.pull_request_calls == [("run1", "agent/task-task1-add-hello", "abc123def")]
    assert ("PUSH", "RUNNING", None) in api_client.step_calls
    assert any(step[0] == "PUSH" and step[1] == "COMPLETED" for step in api_client.step_calls)
    assert ("PR", "RUNNING", None) in api_client.step_calls
    assert any(
        step[0] == "PR" and step[1] == "COMPLETED" and step[2] == "https://github.com/org/demo/pull/7"
        for step in api_client.step_calls
    )
    # PullRequestService completes the run server-side -- the orchestrator never calls
    # /internal/v1/runs/{id}/status itself on the happy path.
    assert api_client.status_calls == []


async def test_approval_decided_nonFastForwardPush_flagsNeedsRebase():
    push_result = {"pushed": False, "needsRebase": True, "commitSha": "abc123def", "reason": "! [rejected] (non-fast-forward)"}
    pipeline, api_client, runner_client, run_lock = _pipeline(context=APPROVED_CONTEXT, push_result=push_result)

    await pipeline.handle_approval_decided(APPROVAL_DECIDED_PAYLOAD)

    assert api_client.pull_request_calls == []
    assert ("PUSH", "FAILED", "! [rejected] (non-fast-forward)") in api_client.step_calls
    assert api_client.status_calls == [
        {
            "status": "FAILED",
            "errorMessage": "NEEDS_REBASE: ! [rejected] (non-fast-forward)",
            "testStatus": None,
            "workspacePath": None,
            "branchName": None,
            "prompt": None,
            "summary": None,
            "exitCode": None,
        }
    ]
