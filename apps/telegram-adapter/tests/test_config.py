from talos_telegram_adapter.config import Settings
from talos_telegram_adapter.main import is_configured


def _settings(**overrides) -> Settings:
    defaults = dict(
        bot_token="",
        allowed_chat_ids=frozenset(),
        api_base_url="http://talos-api:8080",
        service_account_email="telegram-bot@test.local",
        service_account_password="pw",
        web_base_url="https://talos.example.com",
        rabbitmq_url="amqp://talos:talos@rabbitmq:5672",
        redis_url="redis://redis:6379",
    )
    defaults.update(overrides)
    return Settings(**defaults)


def test_is_configured_false_without_bot_token():
    assert is_configured(_settings(bot_token="", allowed_chat_ids=frozenset({"111"}))) is False


def test_is_configured_false_without_allowed_chat_ids():
    assert is_configured(_settings(bot_token="abc", allowed_chat_ids=frozenset())) is False


def test_is_configured_true_with_both_set():
    assert is_configured(_settings(bot_token="abc", allowed_chat_ids=frozenset({"111"}))) is True
