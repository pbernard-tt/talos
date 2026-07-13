# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

from fakes import FakeApiClient, FakeRunnerClient

from talos_orchestrator.retention import run_once

CONTEXT = {
    "run": {"status": "QUEUED"},
    "task": {"id": "task1", "title": "x", "description": "x"},
    "project": {"id": "proj1", "slug": "demo", "defaultBranch": "main", "repoUrl": "x"},
    "activeConfig": {},
}
PREPARE_RESULT = {"workspacePath": "/ws", "branchName": "b"}


def _clients(candidates, cleanup_result=None):
    api_client = FakeApiClient(CONTEXT)
    api_client.retention_candidates = candidates
    runner_client = FakeRunnerClient(PREPARE_RESULT, [], [], {"files": []})
    if cleanup_result is not None:
        runner_client.cleanup_result = cleanup_result
    return api_client, runner_client


async def test_run_once_groups_candidates_by_project_and_calls_cleanup():
    candidates = [
        {"runId": "run1", "projectSlug": "demo"},
        {"runId": "run2", "projectSlug": "demo"},
        {"runId": "run3", "projectSlug": "other"},
    ]
    api_client, runner_client = _clients(candidates, cleanup_result={"deletedRunIds": ["run1", "run2"]})

    deleted = await run_once(api_client, runner_client, max_age_days=7)

    assert api_client.retention_candidates_calls == [7]
    assert ("demo", ["run1", "run2"], 7) in runner_client.cleanup_calls
    assert ("other", ["run3"], 7) in runner_client.cleanup_calls
    # cleanup_result is shared by the fake across calls, so both calls report the same 2 deleted ids
    assert deleted == 4
    # Phase 16: artifacts deleted once per reported deletedRunId, same shared-fake-result quirk
    assert api_client.delete_artifacts_calls == ["run1", "run2", "run1", "run2"]


async def test_run_once_with_no_candidates_calls_nothing():
    api_client, runner_client = _clients([])

    deleted = await run_once(api_client, runner_client, max_age_days=7)

    assert deleted == 0
    assert runner_client.cleanup_calls == []
    assert api_client.delete_artifacts_calls == []


async def test_run_once_does_not_delete_artifacts_for_undeleted_workspaces():
    # A candidate whose workspace cleanup skipped it (e.g. younger than max_age_days) must keep
    # its artifacts too -- only actually-deleted run ids should trigger an artifact delete.
    candidates = [{"runId": "run1", "projectSlug": "demo"}]
    api_client, runner_client = _clients(candidates, cleanup_result={"deletedRunIds": []})

    deleted = await run_once(api_client, runner_client, max_age_days=7)

    assert deleted == 0
    assert api_client.delete_artifacts_calls == []
