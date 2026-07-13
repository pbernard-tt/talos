# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

"""Env var reference: Appendix A "talos-telegram-adapter" of the implementation plan."""

from __future__ import annotations

import os
from dataclasses import dataclass


def _parse_allowed_chat_ids(raw: str) -> frozenset[str]:
    return frozenset(chat_id.strip() for chat_id in raw.split(",") if chat_id.strip())


@dataclass(frozen=True)
class Settings:
    bot_token: str
    allowed_chat_ids: frozenset[str]
    api_base_url: str
    service_account_email: str
    service_account_password: str
    web_base_url: str
    rabbitmq_url: str
    redis_url: str
    poll_timeout_seconds: int = 30


def load_settings() -> Settings:
    return Settings(
        bot_token=os.environ.get("TALOS_TELEGRAM_BOT_TOKEN", ""),
        allowed_chat_ids=_parse_allowed_chat_ids(os.environ.get("TALOS_TELEGRAM_ALLOWED_CHAT_IDS", "")),
        api_base_url=os.environ.get("TALOS_API_BASE_URL", "http://talos-api:8080"),
        service_account_email=os.environ.get("TALOS_TELEGRAM_SERVICE_EMAIL", ""),
        service_account_password=os.environ.get("TALOS_TELEGRAM_SERVICE_PASSWORD", ""),
        web_base_url=os.environ.get("TALOS_WEB_BASE_URL", "http://localhost:4200"),
        rabbitmq_url=os.environ.get("TALOS_RABBITMQ_URL", "amqp://talos:talos@rabbitmq:5672"),
        redis_url=os.environ.get("TALOS_REDIS_URL", "redis://redis:6379"),
        poll_timeout_seconds=int(os.environ.get("TALOS_TELEGRAM_POLL_TIMEOUT_SECONDS", "30")),
    )
