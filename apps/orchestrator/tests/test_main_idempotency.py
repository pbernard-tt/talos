import json

from talos_orchestrator.main import _handle


class FakeMessage:
    def __init__(self, body: bytes) -> None:
        self.body = body

    def process(self, requeue: bool = True):
        return self

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, tb) -> bool:
        return False


class FakeRedis:
    def __init__(self) -> None:
        self._store: set[str] = set()

    async def set(self, key: str, value: str, nx: bool = False, ex: int | None = None):
        if nx and key in self._store:
            return None
        self._store.add(key)
        return True


async def test_handle_processes_new_event_once():
    redis_client = FakeRedis()
    calls = []

    async def handler(payload):
        calls.append(payload)

    envelope = json.dumps({"event_id": "e1", "payload": {"run_id": "r1"}}).encode()
    await _handle(FakeMessage(envelope), redis_client, handler)

    assert calls == [{"run_id": "r1"}]


async def test_handle_skips_duplicate_event_id():
    redis_client = FakeRedis()
    calls = []

    async def handler(payload):
        calls.append(payload)

    envelope = json.dumps({"event_id": "e1", "payload": {"run_id": "r1"}}).encode()
    await _handle(FakeMessage(envelope), redis_client, handler)
    await _handle(FakeMessage(envelope), redis_client, handler)

    assert calls == [{"run_id": "r1"}]  # second delivery was a no-op


async def test_handle_processes_distinct_event_ids_independently():
    redis_client = FakeRedis()
    calls = []

    async def handler(payload):
        calls.append(payload)

    await _handle(FakeMessage(json.dumps({"event_id": "e1", "payload": {"run_id": "r1"}}).encode()), redis_client, handler)
    await _handle(FakeMessage(json.dumps({"event_id": "e2", "payload": {"run_id": "r2"}}).encode()), redis_client, handler)

    assert calls == [{"run_id": "r1"}, {"run_id": "r2"}]
