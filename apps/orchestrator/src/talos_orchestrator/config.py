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


def load_settings() -> Settings:
    return Settings(
        api_base_url=os.environ.get("TALOS_API_BASE_URL", "http://talos-api:8080"),
        internal_api_token=os.environ.get("TALOS_INTERNAL_API_TOKEN", ""),
        rabbitmq_url=os.environ.get("TALOS_RABBITMQ_URL", "amqp://talos:talos@rabbitmq:5672"),
        redis_url=os.environ.get("TALOS_REDIS_URL", "redis://redis:6379"),
        runner_base_url=os.environ.get("TALOS_RUNNER_BASE_URL", "http://talos-runner-supervisor:8081"),
        max_active_runs=int(os.environ.get("TALOS_MAX_ACTIVE_RUNS", "1")),
        run_timeout_minutes=int(os.environ.get("TALOS_RUN_TIMEOUT_MINUTES", "60")),
    )
