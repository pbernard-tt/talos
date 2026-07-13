# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

from __future__ import annotations

from typing import Any

from talos_whatsapp_adapter.api_client import ApiError


class FakeApiClient:
    def __init__(self) -> None:
        self.projects: dict[str, str] = {}  # name -> id
        self.tasks: dict[str, dict[str, Any]] = {}
        self.runs: dict[str, dict[str, Any]] = {}
        self.pending_approvals: list[dict[str, Any]] = []
        self.created_tasks: list[dict[str, Any]] = []
        self.rejected_senders: list[tuple[str, str]] = []
        self._next_task_id = 1

    async def find_project_id_by_name(self, name: str) -> str | None:
        return self.projects.get(name.strip().casefold())

    def add_project(self, name: str, project_id: str) -> None:
        self.projects[name.strip().casefold()] = project_id

    async def create_task(self, project_id: str, title: str, description: str | None) -> dict[str, Any]:
        task_id = f"task-{self._next_task_id}"
        self._next_task_id += 1
        task = {"id": task_id, "projectId": project_id, "title": title, "description": description, "status": "BACKLOG"}
        self.tasks[task_id] = task
        self.created_tasks.append(task)
        return task

    async def get_task(self, task_id: str) -> dict[str, Any]:
        if task_id not in self.tasks:
            raise ApiError(404, "Task not found")
        return self.tasks[task_id]

    async def get_run(self, run_id: str) -> dict[str, Any]:
        if run_id not in self.runs:
            raise ApiError(404, "Run not found")
        return self.runs[run_id]

    async def list_pending_approvals(self) -> list[dict[str, Any]]:
        return self.pending_approvals

    async def record_rejected_sender(self, channel: str, chat_id: str) -> None:
        self.rejected_senders.append((channel, chat_id))


class FakeWhatsAppClient:
    def __init__(self) -> None:
        self.sent_messages: list[tuple[str, str]] = []

    async def send_message(self, wa_id: str, text: str) -> None:
        self.sent_messages.append((wa_id, text))
