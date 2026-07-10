import json

from talos_whatsapp_adapter.config import Settings
from talos_whatsapp_adapter.notifier import _handle, format_notification

from fakes import FakeWhatsAppClient


def _settings(**overrides) -> Settings:
    defaults = dict(
        verify_token="verify-me",
        app_secret="app-secret",
        access_token="access-token",
        phone_number_id="123456",
        allowed_wa_ids=frozenset({"111", "222"}),
        api_base_url="http://talos-api:8080",
        service_account_email="whatsapp-bot@test.local",
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
    text = format_notification(
        "approval.requested",
        {"approval_id": "a1", "run_id": "r1", "task_title": "Fix bug", "review_status": "CLEAN"},
        _settings(),
    )
    assert "Fix bug" in text
    assert "https://talos.example.com/review/r1" in text


def test_format_notification_returns_none_for_unrecognized_event_type():
    assert format_notification("task.run.requested", {}, _settings()) is None


async def test_handle_notifies_every_allow_listed_wa_id():
    settings = _settings()
    whatsapp_client = FakeWhatsAppClient()
    redis_client = FakeRedis()
    envelope = json.dumps(
        {
            "event_id": "e1",
            "event_type": "run.status.changed",
            "payload": {"run_id": "r1", "from": "RUNNING_AGENT", "to": "REVIEWING"},
        }
    ).encode()

    await _handle(FakeMessage(envelope), redis_client, whatsapp_client, settings)

    notified = {wa_id for wa_id, _ in whatsapp_client.sent_messages}
    assert notified == {"111", "222"}


async def test_handle_skips_duplicate_event_id():
    settings = _settings()
    whatsapp_client = FakeWhatsAppClient()
    redis_client = FakeRedis()
    envelope = json.dumps(
        {"event_id": "e1", "event_type": "run.status.changed", "payload": {"run_id": "r1", "from": "A", "to": "B"}}
    ).encode()

    await _handle(FakeMessage(envelope), redis_client, whatsapp_client, settings)
    await _handle(FakeMessage(envelope), redis_client, whatsapp_client, settings)

    assert len(whatsapp_client.sent_messages) == 2  # one send per allow-listed id, not four
