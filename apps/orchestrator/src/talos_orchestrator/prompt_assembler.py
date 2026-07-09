"""Section 7.3's fixed, auditable prompt assembly."""

from __future__ import annotations

from pathlib import Path
from typing import Any

_MAX_CONTEXT_DOCUMENT_CHARS = 8_000


def assemble_prompt(task: dict[str, Any], project: dict[str, Any], active_config: dict[str, Any], workspace_path: str) -> str:
    """Build the four Section 7.3 prompt sections in their prescribed order."""
    rules = active_config.get("rules") or {}
    blocked_patterns = rules.get("forbidden") or []
    blocked = ", ".join(blocked_patterns) if blocked_patterns else "none"
    sections = [
        f"You are working in an isolated branch of {project['slug']}. Do not modify files matching: {blocked}. "
        "Do not run destructive commands. Stop when the task is complete.",
    ]
    workspace = Path(workspace_path).resolve()
    for configured_path in (active_config.get("context") or {}).get("docs") or []:
        path = (workspace / configured_path).resolve()
        if not path.is_relative_to(workspace) or not path.is_file():
            continue
        try:
            contents = path.read_text(errors="replace")[:_MAX_CONTEXT_DOCUMENT_CHARS]
        except OSError:
            continue
        sections.append(f"Project context ({configured_path}):\n{contents}")
    sections.append(f"Task title: {task['title']}\nTask description:\n{task.get('description') or ''}")
    sections.append("Make the necessary code changes. Do not commit; Talos handles commits.")
    return "\n\n".join(sections)
