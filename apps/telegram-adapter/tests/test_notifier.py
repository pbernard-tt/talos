# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

import json

from talos_telegram_adapter.config import Settings
from talos_telegram_adapter.notifier import _handle, format_notification

from fakes import FakeTelegramClient


def _settings(**overrides) -> Settings:
    defaults = dict(
        bot_token="test-token",
        allowed_chat_ids=frozenset({"111", "222"}),
        api_base_url="http://talos-api:8080",
        service_account_email="telegram-bot@test.local",
        service_account_password="pw",
        web_base_url="https://talos.example.com",
        rabbitmq_url="amqp://talos:talos@rabbitmq:5672",
        redis_url="redis://redis:6379",
    )
    defaults.update(overrides)
    return Settings(**defaults)


class FakeMessage:
    def __init__(self, body: bytes) -> None:
        self.body = body

    def process(self, requeue: bool = True):
        return self

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, tb) -> bool:
        return False


class FakeRedis:
    def __init__(self) -> None:
        self._store: set[str] = set()

    async def set(self, key: str, value: str, nx: bool = False, ex: int | None = None):
        if nx and key in self._store:
            return None
        self._store.add(key)
        return True


def test_format_notification_approval_requested_includes_review_deep_link():
    settings = _settings()
    text = format_notification(
        "approval.requested",
        {"approval_id": "a1", "run_id": "r1", "task_title": "Fix bug", "review_status": "CLEAN"},
        settings,
    )
    assert "Fix bug" in text
    assert "https://talos.example.com/review/r1" in text
    assert "RISK FLAGGED" not in text


def test_format_notification_flags_risk_flagged_review_status():
    settings = _settings()
    text = format_notification(
        "approval.requested",
        {"approval_id": "a1", "run_id": "r1", "task_title": "Fix bug", "review_status": "RISK_FLAGGED"},
        settings,
    )
    assert "RISK FLAGGED" in text


def test_format_notification_pr_created_includes_pr_url_and_run_deep_link():
    settings = _settings()
    text = format_notification(
        "pr.created", {"run_id": "r1", "pr_url": "https://github.com/org/repo/pull/7", "pr_number": 7}, settings
    )
    assert "https://github.com/org/repo/pull/7" in text
    assert "https://talos.example.com/runs/r1" in text


def test_format_notification_run_status_changed_includes_transition_and_deep_link():
    settings = _settings()
    text = format_notification("run.status.changed", {"run_id": "r1", "from": "RUNNING_AGENT", "to": "REVIEWING"}, settings)
    assert "RUNNING_AGENT" in text
    assert "REVIEWING" in text
    assert "https://talos.example.com/runs/r1" in text


def test_format_notification_returns_none_for_unrecognized_event_type():
    assert format_notification("task.run.requested", {}, _settings()) is None


async def test_handle_notifies_every_allow_listed_chat():
    settings = _settings()
    telegram_client = FakeTelegramClient()
    redis_client = FakeRedis()
    envelope = json.dumps(
        {
            "event_id": "e1",
            "event_type": "run.status.changed",
            "payload": {"run_id": "r1", "from": "RUNNING_AGENT", "to": "REVIEWING"},
        }
    ).encode()

    await _handle(FakeMessage(envelope), redis_client, telegram_client, settings)

    notified_chats = {chat_id for chat_id, _ in telegram_client.sent_messages}
    assert notified_chats == {"111", "222"}


async def test_handle_skips_duplicate_event_id():
    settings = _settings()
    telegram_client = FakeTelegramClient()
    redis_client = FakeRedis()
    envelope = json.dumps(
        {"event_id": "e1", "event_type": "run.status.changed", "payload": {"run_id": "r1", "from": "A", "to": "B"}}
    ).encode()

    await _handle(FakeMessage(envelope), redis_client, telegram_client, settings)
    await _handle(FakeMessage(envelope), redis_client, telegram_client, settings)

    assert len(telegram_client.sent_messages) == 2  # one send per allow-listed chat, not four
