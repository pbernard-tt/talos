# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

"""Bootstrap: the Telegram long-poll loop (inbound task intake) and the talos.events notifier
consumer run concurrently (Section 16 Phase 12 Track B)."""

from __future__ import annotations

import asyncio
import logging

import aio_pika
import redis.asyncio as redis

from talos_telegram_adapter.api_client import ApiClient
from talos_telegram_adapter.commands import CommandParseError, extract_message, parse_command
from talos_telegram_adapter.config import Settings, load_settings
from talos_telegram_adapter.handlers import handle_command
from talos_telegram_adapter.notifier import start_consuming
from talos_telegram_adapter.telegram_client import TelegramClient

logger = logging.getLogger(__name__)


async def poll_once(
    telegram_client: TelegramClient,
    api_client: ApiClient,
    settings: Settings,
    offset: int | None,
) -> int | None:
    """Processes one batch of updates, returning the next offset. Split out from the infinite loop
    so tests can drive a bounded number of iterations."""
    updates = await telegram_client.get_updates(offset)
    for update in updates:
        offset = update["update_id"] + 1
        extracted = extract_message(update)
        if extracted is None:
            continue
        chat_id, text = extracted
        if chat_id not in settings.allowed_chat_ids:
            logger.warning("message from non-allow-listed chat %s ignored", chat_id)
            await api_client.record_rejected_sender("TELEGRAM", chat_id)
            continue
        try:
            command = parse_command(chat_id, text)
            reply = await handle_command(api_client, command)
        except CommandParseError as exc:
            reply = str(exc)
        except Exception:
            logger.exception("error handling command from chat %s", chat_id)
            reply = "Something went wrong handling that command."
        await telegram_client.send_message(chat_id, reply)
    return offset


async def _poll_loop(telegram_client: TelegramClient, api_client: ApiClient, settings: Settings) -> None:
    offset: int | None = None
    while True:
        offset = await poll_once(telegram_client, api_client, settings, offset)


def is_configured(settings: Settings) -> bool:
    return bool(settings.bot_token) and bool(settings.allowed_chat_ids)


async def _amain() -> None:
    settings = load_settings()
    if not is_configured(settings):
        # Mirrors AdminSeeder's "skip silently if unconfigured" -- but this is a long-running
        # service, not a one-shot seeder, so it idles rather than exiting: Track B is optional
        # post-MVP (Section 16), and a `docker compose up` with it unconfigured must still work,
        # not crash-loop.
        logger.warning(
            "TALOS_TELEGRAM_BOT_TOKEN/TALOS_TELEGRAM_ALLOWED_CHAT_IDS not set; talos-telegram-adapter idle"
        )
        await asyncio.Event().wait()
        return

    telegram_client = TelegramClient(settings.bot_token, settings.poll_timeout_seconds)
    api_client = ApiClient(settings)
    redis_client = redis.from_url(settings.redis_url)

    connection = await aio_pika.connect_robust(settings.rabbitmq_url)
    async with connection:
        await start_consuming(connection, telegram_client, redis_client, settings)
        try:
            await _poll_loop(telegram_client, api_client, settings)
        finally:
            await telegram_client.aclose()
            await api_client.aclose()
            await redis_client.aclose()


def main() -> None:
    logging.basicConfig(level=logging.INFO)
    asyncio.run(_amain())
