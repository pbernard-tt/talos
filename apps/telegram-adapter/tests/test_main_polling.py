from talos_telegram_adapter.config import Settings
from talos_telegram_adapter.main import poll_once

from fakes import FakeApiClient, FakeTelegramClient


def _settings(**overrides) -> Settings:
    defaults = dict(
        bot_token="test-token",
        allowed_chat_ids=frozenset({"111"}),
        api_base_url="http://talos-api:8080",
        service_account_email="telegram-bot@test.local",
        service_account_password="pw",
        web_base_url="https://talos.example.com",
        rabbitmq_url="amqp://talos:talos@rabbitmq:5672",
        redis_url="redis://redis:6379",
    )
    defaults.update(overrides)
    return Settings(**defaults)


def _text_update(update_id: int, chat_id: int, text: str) -> dict:
    return {"update_id": update_id, "message": {"chat": {"id": chat_id}, "text": text}}


async def test_allow_listed_chat_command_is_executed_and_replied_to():
    settings = _settings()
    telegram_client = FakeTelegramClient(updates_by_offset=[[_text_update(1, 111, "/approvals")]])
    api_client = FakeApiClient()

    next_offset = await poll_once(telegram_client, api_client, settings, offset=None)

    assert next_offset == 2
    assert api_client.rejected_senders == []
    assert telegram_client.sent_messages == [("111", "No pending approvals.")]


async def test_non_allow_listed_chat_is_silently_audited_and_never_replied_to():
    settings = _settings()
    telegram_client = FakeTelegramClient(updates_by_offset=[[_text_update(1, 999, "/approvals")]])
    api_client = FakeApiClient()

    await poll_once(telegram_client, api_client, settings, offset=None)

    assert api_client.rejected_senders == [("TELEGRAM", "999")]
    assert telegram_client.sent_messages == []


async def test_unparseable_command_from_allow_listed_chat_gets_usage_reply_not_a_crash():
    settings = _settings()
    telegram_client = FakeTelegramClient(updates_by_offset=[[_text_update(1, 111, "/create_task onlyoneproject")]])
    api_client = FakeApiClient()

    await poll_once(telegram_client, api_client, settings, offset=None)

    assert api_client.created_tasks == []
    assert len(telegram_client.sent_messages) == 1
    chat_id, reply = telegram_client.sent_messages[0]
    assert chat_id == "111"
    assert "Usage: /create_task" in reply


async def test_non_message_updates_are_skipped_and_advance_offset():
    settings = _settings()
    telegram_client = FakeTelegramClient(updates_by_offset=[[{"update_id": 5, "edited_message": {}}]])
    api_client = FakeApiClient()

    next_offset = await poll_once(telegram_client, api_client, settings, offset=None)

    assert next_offset == 6
    assert telegram_client.sent_messages == []
    assert api_client.rejected_senders == []
