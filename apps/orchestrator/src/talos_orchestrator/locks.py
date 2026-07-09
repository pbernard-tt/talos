"""Redis lock helpers (Section 6.3/11): talos:lock:run:{project_id}:{base_branch}, TTL = run
timeout, released on terminal state. A second concurrent start on the same project/branch is
rejected while the lock is held.
"""

from __future__ import annotations

import redis.asyncio as redis

from talos_orchestrator.config import Settings

_RELEASE_SCRIPT = """
if redis.call("get", KEYS[1]) == ARGV[1] then
    return redis.call("del", KEYS[1])
else
    return 0
end
"""


class RunLock:
    def __init__(self, settings: Settings) -> None:
        self._redis = redis.from_url(settings.redis_url)

    async def aclose(self) -> None:
        await self._redis.aclose()

    @staticmethod
    def _key(project_id: str, base_branch: str) -> str:
        return f"talos:lock:run:{project_id}:{base_branch}"

    async def acquire(self, project_id: str, base_branch: str, run_id: str, ttl_seconds: int) -> bool:
        key = self._key(project_id, base_branch)
        return bool(await self._redis.set(key, run_id, nx=True, ex=ttl_seconds))

    async def release(self, project_id: str, base_branch: str, run_id: str) -> None:
        key = self._key(project_id, base_branch)
        # Compare-and-delete: never release a lock some other (later) run now owns -- e.g. this
        # run's TTL already expired and a new run acquired it before this release call ran.
        await self._redis.eval(_RELEASE_SCRIPT, 1, key, run_id)
