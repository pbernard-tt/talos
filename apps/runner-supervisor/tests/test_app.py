# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

import json
import os
import threading
import time
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from talos_runner_supervisor.app import app, get_settings


@pytest.fixture
def client(settings):
    app.dependency_overrides[get_settings] = lambda: settings
    with TestClient(app) as test_client:
        # Every endpoint except /health requires the shared internal token (initial review #5).
        test_client.headers["X-Talos-Internal-Token"] = settings.internal_api_token
        yield test_client
    app.dependency_overrides.clear()


def _prepare(client: TestClient, origin_repo: Path, task_id: str = "task1", task_title: str = "Add hello endpoint"):
    resp = client.post(
        "/workspaces/prepare",
        json={
            "runId": f"prep-{task_id}",
            "taskId": task_id,
            "taskTitle": task_title,
            "projectSlug": "demo",
            "repoUrl": str(origin_repo),
            "defaultBranch": "main",
        },
    )
    assert resp.status_code == 200
    return resp.json()["workspacePath"]


def test_health(client):
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}


def test_health_requiresNoToken(client):
    resp = client.get("/health", headers={"X-Talos-Internal-Token": ""})
    assert resp.status_code == 200


def test_missingToken_rejected401(settings):
    app.dependency_overrides[get_settings] = lambda: settings
    try:
        with TestClient(app) as bare_client:
            resp = bare_client.post("/workspaces/cleanup", json={})
            assert resp.status_code == 401
            assert resp.json()["detail"]["error"]["code"] == "INTERNAL_TOKEN_INVALID"
    finally:
        app.dependency_overrides.clear()


def test_wrongToken_rejected401(client):
    resp = client.post("/workspaces/cleanup", json={}, headers={"X-Talos-Internal-Token": "wrong-token"})
    assert resp.status_code == 401
    assert resp.json()["detail"]["error"]["code"] == "INTERNAL_TOKEN_INVALID"


def test_unconfiguredToken_failsClosed503(settings):
    import dataclasses

    unconfigured = dataclasses.replace(settings, internal_api_token="")
    app.dependency_overrides[get_settings] = lambda: unconfigured
    try:
        with TestClient(app) as bare_client:
            resp = bare_client.post(
                "/workspaces/cleanup", json={}, headers={"X-Talos-Internal-Token": "anything"}
            )
            assert resp.status_code == 503
            assert resp.json()["detail"]["error"]["code"] == "INTERNAL_TOKEN_NOT_CONFIGURED"
    finally:
        app.dependency_overrides.clear()


