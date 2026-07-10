"""Thin httpx wrapper for the Telegram Bot API (long-poll mode -- no public webhook needed for a
self-hosted single-VPS deployment, Section 18). https://core.telegram.org/bots/api#getupdates /
#sendmessage. The bot token is masked in every log path (never interpolated into a log message)."""

from __future__ import annotations

from typing import Any

import httpx


class TelegramClient:
    def __init__(self, bot_token: str, poll_timeout_seconds: int) -> None:
        self._client = httpx.AsyncClient(
            base_url=f"https://api.telegram.org/bot{bot_token}",
            timeout=poll_timeout_seconds + 10.0,
        )
        self._poll_timeout_seconds = poll_timeout_seconds

    async def aclose(self) -> None:
        await self._client.aclose()

    async def get_updates(self, offset: int | None) -> list[dict[str, Any]]:
        params: dict[str, Any] = {"timeout": self._poll_timeout_seconds}
        if offset is not None:
            params["offset"] = offset
        response = await self._client.get("/getUpdates", params=params)
        response.raise_for_status()
        body = response.json()
        if not body.get("ok"):
            return []
        return body["result"]

    async def send_message(self, chat_id: str, text: str) -> None:
        response = await self._client.post("/sendMessage", json={"chat_id": chat_id, "text": text})
        response.raise_for_status()
