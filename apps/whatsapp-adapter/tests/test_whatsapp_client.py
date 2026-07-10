import hashlib
import hmac

from talos_whatsapp_adapter.whatsapp_client import verify_signature


def _sign(body: bytes, app_secret: str) -> str:
    return "sha256=" + hmac.new(app_secret.encode("utf-8"), body, hashlib.sha256).hexdigest()


def test_verify_signature_accepts_correctly_signed_body():
    body = b'{"entry":[]}'
    signature = _sign(body, "app-secret")
    assert verify_signature(body, signature, "app-secret") is True


def test_verify_signature_rejects_wrong_secret():
    body = b'{"entry":[]}'
    signature = _sign(body, "app-secret")
    assert verify_signature(body, signature, "different-secret") is False


def test_verify_signature_rejects_tampered_body():
    body = b'{"entry":[]}'
    signature = _sign(body, "app-secret")
    assert verify_signature(b'{"entry":[{"tampered":true}]}', signature, "app-secret") is False


def test_verify_signature_rejects_missing_header():
    assert verify_signature(b"{}", None, "app-secret") is False


def test_verify_signature_rejects_header_without_sha256_prefix():
    assert verify_signature(b"{}", "deadbeef", "app-secret") is False


def test_verify_signature_rejects_when_app_secret_unconfigured():
    body = b'{"entry":[]}'
    signature = _sign(body, "")
    assert verify_signature(body, signature, "") is False
