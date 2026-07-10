import json
from pathlib import Path

import jsonschema
import pytest

from talos_whatsapp_adapter.commands import CommandParseError, extract_messages, parse_command

_SCHEMA_PATH = Path(__file__).resolve().parents[3] / "packages" / "contracts" / "chat" / "inbound-command.schema.json"
_SCHEMA = json.loads(_SCHEMA_PATH.read_text())


def _validate(command: dict) -> None:
    jsonschema.validate(command, _SCHEMA)


def _webhook_body(wa_id: str, text: str) -> dict:
    return {
        "entry": [
            {
                "changes": [
                    {
                        "value": {
                            "messages": [
                                {"from": wa_id, "type": "text", "text": {"body": text}},
                            ]
                        }
                    }
                ]
            }
        ]
    }


def test_extract_messages_returns_sender_and_text():
    body = _webhook_body("15551234567", "/approvals")
    assert extract_messages(body) == [("15551234567", "/approvals")]


def test_extract_messages_ignores_non_text_messages():
    body = {
        "entry": [{"changes": [{"value": {"messages": [{"from": "1", "type": "image", "image": {"id": "x"}}]}}]}]
    }
    assert extract_messages(body) == []


def test_extract_messages_returns_empty_list_for_status_update_payload():
    body = {"entry": [{"changes": [{"value": {"statuses": [{"id": "wamid.1", "status": "delivered"}]}}]}]}
    assert extract_messages(body) == []


def test_create_task_parses_project_title_and_description():
    command = parse_command("1", "/create_task Talos :: Fix the bug :: See stack trace")
    assert command == {
        "channel": "WHATSAPP",
        "chat_id": "1",
        "command": "CREATE_TASK",
        "project_name": "Talos",
        "title": "Fix the bug",
        "description": "See stack trace",
    }
    _validate(command)


def test_create_task_missing_title_raises_usage_error():
    with pytest.raises(CommandParseError, match="Usage: /create_task"):
        parse_command("1", "/create_task Talos")


def test_task_status_parses_task_id():
    command = parse_command("1", "/task_status abc-123")
    assert command["command"] == "TASK_STATUS"
    _validate(command)


def test_run_status_parses_run_id():
    command = parse_command("1", "/run_status abc-123")
    assert command["command"] == "RUN_STATUS"
    _validate(command)


def test_list_approvals():
    command = parse_command("1", "/approvals")
    assert command == {"channel": "WHATSAPP", "chat_id": "1", "command": "LIST_APPROVALS"}
    _validate(command)


def test_unknown_command_raises_helpful_error():
    with pytest.raises(CommandParseError, match="Unknown command"):
        parse_command("1", "/whoami")
