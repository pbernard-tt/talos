"""REST client against talos-api's public /api/v1 surface (Section 16 Phase 12 Track B) -- this
adapter never touches /internal/v1 and never opens a database connection; talos-api remains the
sole PostgreSQL writer. Authenticates as the seeded WhatsApp service account, re-logging in before
its 24h JWT (Section 12.2) expires."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Any

import httpx

from talos_whatsapp_adapter.config import Settings

_TOKEN_REFRESH_MARGIN = timedelta(minutes=5)


class ApiError(Exception):
    def __init__(self, status_code: int, message: str) -> None:
        super().__init__(message)
        self.status_code = status_code


class ApiClient:
    def __init__(self, settings: Settings) -> None:
        self._client = httpx.AsyncClient(base_url=settings.api_base_url, timeout=30.0)
        self._email = settings.service_account_email
        self._password = settings.service_account_password
        self._token: str | None = None
        self._token_expires_at: datetime | None = None

    async def aclose(self) -> None:
        await self._client.aclose()

    async def _ensure_token(self) -> str:
        now = datetime.now(timezone.utc)
        if self._token is None or self._token_expires_at is None or now >= self._token_expires_at - _TOKEN_REFRESH_MARGIN:
            response = await self._client.post(
                "/api/v1/auth/login", json={"email": self._email, "password": self._password}
            )
            response.raise_for_status()
            body = response.json()
            self._token = body["token"]
            self._token_expires_at = datetime.fromisoformat(body["expiresAt"].replace("Z", "+00:00"))
        assert self._token is not None
        return self._token

    async def _request(self, method: str, path: str, **kwargs: Any) -> httpx.Response:
        token = await self._ensure_token()
        headers = kwargs.pop("headers", {})
        headers["Authorization"] = f"Bearer {token}"
        response = await self._client.request(method, path, headers=headers, **kwargs)
        if response.status_code >= 400:
            try:
                message = response.json()["error"]["message"]
            except Exception:
                message = response.text
            raise ApiError(response.status_code, message)
        return response

    async def find_project_id_by_name(self, name: str) -> str | None:
        response = await self._request("GET", "/api/v1/projects", params={"size": 200})
        for project in response.json().get("content", []):
            if project["name"].strip().casefold() == name.strip().casefold():
                return project["id"]
        return None

    async def create_task(self, project_id: str, title: str, description: str | None) -> dict[str, Any]:
        body: dict[str, Any] = {"projectId": project_id, "title": title, "source": "WHATSAPP"}
        if description:
            body["description"] = description
        response = await self._request("POST", "/api/v1/tasks", json=body)
        return response.json()

    async def get_task(self, task_id: str) -> dict[str, Any]:
        response = await self._request("GET", f"/api/v1/tasks/{task_id}")
        return response.json()

    async def get_run(self, run_id: str) -> dict[str, Any]:
        response = await self._request("GET", f"/api/v1/runs/{run_id}")
        return response.json()

    async def list_pending_approvals(self) -> list[dict[str, Any]]:
        response = await self._request("GET", "/api/v1/approvals", params={"status": "PENDING"})
        return response.json()["content"]

    async def record_rejected_sender(self, channel: str, chat_id: str) -> None:
        await self._request("POST", "/api/v1/chat/rejected-sender", json={"channel": channel, "chatId": chat_id})
