# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

"""Core per-message handling for an inbound WhatsApp webhook POST, split out from app.py so it's
testable without spinning up FastAPI/lifespan (mirrors apps/telegram-adapter's poll_once)."""

from __future__ import annotations

import logging
from typing import Any

from talos_whatsapp_adapter.api_client import ApiClient
from talos_whatsapp_adapter.commands import CommandParseError, extract_messages, parse_command
from talos_whatsapp_adapter.config import Settings
from talos_whatsapp_adapter.handlers import handle_command
from talos_whatsapp_adapter.whatsapp_client import WhatsAppClient

logger = logging.getLogger(__name__)


async def process_webhook_payload(
    payload: dict[str, Any],
    api_client: ApiClient,
    whatsapp_client: WhatsAppClient,
    settings: Settings,
) -> None:
    for wa_id, text in extract_messages(payload):
        if wa_id not in settings.allowed_wa_ids:
            logger.warning("message from non-allow-listed WhatsApp ID %s ignored", wa_id)
            await api_client.record_rejected_sender("WHATSAPP", wa_id)
            continue
        try:
            command = parse_command(wa_id, text)
            reply = await handle_command(api_client, command)
        except CommandParseError as exc:
            reply = str(exc)
        except Exception:
            logger.exception("error handling command from WhatsApp ID %s", wa_id)
            reply = "Something went wrong handling that command."
        await whatsapp_client.send_message(wa_id, reply)
