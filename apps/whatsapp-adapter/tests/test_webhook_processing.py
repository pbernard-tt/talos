# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

from talos_whatsapp_adapter.config import Settings
from talos_whatsapp_adapter.webhook_processing import process_webhook_payload

from fakes import FakeApiClient, FakeWhatsAppClient


def _settings(**overrides) -> Settings:
    defaults = dict(
        verify_token="verify-me",
        app_secret="app-secret",
        access_token="access-token",
        phone_number_id="123456",
        allowed_wa_ids=frozenset({"111"}),
        api_base_url="http://talos-api:8080",
        service_account_email="whatsapp-bot@test.local",
        service_account_password="pw",
        web_base_url="https://talos.example.com",
        rabbitmq_url="amqp://talos:talos@rabbitmq:5672",
        redis_url="redis://redis:6379",
    )
    defaults.update(overrides)
    return Settings(**defaults)


def _webhook_body(wa_id: str, text: str) -> dict:
    return {"entry": [{"changes": [{"value": {"messages": [{"from": wa_id, "type": "text", "text": {"body": text}}]}}]}]}


async def test_allow_listed_sender_command_is_executed_and_replied_to():
    settings = _settings()
    api_client = FakeApiClient()
    whatsapp_client = FakeWhatsAppClient()

    await process_webhook_payload(_webhook_body("111", "/approvals"), api_client, whatsapp_client, settings)

    assert api_client.rejected_senders == []
    assert whatsapp_client.sent_messages == [("111", "No pending approvals.")]


async def test_non_allow_listed_sender_is_audited_and_never_replied_to():
    settings = _settings()
    api_client = FakeApiClient()
    whatsapp_client = FakeWhatsAppClient()

    await process_webhook_payload(_webhook_body("999", "/approvals"), api_client, whatsapp_client, settings)

    assert api_client.rejected_senders == [("WHATSAPP", "999")]
    assert whatsapp_client.sent_messages == []


async def test_unparseable_command_gets_usage_reply_not_a_crash():
    settings = _settings()
    api_client = FakeApiClient()
    whatsapp_client = FakeWhatsAppClient()

    await process_webhook_payload(_webhook_body("111", "/create_task onlyoneproject"), api_client, whatsapp_client, settings)

    assert api_client.created_tasks == []
    assert len(whatsapp_client.sent_messages) == 1
    wa_id, reply = whatsapp_client.sent_messages[0]
    assert wa_id == "111"
    assert "Usage: /create_task" in reply


async def test_status_update_only_payload_produces_no_replies():
    settings = _settings()
    api_client = FakeApiClient()
    whatsapp_client = FakeWhatsAppClient()
    body = {"entry": [{"changes": [{"value": {"statuses": [{"id": "wamid.1", "status": "delivered"}]}}]}]}

    await process_webhook_payload(body, api_client, whatsapp_client, settings)

    assert whatsapp_client.sent_messages == []
    assert api_client.rejected_senders == []
