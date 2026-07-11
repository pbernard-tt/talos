"""Phase 11 (Section 8.3): periodic workspace retention job. Only the orchestrator can reach both
talos-api (for candidates) and talos-runner-supervisor (to delete workspace directories) -- per the
architecture's four communication paths, this can't live in talos-api itself.
"""

from __future__ import annotations

import asyncio
import logging
from collections import defaultdict

from talos_orchestrator.api_client import ApiClient
from talos_orchestrator.config import Settings
from talos_orchestrator.runner_client import RunnerClient

logger = logging.getLogger(__name__)


async def run_once(api_client: ApiClient, runner_client: RunnerClient, max_age_days: int) -> int:
    candidates = await api_client.get_retention_candidates(max_age_days)
    run_ids_by_project: dict[str, list[str]] = defaultdict(list)
    for candidate in candidates:
        run_ids_by_project[candidate["projectSlug"]].append(candidate["runId"])

    deleted = 0
    for project_slug, run_ids in run_ids_by_project.items():
        result = await runner_client.cleanup(project_slug, run_ids, max_age_days)
        deleted_run_ids = result.get("deletedRunIds", [])
        deleted += len(deleted_run_ids)
        # Phase 16: only for runs whose workspace was actually deleted -- a run left alone by
        # cleanup (e.g. younger than max_age_days) keeps its artifacts too.
        for run_id in deleted_run_ids:
            await api_client.delete_artifacts(run_id)
    return deleted


async def run_periodically(api_client: ApiClient, runner_client: RunnerClient, settings: Settings) -> None:
    while True:
        await asyncio.sleep(settings.retention_interval_seconds)
        try:
            deleted = await run_once(api_client, runner_client, settings.retention_max_age_days)
            if deleted:
                logger.info("retention: deleted %d workspace(s)", deleted)
        except Exception:  # noqa: BLE001 -- a failed sweep must not kill the periodic task loop
            logger.exception("retention sweep failed")
