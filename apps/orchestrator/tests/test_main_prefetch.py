import dataclasses

from talos_orchestrator.config import Settings
from talos_orchestrator.main import _prefetch_count

SETTINGS = Settings(
    api_base_url="http://api",
    internal_api_token="",
    rabbitmq_url="amqp://x",
    redis_url="redis://x",
    runner_base_url="http://runner",
    max_active_runs=1,
    run_timeout_minutes=60,
    worker_image_base="workers/base-agent-runner:latest",
    worker_image_java="workers/java-runner:latest",
    worker_image_node="workers/node-runner:latest",
    worker_image_python="workers/python-runner:latest",
    retention_max_age_days=7,
    retention_interval_seconds=21600,
)


def test_prefetch_count_matches_max_active_runs():
    settings = dataclasses.replace(SETTINGS, max_active_runs=5)

    assert _prefetch_count(settings) == 5


def test_prefetch_count_floors_at_one():
    settings = dataclasses.replace(SETTINGS, max_active_runs=0)

    assert _prefetch_count(settings) == 1
