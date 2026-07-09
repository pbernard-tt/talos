from fakes import FakeApiClient

from talos_orchestrator.log_batcher import LogBatcher

CONTEXT = {}  # LogBatcher only needs ingest_logs; FakeApiClient's other methods are unused here


async def test_flushes_when_buffer_reaches_flush_size():
    api_client = FakeApiClient(CONTEXT)
    batcher = LogBatcher(api_client, "run1", flush_size=3, flush_interval=999)

    for i in range(3):
        await batcher.add("STDOUT", f"line {i}", "2026-07-09T12:00:00Z")

    assert len(api_client.log_entries) == 3
    assert [e["sequence"] for e in api_client.log_entries] == [1, 2, 3]


async def test_does_not_flush_before_size_or_interval_reached():
    api_client = FakeApiClient(CONTEXT)
    batcher = LogBatcher(api_client, "run1", flush_size=50, flush_interval=999)

    await batcher.add("STDOUT", "line", "2026-07-09T12:00:00Z")

    assert api_client.log_entries == []


async def test_manual_flush_sends_buffered_entries():
    api_client = FakeApiClient(CONTEXT)
    batcher = LogBatcher(api_client, "run1", flush_size=50, flush_interval=999)

    await batcher.add("STDOUT", "line", "2026-07-09T12:00:00Z")
    await batcher.flush()

    assert len(api_client.log_entries) == 1


async def test_flush_with_empty_buffer_is_a_no_op():
    api_client = FakeApiClient(CONTEXT)
    batcher = LogBatcher(api_client, "run1")

    await batcher.flush()

    assert api_client.log_entries == []
