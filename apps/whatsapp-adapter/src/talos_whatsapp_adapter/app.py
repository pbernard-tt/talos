"""FastAPI app: talos-whatsapp-adapter's webhook + health surface (Section 16 Phase 12 Track B).
Unlike Telegram's long-poll, the Cloud API only supports webhooks -- Meta requires a public HTTPS
endpoint, so this adapter (unlike apps/telegram-adapter) needs its own web server."""

from __future__ import annotations

import json
import logging
from contextlib import asynccontextmanager

import aio_pika
import redis.asyncio as redis
from fastapi import FastAPI, Request, Response

from talos_whatsapp_adapter.api_client import ApiClient
from talos_whatsapp_adapter.config import Settings, load_settings
from talos_whatsapp_adapter.notifier import start_consuming
from talos_whatsapp_adapter.webhook_processing import process_webhook_payload
from talos_whatsapp_adapter.whatsapp_client import WhatsAppClient, verify_signature

logger = logging.getLogger(__name__)


def is_configured(settings: Settings) -> bool:
    return bool(settings.access_token) and bool(settings.phone_number_id) and bool(settings.allowed_wa_ids)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = load_settings()
    app.state.settings = settings
    app.state.api_client = ApiClient(settings)
    app.state.whatsapp_client = WhatsAppClient(
        settings.access_token, settings.phone_number_id, settings.graph_api_base_url
    )
    app.state.rabbitmq_connection = None
    app.state.redis_client = None

    if is_configured(settings):
        app.state.redis_client = redis.from_url(settings.redis_url)
        app.state.rabbitmq_connection = await aio_pika.connect_robust(settings.rabbitmq_url)
        await start_consuming(app.state.rabbitmq_connection, app.state.whatsapp_client, app.state.redis_client, settings)
    else:
        # Section 16 Phase 12 Track B is optional post-MVP: `docker compose up` must still work
        # with it unconfigured, not crash-loop -- the webhook endpoints simply have nothing to
        # notify or allow-list against until TALOS_WHATSAPP_* is set.
        logger.warning(
            "TALOS_WHATSAPP_ACCESS_TOKEN/PHONE_NUMBER_ID/ALLOWED_IDS not set; "
            "talos-whatsapp-adapter serving /health only, no notifier"
        )

    try:
        yield
    finally:
        await app.state.api_client.aclose()
        await app.state.whatsapp_client.aclose()
        if app.state.rabbitmq_connection is not None:
            await app.state.rabbitmq_connection.close()
        if app.state.redis_client is not None:
            await app.state.redis_client.aclose()


app = FastAPI(title="Talos WhatsApp Adapter", lifespan=lifespan)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/webhook")
async def verify_webhook(request: Request) -> Response:
    """Meta's subscription handshake: https://developers.facebook.com/docs/graph-api/webhooks/getting-started"""
    settings: Settings = request.app.state.settings
    params = request.query_params
    if (
        settings.verify_token
        and params.get("hub.mode") == "subscribe"
        and params.get("hub.verify_token") == settings.verify_token
    ):
        return Response(content=params.get("hub.challenge", ""), media_type="text/plain")
    return Response(status_code=403)


@app.post("/webhook")
async def receive_webhook(request: Request) -> Response:
    settings: Settings = request.app.state.settings
    body = await request.body()
    signature = request.headers.get("X-Hub-Signature-256")
    if not verify_signature(body, signature, settings.app_secret):
        logger.warning("rejected webhook POST with invalid or missing X-Hub-Signature-256")
        return Response(status_code=403)

    payload = json.loads(body)
    await process_webhook_payload(payload, request.app.state.api_client, request.app.state.whatsapp_client, settings)
    return Response(status_code=200)