def test_prepare_workspace_endpoint(client, origin_repo):
    resp = client.post(
        "/workspaces/prepare",
        json={
            "runId": "run1",
            "taskId": "task1",
            "taskTitle": "Add hello endpoint",
            "projectSlug": "demo",
            "repoUrl": str(origin_repo),
            "defaultBranch": "main",
        },
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["branchName"] == "agent/task-task1-add-hello-endpoint"
    assert Path(body["workspacePath"]).exists()


def test_execute_then_diff_end_to_end(client, origin_repo):
    workspace_path = _prepare(client, origin_repo, task_id="exec1")

    resp = client.post(
        "/runs/run-exec/execute",
        json={
            "adapterKey": "custom-shell",
            "workspacePath": workspace_path,
            "prompt": "echo 'new content' > new-file.txt",
            "env": {},
            "authMode": "api_key",
            "timeoutSeconds": 10,
        },
    )
    assert resp.status_code == 200
    lines = [json.loads(line) for line in resp.text.strip().splitlines()]
    result_lines = [line for line in lines if line.get("type") == "result"]
    assert len(result_lines) == 1
    assert result_lines[0]["success"] is True

    diff_resp = client.post("/runs/run-exec/diff", json={"workspacePath": workspace_path})
    assert diff_resp.status_code == 200
    diff_body = diff_resp.json()
    assert any(f["filePath"] == "new-file.txt" and f["changeType"] == "ADDED" for f in diff_body["files"])
    assert Path(diff_body["diffArtifactPath"]).exists()


def test_execute_rejects_concurrent_run_with_same_id(client, origin_repo):
    workspace_path = _prepare(client, origin_repo, task_id="exec2")

    def run_slow():
        client.post(
            "/runs/run-busy/execute",
            json={
                "adapterKey": "custom-shell",
                "workspacePath": workspace_path,
                "prompt": "sleep 3",
                "env": {},
                "authMode": "api_key",
                "timeoutSeconds": 30,
            },
        )

    thread = threading.Thread(target=run_slow)
    thread.start()
    time.sleep(0.5)
    try:
        resp = client.post(
            "/runs/run-busy/execute",
            json={
                "adapterKey": "custom-shell",
                "workspacePath": workspace_path,
                "prompt": "echo hi",
                "env": {},
                "authMode": "api_key",
                "timeoutSeconds": 10,
            },
        )
        assert resp.status_code == 409
    finally:
        thread.join(timeout=15)


def test_stop_kills_running_process(client, origin_repo):
    workspace_path = _prepare(client, origin_repo, task_id="exec3")

    def run_slow():
        client.post(
            "/runs/run-stop/execute",
            json={
                "adapterKey": "custom-shell",
                "workspacePath": workspace_path,
                "prompt": "sleep 30",
                "env": {},
                "authMode": "api_key",
                "timeoutSeconds": 60,
            },
        )

    thread = threading.Thread(target=run_slow)
    thread.start()
    time.sleep(0.5)

    stop_resp = client.post("/runs/run-stop/stop")
    assert stop_resp.status_code == 204

    thread.join(timeout=15)
    assert not thread.is_alive()


def test_stop_unknown_run_returns_404(client):
    resp = client.post("/runs/unknown-run/stop")
    assert resp.status_code == 404


def test_cleanup_deletes_old_named_runs(client, settings):
    run_dir = Path(settings.workspaces_root) / "demo" / "runs" / "old-run"
    run_dir.mkdir(parents=True)
    (run_dir / "marker.txt").write_text("x")
    old_time = time.time() - 8 * 86400
    os.utime(run_dir, (old_time, old_time))

    resp = client.post("/workspaces/cleanup", json={"projectSlug": "demo", "runIds": ["old-run"]})
    assert resp.status_code == 200
    assert resp.json()["deletedRunIds"] == ["old-run"]
    assert not run_dir.exists()


def test_cleanup_leaves_run_younger_than_max_age(client, settings):
    run_dir = Path(settings.workspaces_root) / "demo" / "runs" / "young-run"
    run_dir.mkdir(parents=True)

    resp = client.post("/workspaces/cleanup", json={"projectSlug": "demo", "runIds": ["young-run"]})
    assert resp.json()["deletedRunIds"] == []
    assert run_dir.exists()


def test_cleanup_without_run_ids_deletes_nothing(client):
    resp = client.post("/workspaces/cleanup", json={})
    assert resp.status_code == 200
    assert resp.json()["deletedRunIds"] == []


def test_push_endpoint_commitsAndPushes(client, origin_repo):
    workspace_path = _prepare(client, origin_repo, task_id="push1")
    (Path(workspace_path) / "new-file.txt").write_text("hello\n")

    resp = client.post(
        "/runs/run-push/push",
        json={
            "workspacePath": workspace_path,
            "branchName": "agent/task-push1-add-hello-endpoint",
            "defaultBranch": "main",
            "commitMessage": "talos: Add hello endpoint (task push1, run run-push)",
            "token": "unused-for-a-local-path-remote",
            "repoUrl": str(origin_repo),
        },
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["pushed"] is True
    assert body["needsRebase"] is False
    assert body["commitSha"]


def test_push_endpoint_refusesDefaultBranch_returns422(client, origin_repo):
    workspace_path = _prepare(client, origin_repo, task_id="push2")

    resp = client.post(
        "/runs/run-push2/push",
        json={
            "workspacePath": workspace_path,
            "branchName": "main",
            "defaultBranch": "main",
            "commitMessage": "x",
            "token": "x",
            "repoUrl": str(origin_repo),
        },
    )
    assert resp.status_code == 422
    assert resp.json()["detail"]["error"]["code"] == "GIT_PUSH_FAILED"
