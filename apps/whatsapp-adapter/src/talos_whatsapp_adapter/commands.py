# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

"""Parses a WhatsApp Cloud API inbound webhook payload into the normalized inbound-command shape
defined in packages/contracts/chat/inbound-command.schema.json. Deterministic slash-command parsing
only -- Talos orchestrates agents, it does not add its own NLU/LLM layer for chat intake (hard
constraint 1). Shares the same command grammar as apps/telegram-adapter (Section 16 Track B: "the
same command schema")."""

from __future__ import annotations

from typing import Any

_USAGE = {
    "create_task": "Usage: /create_task <project name> :: <title> :: [description]",
    "task_status": "Usage: /task_status <task-id>",
    "run_status": "Usage: /run_status <run-id>",
}


class CommandParseError(Exception):
    pass


def extract_messages(webhook_body: dict[str, Any]) -> list[tuple[str, str]]:
    """Returns (wa_id, text) for every plain-text message in a WhatsApp webhook POST body.
    https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks/payload-examples"""
    results: list[tuple[str, str]] = []
    for entry in webhook_body.get("entry", []):
        for change in entry.get("changes", []):
            for message in change.get("value", {}).get("messages", []):
                if message.get("type") != "text":
                    continue
                wa_id = message.get("from")
                text = message.get("text", {}).get("body")
                if wa_id and text:
                    results.append((wa_id, text))
    return results


def parse_command(chat_id: str, text: str) -> dict[str, Any]:
    """Returns a dict matching the inbound-command schema, or raises CommandParseError with a
    user-facing usage message."""
    text = text.strip()
    if text.startswith("/create_task"):
        parts = [part.strip() for part in text[len("/create_task"):].split("::")]
        if len(parts) < 2 or not parts[0] or not parts[1]:
            raise CommandParseError(_USAGE["create_task"])
        command: dict[str, Any] = {
            "channel": "WHATSAPP",
            "chat_id": chat_id,
            "command": "CREATE_TASK",
            "project_name": parts[0],
            "title": parts[1],
        }
        if len(parts) > 2 and parts[2]:
            command["description"] = parts[2]
        return command

    if text.startswith("/task_status"):
        arg = text[len("/task_status"):].strip()
        if not arg:
            raise CommandParseError(_USAGE["task_status"])
        return {"channel": "WHATSAPP", "chat_id": chat_id, "command": "TASK_STATUS", "task_id": arg}

    if text.startswith("/run_status"):
        arg = text[len("/run_status"):].strip()
        if not arg:
            raise CommandParseError(_USAGE["run_status"])
        return {"channel": "WHATSAPP", "chat_id": chat_id, "command": "RUN_STATUS", "run_id": arg}

    if text.startswith("/approvals"):
        return {"channel": "WHATSAPP", "chat_id": chat_id, "command": "LIST_APPROVALS"}

    raise CommandParseError(
        "Unknown command. Available: /create_task, /task_status, /run_status, /approvals."
    )
