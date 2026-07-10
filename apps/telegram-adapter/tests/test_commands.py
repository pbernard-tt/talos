import json
from pathlib import Path

import jsonschema
import pytest

from talos_telegram_adapter.commands import CommandParseError, extract_message, parse_command

_SCHEMA_PATH = Path(__file__).resolve().parents[3] / "packages" / "contracts" / "chat" / "inbound-command.schema.json"
_SCHEMA = json.loads(_SCHEMA_PATH.read_text())


def _validate(command: dict) -> None:
    jsonschema.validate(command, _SCHEMA)


def test_extract_message_returns_chat_id_and_text():
    update = {"update_id": 1, "message": {"chat": {"id": 42}, "text": "/approvals"}}
    assert extract_message(update) == ("42", "/approvals")


def test_extract_message_returns_none_for_non_message_update():
    assert extract_message({"update_id": 1, "edited_message": {}}) is None
    assert extract_message({"update_id": 1, "message": {"chat": {"id": 1}}}) is None


def test_create_task_parses_project_title_and_description():
    command = parse_command("42", "/create_task Talos :: Fix the bug :: See stack trace")
    assert command == {
        "channel": "TELEGRAM",
        "chat_id": "42",
        "command": "CREATE_TASK",
        "project_name": "Talos",
        "title": "Fix the bug",
        "description": "See stack trace",
    }
    _validate(command)


def test_create_task_without_description_omits_the_field():
    command = parse_command("42", "/create_task Talos :: Fix the bug")
    assert "description" not in command
    _validate(command)


def test_create_task_missing_title_raises_usage_error():
    with pytest.raises(CommandParseError, match="Usage: /create_task"):
        parse_command("42", "/create_task Talos")


def test_task_status_parses_task_id():
    command = parse_command("42", "/task_status 0198f000-0000-7000-8000-000000000000")
    assert command["command"] == "TASK_STATUS"
    assert command["task_id"] == "0198f000-0000-7000-8000-000000000000"
    _validate(command)


def test_task_status_missing_id_raises_usage_error():
    with pytest.raises(CommandParseError, match="Usage: /task_status"):
        parse_command("42", "/task_status")


def test_run_status_parses_run_id():
    command = parse_command("42", "/run_status 0198f000-0000-7000-8000-000000000001")
    assert command["command"] == "RUN_STATUS"
    _validate(command)


def test_list_approvals():
    command = parse_command("42", "/approvals")
    assert command == {"channel": "TELEGRAM", "chat_id": "42", "command": "LIST_APPROVALS"}
    _validate(command)


def test_unknown_command_raises_helpful_error():
    with pytest.raises(CommandParseError, match="Unknown command"):
        parse_command("42", "/whoami")
