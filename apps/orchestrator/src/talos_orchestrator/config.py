"""Env var reference: Appendix A "talos-orchestrator" of the implementation plan."""

from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    api_base_url: str
    internal_api_token: str
    rabbitmq_url: str
    redis_url: str
    runner_base_url: str
    max_active_runs: int
    run_timeout_minutes: int
    # Phase 11 (Section 8/12.1): per-run Docker container image resolved from project.stackType.
    worker_image_base: str
    worker_image_java: str
    worker_image_node: str
    worker_image_python: str
    # Phase 11: periodic workspace retention job (Section 8.3's cleanup rule).
    retention_max_age_days: int
    retention_interval_seconds: int


def load_settings() -> Settings:
    return Settings(
        api_base_url=os.environ.get("TALOS_API_BASE_URL", "http://talos-api:8080"),
        internal_api_token=os.environ.get("TALOS_INTERNAL_API_TOKEN", ""),
        rabbitmq_url=os.environ.get("TALOS_RABBITMQ_URL", "amqp://talos:talos@rabbitmq:5672"),
        redis_url=os.environ.get("TALOS_REDIS_URL", "redis://redis:6379"),
        runner_base_url=os.environ.get("TALOS_RUNNER_BASE_URL", "http://talos-runner-supervisor:8081"),
        max_active_runs=int(os.environ.get("TALOS_MAX_ACTIVE_RUNS", "1")),
        run_timeout_minutes=int(os.environ.get("TALOS_RUN_TIMEOUT_MINUTES", "60")),
        worker_image_base=os.environ.get("TALOS_WORKER_IMAGE_BASE", "workers/base-agent-runner:latest"),
        worker_image_java=os.environ.get("TALOS_WORKER_IMAGE_JAVA", "workers/java-runner:latest"),
        worker_image_node=os.environ.get("TALOS_WORKER_IMAGE_NODE", "workers/node-runner:latest"),
        worker_image_python=os.environ.get("TALOS_WORKER_IMAGE_PYTHON", "workers/python-runner:latest"),
        retention_max_age_days=int(os.environ.get("TALOS_RETENTION_MAX_AGE_DAYS", "7")),
        retention_interval_seconds=int(os.environ.get("TALOS_RETENTION_INTERVAL_SECONDS", str(6 * 60 * 60))),
    )
