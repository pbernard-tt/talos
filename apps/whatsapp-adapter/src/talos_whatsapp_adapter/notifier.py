# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

"""Section 11: talos.events -> notifiers consumer for approval.requested/pr.created/run.status.changed,
on this adapter's own quorum queue, idempotent on event_id (processed IDs cached in Redis, 24h TTL) --
the same pattern apps/orchestrator and apps/telegram-adapter use for their own queues. Chat receives
notifications with dashboard deep links only; approval *decisions* stay dashboard-only (Section 16
Phase 12 Track B)."""

from __future__ import annotations

import functools
import json
import logging
from typing import Any

import aio_pika
import redis.asyncio as redis
from aio_pika.abc import AbstractIncomingMessage

from talos_whatsapp_adapter.config import Settings
from talos_whatsapp_adapter.whatsapp_client import WhatsAppClient

logger = logging.getLogger(__name__)

EVENTS_EXCHANGE = "talos.events"
DLX_EXCHANGE = "talos.events.dlx"
DLQ_QUEUE = "talos.dlq"
NOTIFIER_QUEUE = "talos.notifiers.whatsapp"
NOTIFIED_ROUTING_KEYS = ("approval.requested", "pr.created", "run.status.changed")

_QUEUE_ARGS = {"x-queue-type": "quorum", "x-delivery-limit": 3, "x-dead-letter-exchange": DLX_EXCHANGE}
_IDEMPOTENCY_TTL_SECONDS = 24 * 60 * 60


def format_notification(event_type: str, payload: dict[str, Any], settings: Settings) -> str | None:
    if event_type == "approval.requested":
        review_url = f"{settings.web_base_url}/review/{payload['run_id']}"
        flag = " [RISK FLAGGED]" if payload.get("review_status") == "RISK_FLAGGED" else ""
        return f'Approval requested for "{payload["task_title"]}"{flag}\n{review_url}'
    if event_type == "pr.created":
        run_url = f"{settings.web_base_url}/runs/{payload['run_id']}"
        return f"PR #{payload['pr_number']} opened: {payload['pr_url']}\n{run_url}"
    if event_type == "run.status.changed":
        run_url = f"{settings.web_base_url}/runs/{payload['run_id']}"
        return f"Run {payload['run_id']}: {payload['from']} -> {payload['to']}\n{run_url}"
    return None


async def _handle(
    message: AbstractIncomingMessage,
    redis_client: redis.Redis,
    whatsapp_client: WhatsAppClient,
    settings: Settings,
) -> None:
    async with message.process(requeue=True):
        envelope = json.loads(message.body)
        event_id = envelope["event_id"]
        is_new = await redis_client.set(f"talos:processed-event:{event_id}", "1", nx=True, ex=_IDEMPOTENCY_TTL_SECONDS)
        if not is_new:
            logger.info("event %s already processed, skipping", event_id)
            return
        text = format_notification(envelope["event_type"], envelope["payload"], settings)
        if text is None:
            return
        for wa_id in settings.allowed_wa_ids:
            await whatsapp_client.send_message(wa_id, text)


async def start_consuming(
    connection: aio_pika.abc.AbstractRobustConnection,
    whatsapp_client: WhatsAppClient,
    redis_client: redis.Redis,
    settings: Settings,
) -> None:
    channel = await connection.channel()
    await channel.set_qos(prefetch_count=1)

    exchange = await channel.declare_exchange(EVENTS_EXCHANGE, aio_pika.ExchangeType.TOPIC, durable=True)
    dlx = await channel.declare_exchange(DLX_EXCHANGE, aio_pika.ExchangeType.TOPIC, durable=True)
    dlq = await channel.declare_queue(DLQ_QUEUE, durable=True, arguments={"x-queue-type": "quorum"})
    await dlq.bind(dlx, routing_key="#")

    queue = await channel.declare_queue(NOTIFIER_QUEUE, durable=True, arguments=_QUEUE_ARGS)
    for routing_key in NOTIFIED_ROUTING_KEYS:
        await queue.bind(exchange, routing_key=routing_key)

    await queue.consume(
        functools.partial(_handle, redis_client=redis_client, whatsapp_client=whatsapp_client, settings=settings)
    )
    logger.info("talos-whatsapp-adapter consuming %s", NOTIFIER_QUEUE)
