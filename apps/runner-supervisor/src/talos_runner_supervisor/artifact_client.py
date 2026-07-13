# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

"""Phase 16: posts run artifacts (diff.patch, transcript, test-report) directly to talos-api's
`/internal/v1/runs/{id}/artifacts`, using TALOS_INTERNAL_API_TOKEN -- Appendix A already reserved
this token for "artifact/log posts" (config.py). Best-effort: a failed post is logged, not raised,
since the artifact still exists on this service's own local filesystem regardless (until the next
retention sweep) and a storage-side hiccup must not fail the run step that produced it.
"""

from __future__ import annotations

import logging
from pathlib import Path

import httpx

from talos_runner_supervisor.config import Settings

logger = logging.getLogger(__name__)


async def post_artifact(settings: Settings, run_id: str, kind: str, name: str, path: Path, content_type: str) -> None:
    if not settings.internal_api_token or not path.exists():
        return
    try:
        async with httpx.AsyncClient(
            base_url=settings.api_base_url,
            headers={"X-Talos-Internal-Token": settings.internal_api_token},
            timeout=30.0,
        ) as client:
            with path.open("rb") as f:
                response = await client.post(
                    f"/internal/v1/runs/{run_id}/artifacts",
                    params={"kind": kind, "name": name},
                    files={"file": (name, f, content_type)},
                )
                response.raise_for_status()
    except httpx.HTTPError:
        logger.warning("failed to post %s artifact %r for run %s", kind, name, run_id, exc_info=True)
