# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

import logging
from dataclasses import replace
from pathlib import Path

import httpx
import pytest

from talos_runner_supervisor.artifact_client import post_artifact


def _patch_transport(monkeypatch: pytest.MonkeyPatch, handler) -> list[httpx.Request]:
    calls: list[httpx.Request] = []

    async def recording_handler(request: httpx.Request) -> httpx.Response:
        calls.append(request)
        return await handler(request)

    original_client = httpx.AsyncClient
    monkeypatch.setattr(
        httpx, "AsyncClient", lambda **kwargs: original_client(transport=httpx.MockTransport(recording_handler), **kwargs)
    )
    return calls


async def _ok(request: httpx.Request) -> httpx.Response:
    return httpx.Response(201)


async def test_post_artifact_skips_when_no_token(settings, tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    path = tmp_path / "diff.patch"
    path.write_text("diff --git a b\n")
    calls = _patch_transport(monkeypatch, _ok)

    await post_artifact(replace(settings, internal_api_token=""), "run1", "DIFF_PATCH", "diff.patch", path, "text/x-diff")

    assert calls == []


async def test_post_artifact_skips_when_file_missing(settings, tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    missing = tmp_path / "does-not-exist.log"
    calls = _patch_transport(monkeypatch, _ok)

    await post_artifact(
        replace(settings, internal_api_token="test-token"), "run1", "TEST_REPORT", "test-report.log", missing, "text/plain"
    )

    assert calls == []


async def test_post_artifact_posts_multipart_with_service_token(settings, tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    path = tmp_path / "transcript.jsonl"
    path.write_text('{"type": "log"}\n')
    calls = _patch_transport(monkeypatch, _ok)

    await post_artifact(
        replace(settings, internal_api_token="test-token"),
        "run1",
        "TRANSCRIPT",
        "transcript.jsonl",
        path,
        "application/x-ndjson",
    )

    assert len(calls) == 1
    request = calls[0]
    assert request.url.path == "/internal/v1/runs/run1/artifacts"
    assert request.url.params["kind"] == "TRANSCRIPT"
    assert request.url.params["name"] == "transcript.jsonl"
    assert request.headers["X-Talos-Internal-Token"] == "test-token"


async def test_post_artifact_logs_and_swallows_http_errors(
    settings, tmp_path: Path, monkeypatch: pytest.MonkeyPatch, caplog: pytest.LogCaptureFixture
):
    path = tmp_path / "diff.patch"
    path.write_text("diff\n")

    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(500)

    _patch_transport(monkeypatch, handler)

    with caplog.at_level(logging.WARNING):
        await post_artifact(
            replace(settings, internal_api_token="test-token"), "run1", "DIFF_PATCH", "diff.patch", path, "text/x-diff"
        )

    assert "failed to post" in caplog.text
