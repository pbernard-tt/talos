# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

from talos_whatsapp_adapter.handlers import handle_command

from fakes import FakeApiClient


async def test_create_task_resolves_project_and_creates_task():
    api_client = FakeApiClient()
    api_client.add_project("Talos", "proj-1")

    reply = await handle_command(
        api_client,
        {"channel": "WHATSAPP", "chat_id": "1", "command": "CREATE_TASK", "project_name": "Talos", "title": "Fix bug"},
    )

    assert len(api_client.created_tasks) == 1
    created = api_client.created_tasks[0]
    assert created["projectId"] == "proj-1"
    assert "Created task" in reply


async def test_create_task_unknown_project_does_not_call_create():
    api_client = FakeApiClient()

    reply = await handle_command(
        api_client,
        {"channel": "WHATSAPP", "chat_id": "1", "command": "CREATE_TASK", "project_name": "Nope", "title": "Fix bug"},
    )

    assert api_client.created_tasks == []
    assert "No project named" in reply


async def test_task_status_reports_status():
    api_client = FakeApiClient()
    api_client.tasks["task-1"] = {"id": "task-1", "title": "Fix bug", "status": "READY"}

    reply = await handle_command(api_client, {"channel": "WHATSAPP", "chat_id": "1", "command": "TASK_STATUS", "task_id": "task-1"})

    assert "task-1" in reply
    assert "READY" in reply


async def test_task_status_unknown_task_reports_error_without_raising():
    api_client = FakeApiClient()

    reply = await handle_command(api_client, {"channel": "WHATSAPP", "chat_id": "1", "command": "TASK_STATUS", "task_id": "missing"})

    assert reply.startswith("Error:")


async def test_run_status_reports_status():
    api_client = FakeApiClient()
    api_client.runs["run-1"] = {"id": "run-1", "status": "RUNNING_AGENT"}

    reply = await handle_command(api_client, {"channel": "WHATSAPP", "chat_id": "1", "command": "RUN_STATUS", "run_id": "run-1"})

    assert "run-1" in reply
    assert "RUNNING_AGENT" in reply


async def test_list_approvals_reports_none_pending():
    api_client = FakeApiClient()

    reply = await handle_command(api_client, {"channel": "WHATSAPP", "chat_id": "1", "command": "LIST_APPROVALS"})

    assert reply == "No pending approvals."


async def test_list_approvals_lists_each_pending_approval():
    api_client = FakeApiClient()
    api_client.pending_approvals = [{"id": "appr-1", "runId": "run-1", "approvalType": "PUSH"}]

    reply = await handle_command(api_client, {"channel": "WHATSAPP", "chat_id": "1", "command": "LIST_APPROVALS"})

    assert "appr-1" in reply
