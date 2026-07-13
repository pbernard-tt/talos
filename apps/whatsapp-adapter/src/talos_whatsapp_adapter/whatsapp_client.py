# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

"""Thin httpx wrapper for the WhatsApp Business Cloud API
(https://developers.facebook.com/docs/whatsapp/cloud-api/reference/messages) plus the
X-Hub-Signature-256 webhook signature check every inbound request must pass before anything else
runs (https://developers.facebook.com/docs/graph-api/webhooks/getting-started#validating-payloads).
The access token and app secret are masked in every log path (never interpolated into a log message)."""

from __future__ import annotations

import hashlib
import hmac

import httpx


def verify_signature(body: bytes, signature_header: str | None, app_secret: str) -> bool:
    if not signature_header or not signature_header.startswith("sha256=") or not app_secret:
        return False
    expected = hmac.new(app_secret.encode("utf-8"), body, hashlib.sha256).hexdigest()
    provided = signature_header[len("sha256="):]
    return hmac.compare_digest(expected, provided)


class WhatsAppClient:
    def __init__(self, access_token: str, phone_number_id: str, graph_api_base_url: str) -> None:
        self._client = httpx.AsyncClient(
            base_url=f"{graph_api_base_url}/{phone_number_id}",
            headers={"Authorization": f"Bearer {access_token}"},
            timeout=30.0,
        )

    async def aclose(self) -> None:
        await self._client.aclose()

    async def send_message(self, wa_id: str, text: str) -> None:
        response = await self._client.post(
            "/messages",
            json={"messaging_product": "whatsapp", "to": wa_id, "type": "text", "text": {"body": text}},
        )
        response.raise_for_status()
