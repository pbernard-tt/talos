# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

import hashlib
import hmac

from fastapi.testclient import TestClient

from talos_whatsapp_adapter.app import app


def _sign(body: bytes, app_secret: str) -> str:
    return "sha256=" + hmac.new(app_secret.encode("utf-8"), body, hashlib.sha256).hexdigest()


def test_health_returns_ok():
    with TestClient(app) as client:
        response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_webhook_verification_handshake_succeeds_with_correct_token(monkeypatch):
    monkeypatch.setenv("TALOS_WHATSAPP_VERIFY_TOKEN", "verify-me")
    with TestClient(app) as client:
        response = client.get(
            "/webhook", params={"hub.mode": "subscribe", "hub.verify_token": "verify-me", "hub.challenge": "12345"}
        )
    assert response.status_code == 200
    assert response.text == "12345"


def test_webhook_verification_handshake_rejects_wrong_token(monkeypatch):
    monkeypatch.setenv("TALOS_WHATSAPP_VERIFY_TOKEN", "verify-me")
    with TestClient(app) as client:
        response = client.get(
            "/webhook", params={"hub.mode": "subscribe", "hub.verify_token": "wrong", "hub.challenge": "12345"}
        )
    assert response.status_code == 403


def test_webhook_post_rejects_unsigned_body(monkeypatch):
    monkeypatch.setenv("TALOS_WHATSAPP_APP_SECRET", "app-secret")
    with TestClient(app) as client:
        response = client.post("/webhook", content=b'{"entry":[]}')
    assert response.status_code == 403


def test_webhook_post_rejects_body_signed_with_wrong_secret(monkeypatch):
    monkeypatch.setenv("TALOS_WHATSAPP_APP_SECRET", "app-secret")
    body = b'{"entry":[]}'
    signature = _sign(body, "not-the-real-secret")
    with TestClient(app) as client:
        response = client.post("/webhook", content=body, headers={"X-Hub-Signature-256": signature})
    assert response.status_code == 403


def test_webhook_post_accepts_correctly_signed_body_with_no_messages(monkeypatch):
    monkeypatch.setenv("TALOS_WHATSAPP_APP_SECRET", "app-secret")
    body = b'{"entry":[]}'
    signature = _sign(body, "app-secret")
    with TestClient(app) as client:
        response = client.post("/webhook", content=body, headers={"X-Hub-Signature-256": signature})
    assert response.status_code == 200
