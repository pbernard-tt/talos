"""aio-pika consumer bootstrap (Section 6.3/11): consumes task.run.requested (prefetch 1, manual
ack) and this phase's run.cancel.requested extension, dispatching each to the run pipeline.
"""

from __future__ import annotations

import asyncio
import functools
import json
import logging
from typing import Any, Awaitable, Callable

import aio_pika
import redis.asyncio as redis
from aio_pika.abc import AbstractIncomingMessage

from talos_orchestrator.api_client import ApiClient
from talos_orchestrator.config import load_settings
from talos_orchestrator.locks import RunLock
from talos_orchestrator.pipeline import RunPipeline
from talos_orchestrator.runner_client import RunnerClient

logger = logging.getLogger(__name__)

EVENTS_EXCHANGE = "talos.events"
DLX_EXCHANGE = "talos.events.dlx"
DLQ_QUEUE = "talos.dlq"
RUN_REQUESTS_QUEUE = "talos.orchestrator.run-requests"
CANCELLATIONS_QUEUE = "talos.orchestrator.cancellations"
APPROVALS_QUEUE = "talos.orchestrator.approvals"

# Section 11: "retried up to 3 redeliveries, then dead-lettered to talos.dlq" -- quorum queues'
# native x-delivery-limit does this declaratively, no manual retry-count bookkeeping needed.
_QUEUE_ARGS = {"x-queue-type": "quorum", "x-delivery-limit": 3, "x-dead-letter-exchange": DLX_EXCHANGE}

# Section 11: "All consumers are idempotent keyed on event_id (processed IDs cached in Redis, 24h TTL)."
_IDEMPOTENCY_TTL_SECONDS = 24 * 60 * 60


async def _handle(
    message: AbstractIncomingMessage,
    redis_client: redis.Redis,
    handler: Callable[[dict[str, Any]], Awaitable[None]],
) -> None:
    async with message.process(requeue=True):
        envelope = json.loads(message.body)
        event_id = envelope["event_id"]
        is_new = await redis_client.set(f"talos:processed-event:{event_id}", "1", nx=True, ex=_IDEMPOTENCY_TTL_SECONDS)
        if not is_new:
            logger.info("event %s already processed, skipping", event_id)
            return
        await handler(envelope["payload"])


async def _amain() -> None:
    settings = load_settings()
    api_client = ApiClient(settings)
    runner_client = RunnerClient(settings)
    run_lock = RunLock(settings)
    pipeline = RunPipeline(api_client, runner_client, run_lock)
    redis_client = redis.from_url(settings.redis_url)

    connection = await aio_pika.connect_robust(settings.rabbitmq_url)
    async with connection:
        channel = await connection.channel()
        await channel.set_qos(prefetch_count=1)

        exchange = await channel.declare_exchange(EVENTS_EXCHANGE, aio_pika.ExchangeType.TOPIC, durable=True)
        dlx = await channel.declare_exchange(DLX_EXCHANGE, aio_pika.ExchangeType.TOPIC, durable=True)
        dlq = await channel.declare_queue(DLQ_QUEUE, durable=True, arguments={"x-queue-type": "quorum"})
        await dlq.bind(dlx, routing_key="#")

        run_requests_queue = await channel.declare_queue(RUN_REQUESTS_QUEUE, durable=True, arguments=_QUEUE_ARGS)
        await run_requests_queue.bind(exchange, routing_key="task.run.requested")

        cancellations_queue = await channel.declare_queue(CANCELLATIONS_QUEUE, durable=True, arguments=_QUEUE_ARGS)
        await cancellations_queue.bind(exchange, routing_key="run.cancel.requested")

        approvals_queue = await channel.declare_queue(APPROVALS_QUEUE, durable=True, arguments=_QUEUE_ARGS)
        await approvals_queue.bind(exchange, routing_key="approval.decided")

        await run_requests_queue.consume(
            functools.partial(_handle, redis_client=redis_client, handler=pipeline.handle_run_requested)
        )
        await cancellations_queue.consume(
            functools.partial(_handle, redis_client=redis_client, handler=pipeline.handle_cancel_requested)
        )
        await approvals_queue.consume(
            functools.partial(_handle, redis_client=redis_client, handler=pipeline.handle_approval_decided)
        )

        logger.info(
            "talos-orchestrator consuming %s, %s, and %s", RUN_REQUESTS_QUEUE, CANCELLATIONS_QUEUE, APPROVALS_QUEUE
        )
        try:
            await asyncio.Future()  # run until cancelled
        finally:
            await api_client.aclose()
            await runner_client.aclose()
            await run_lock.aclose()
            await redis_client.aclose()


def main() -> None:
    logging.basicConfig(level=logging.INFO)
    asyncio.run(_amain())
