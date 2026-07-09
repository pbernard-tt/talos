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
