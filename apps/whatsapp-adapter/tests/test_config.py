from talos_whatsapp_adapter.app import is_configured
from talos_whatsapp_adapter.config import Settings


def _settings(**overrides) -> Settings:
    defaults = dict(
        verify_token="",
        app_secret="",
        access_token="",
        phone_number_id="",
        allowed_wa_ids=frozenset(),
        api_base_url="http://talos-api:8080",
        service_account_email="whatsapp-bot@test.local",
        service_account_password="pw",
        web_base_url="https://talos.example.com",
        rabbitmq_url="amqp://talos:talos@rabbitmq:5672",
        redis_url="redis://redis:6379",
    )
    defaults.update(overrides)
    return Settings(**defaults)


def test_is_configured_false_when_access_token_missing():
    settings = _settings(phone_number_id="123", allowed_wa_ids=frozenset({"111"}))
    assert is_configured(settings) is False


def test_is_configured_false_when_allowed_ids_empty():
    settings = _settings(access_token="tok", phone_number_id="123")
    assert is_configured(settings) is False


def test_is_configured_true_when_all_set():
    settings = _settings(access_token="tok", phone_number_id="123", allowed_wa_ids=frozenset({"111"}))
    assert is_configured(settings) is True
