"""Executes a parsed inbound command (commands.py) against the ApiClient and formats a reply.
No command here can approve, reject, deploy, or push -- those stay dashboard-only (Section 16)."""

from __future__ import annotations

from typing import Any

from talos_whatsapp_adapter.api_client import ApiClient, ApiError


async def handle_command(api_client: ApiClient, command: dict[str, Any]) -> str:
    kind = command["command"]
    try:
        if kind == "CREATE_TASK":
            return await _handle_create_task(api_client, command)
        if kind == "TASK_STATUS":
            return await _handle_task_status(api_client, command)
        if kind == "RUN_STATUS":
            return await _handle_run_status(api_client, command)
        if kind == "LIST_APPROVALS":
            return await _handle_list_approvals(api_client)
    except ApiError as exc:
        return f"Error: {exc}"
    raise AssertionError(f"unhandled command kind {kind}")  # pragma: no cover -- schema-validated upstream


async def _handle_create_task(api_client: ApiClient, command: dict[str, Any]) -> str:
    project_id = await api_client.find_project_id_by_name(command["project_name"])
    if project_id is None:
        return f"No project named \"{command['project_name']}\" found."
    task = await api_client.create_task(project_id, command["title"], command.get("description"))
    return f"Created task {task['id']}: {task['title']} ({task['status']})"


async def _handle_task_status(api_client: ApiClient, command: dict[str, Any]) -> str:
    task = await api_client.get_task(command["task_id"])
    return f"Task {task['id']}: {task['title']} -- {task['status']}"


async def _handle_run_status(api_client: ApiClient, command: dict[str, Any]) -> str:
    run = await api_client.get_run(command["run_id"])
    return f"Run {run['id']}: {run['status']}"


async def _handle_list_approvals(api_client: ApiClient) -> str:
    approvals = await api_client.list_pending_approvals()
    if not approvals:
        return "No pending approvals."
    lines = [f"- {a['id']} (run {a['runId']}, {a['approvalType']})" for a in approvals]
    return "Pending approvals:\n" + "\n".join(lines)
