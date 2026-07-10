"""Env var reference: Appendix A "talos-whatsapp-adapter" of the implementation plan."""

from __future__ import annotations

import os
from dataclasses import dataclass


def _parse_allowed_wa_ids(raw: str) -> frozenset[str]:
    return frozenset(wa_id.strip() for wa_id in raw.split(",") if wa_id.strip())


@dataclass(frozen=True)
class Settings:
    verify_token: str
    app_secret: str
    access_token: str
    phone_number_id: str
    allowed_wa_ids: frozenset[str]
    api_base_url: str
    service_account_email: str
    service_account_password: str
    web_base_url: str
    rabbitmq_url: str
    redis_url: str
    graph_api_base_url: str = "https://graph.facebook.com/v20.0"


def load_settings() -> Settings:
    return Settings(
        verify_token=os.environ.get("TALOS_WHATSAPP_VERIFY_TOKEN", ""),
        app_secret=os.environ.get("TALOS_WHATSAPP_APP_SECRET", ""),
        access_token=os.environ.get("TALOS_WHATSAPP_ACCESS_TOKEN", ""),
        phone_number_id=os.environ.get("TALOS_WHATSAPP_PHONE_NUMBER_ID", ""),
        allowed_wa_ids=_parse_allowed_wa_ids(os.environ.get("TALOS_WHATSAPP_ALLOWED_IDS", "")),
        api_base_url=os.environ.get("TALOS_API_BASE_URL", "http://talos-api:8080"),
        service_account_email=os.environ.get("TALOS_WHATSAPP_SERVICE_EMAIL", ""),
        service_account_password=os.environ.get("TALOS_WHATSAPP_SERVICE_PASSWORD", ""),
        web_base_url=os.environ.get("TALOS_WEB_BASE_URL", "http://localhost:4200"),
        rabbitmq_url=os.environ.get("TALOS_RABBITMQ_URL", "amqp://talos:talos@rabbitmq:5672"),
        redis_url=os.environ.get("TALOS_REDIS_URL", "redis://redis:6379"),
        graph_api_base_url=os.environ.get("TALOS_WHATSAPP_GRAPH_API_BASE_URL", "https://graph.facebook.com/v20.0"),
    )
