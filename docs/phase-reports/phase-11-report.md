# Phase 11 report — Hardening and security

## What works

- **Per-run Docker execution.** `CustomShellAdapter`/`ClaudeCodeAdapter` now spawn the agent's
  command inside a per-run `docker run` container (`packages/agent-adapter-spec/src/talos_agent_adapter_spec/container.py`)
  instead of a plain subprocess inside `talos-runner-supervisor`'s own container. Each container:
  mounts only its own run's worktree subpath of the shared `talos_workspaces` volume via
  `--mount ...,volume-subpath=...` (Docker 25+) — a sibling run's files are completely absent from
  its filesystem view, not merely permission-denied; gets `--cap-drop ALL`,
  `--security-opt no-new-privileges`, and env-var-configurable `--memory`/`--cpus`/`--pids-limit`;
  joins the isolated `talos_run_network` (no route to postgres/rabbitmq/redis/api, normal outbound
  internet); receives secrets via `--env-file` (never `-e` argv, which `ps` on the host would
  expose); and is hard-killed on timeout via `docker run`'s `--sig-proxy` plus an unconditional
  `docker kill` safety net, with `--rm` guaranteeing no orphaned containers.
- **`talos-runner-supervisor` is the only container holding `/var/run/docker.sock`.** Its own
  Dockerfile was stripped down (Java/Gradle/Claude Code removed — that toolchain moved to
  `workers/base-agent-runner`/`java-runner`) to just Python 3.12-slim + `uv` + Docker CLI (the
  `docker-cli` Debian package alone, not `docker.io`, so no `dockerd`/`containerd`/`runc` bloat) +
  `git` + `curl`, and its `talos` user is now a fixed `uid=1000 gid=1000` (was
  `useradd --system`, an unpredictable UID) so files it writes into the shared named volumes stay
  writable by the per-run containers' `talos` user. Every per-run container it spawns gets **no**
  Docker socket, confirmed live and by `test_containerized_run_has_no_docker_socket_or_cli`.
- **`workers/{base-agent-runner,java-runner,node-runner,python-runner}` images**, each built and
  verified: `base-agent-runner` (git + Claude Code CLI, non-root uid 1000); `java-runner` (Temurin
  21 + Gradle 9.5.1 + Maven, honoring both the Phase-0 README stub and the Phase-7-proven Gradle
  toolchain); `node-runner` (Node 22); `python-runner` (Python 3.12 via `uv python install`, not
  apt, avoiding Debian bookworm's default 3.11).
- **`talos_workspaces`/`talos_provider_homes` volumes and `talos_run_network` are explicitly
  named** in both compose files (`name:` field) so they resolve identically regardless of Compose's
  project-name prefixing. `talos_run_network` is declared `external: true` and attached to no
  service — attaching `talos-runner-supervisor` itself would let a compromised per-run container
  reach the supervisor's own (unauthenticated — see Documented deviations) HTTP API. Instead the
  supervisor creates it lazily via its own Docker socket access
  (`container.py::ensure_network`), called once per containerized run (idempotent).
- **Retention job now actually runs.** `talos-api`'s new `GET /internal/v1/runs/retention-candidates`
  (`RunService.getRetentionCandidates`) returns terminal runs older than `maxAgeDays` with no `OPEN`
  pull request; `talos-orchestrator`'s new `retention.py` periodic task (default every 6h) calls it,
  groups by project, and calls the runner supervisor's existing (previously-unused)
  `POST /workspaces/cleanup` per project. Lives in the orchestrator because only it can reach both
  talos-api and the runner supervisor (Section 4.3's four communication paths).
- **Login rate limiting.** `dev.talos.auth.LoginRateLimitFilter`, a Redis-backed fixed-window
  counter (`INCR`+`EXPIRE`, no new dependency) keyed by client IP, wired ahead of
  `JwtAuthenticationFilter` in the `/api/v1/**` chain. `TALOS_LOGIN_RATE_LIMIT_MAX_ATTEMPTS=10` per
  `TALOS_LOGIN_RATE_LIMIT_WINDOW_SECONDS=60` by default; exceeding it returns `429 RATE_LIMITED` in
  the standard error envelope. Verified live: 10× wrong-password attempts return `401`, the 11th
  returns `429`.
- **CI dependency + container scanning.** `.github/workflows/ci.yml` gained `dependency-scan`
  (Trivy filesystem scan) and `container-scan` (Trivy image scan of all 8 built images: 4 apps + 4
  `workers/*`), both report-only (`exit-code: '0'`). Every `trivy-action` reference is pinned to a
  commit SHA, not a mutable tag — a March 2026 supply-chain attack force-pushed 76 of 77
  `trivy-action` tags to credential-stealing malware; pinning to the confirmed-safe v0.35.0 commit
  (`57a97c7e7821a5776cebc9bb87c984fa69cba8f1`) was verified necessary via a targeted web search
  before wiring it in, not assumed.
- **`docs/security-model.md`** is now a real threat model document (was a one-line stub): the four
  Section 12.1 enforcement points, the new container isolation boundary and its accepted residual
  risks, secret/masking summary, rate-limit and retention summaries, the CI scanning note above, and
  the backup/restore drill transcript (see Verification).
- **A real Debian-package gap was found and fixed during Dockerfile work, not by any test.**
  `apt-get install -y --no-install-recommends docker.io` on Debian trixie does *not* pull in the
  `docker` CLI binary — `docker-cli` is only a `Recommends:`, silently skipped by
  `--no-install-recommends`. Discovered live (`docker: not found` inside a freshly built
  `talos-runner-supervisor` image) and fixed by installing the standalone `docker-cli` package
  directly (which also drops the `dockerd`/`containerd`/`runc` dependency chain entirely —
  `docker-cli` alone depends only on `libc6`, and even `Breaks: docker.io`, confirmed via
  `apt-cache depends docker-cli` before switching).

## Documented deviations

1. **`talos-runner-supervisor`'s own HTTP endpoints have no request-level auth check.**
   `Settings.internal_api_token` exists but nothing in `app.py` enforces it (unlike `talos-api`'s
   `/internal/v1/**`, which does). The security boundary is network topology alone: only
   `talos-orchestrator` is meant to reach it, and per-run containers are kept off that path by
   design (never attaching the supervisor to `talos_run_network`). Documented in
   `docs/security-model.md` §6 as a known gap, not silently assumed away; adding real auth here is
   flagged as a follow-up since it isn't one of this phase's stated acceptance criteria and the
   topology boundary already holds given the container isolation work.
2. **No per-project network egress allow/deny policy.** Every per-run container gets the same
   `talos_run_network` (outbound internet allowed, internal services denied) — a full per-project
   ACL system is flagged post-MVP.
3. **`docker inspect`/`docker exec` from anyone who already holds host Docker-daemon access can
   still see a running per-run container's env vars**, despite `--env-file` avoiding `-e` argv
   exposure via `ps`. Accepted: that access level already equals `talos-runner-supervisor`'s own
   privilege. The masking sweep covers a different, fully-closed boundary: logs/transcripts/audit.
4. **Login rate limiter trusts `X-Forwarded-For`'s first hop.** Correct for the documented
   Dokploy/Traefik deployment topology; if talos-api were ever exposed directly without a reverse
   proxy in front, a caller could spoof a fresh IP per request and evade the limiter. Out of scope
   for this pass.
5. **Retention sweep interval/threshold are process-wide, not per-project.** Section 8.3 doesn't
   ask for per-project retention policy, so a single `TALOS_RETENTION_MAX_AGE_DAYS` applies
   globally.

## Stubbed / deferred

- Real request-level auth on `talos-runner-supervisor`'s own endpoints (deviation 1).
- Per-project network egress ACLs (deviation 2).
- A scheduled/automated production backup job — the drill validates the dump/restore *mechanism*
  round-trips cleanly; wiring a cron/Dokploy-scheduled `pg_dump` is an operational deployment
  concern, not application code, and wasn't asked for by this phase's acceptance criteria.
- `OpenCodeAdapter`, `CodexCliAdapter`, `OpenHandsAdapter` remain `NotImplementedError` stubs
  (unchanged — still Phase 12+ per the fixed adapter order).

## Verification

- `packages/agent-adapter-spec`: `sg docker -c "uv run pytest"` — 29 passed, including 5 new real
  (not mocked) integration tests against a live Docker daemon in `test_container_execution.py`
  (own-workspace read, sibling-run `.env` invisibility, no docker.sock/CLI, secret masking,
  hard-kill-at-timeout with `--rm` cleanup confirmed via `docker inspect`) plus
  `test_ensure_network_is_idempotent_and_creates_a_real_network`.
- `apps/runner-supervisor`: `uv run pytest` — 24 passed (container_image threading, `_volume_subpath`
  unit tests), no regressions.
- `apps/orchestrator`: `uv run pytest` — 26 passed (`container_image` resolution from
  `project.stackType` including the base-image fallback, `retention.run_once`'s grouping/cleanup-call
  behavior), no regressions.
- `apps/api`: `sg docker -c "./gradlew test"` — 123 passed, 0 failed, including the new
  `RunServiceRetentionCandidatesTest` (Mockito, terminal-run/OPEN-PR-exclusion/cutoff-Instant
  assertions) and `LoginRateLimitIntegrationTest` (real Testcontainers Redis, trips 429 after 3
  configured attempts). One real regression was caught and fixed here: `RunControllerIntegrationTest`
  (21 test methods, one shared Spring context/Redis) logged in fresh per test method, which tripped
  the new rate limiter around test #11 — fixed by caching the bearer token once per class run
  (legitimate test efficiency improvement, not a rate-limiter workaround).
- **Docker images:** `workers/{base-agent-runner,java-runner,node-runner,python-runner}` and the
  stripped `talos-runner-supervisor` all built and smoke-tested manually (`id`, `docker --version`,
  `git --version`, absence of `java`/`gradle`, Python 3.12.13 resolution).
- **Live walk against the full compose stack** (`sg docker -c "TALOS_DOCKER_GID=$(stat -c '%g'
  /var/run/docker.sock) docker compose -f infra/docker-compose.dev.yml up -d --build"` +
  `sg docker -c "./scripts/smoke.sh"`), capturing `docker events` during the run, confirmed the
  **real** end-to-end chain: talos-api → RabbitMQ → talos-orchestrator → talos-runner-supervisor
  spawned an actual `talos-run-{run_id}` container (`workers/base-agent-runner:latest`, since the
  smoke fixture's `stackType` matches no specific runner) — `container create` → `attach` → `start`
  → `die (exitCode=0)` → `destroy`, i.e. clean exit and `--rm` cleanup, all through production
  topology, not a manual curl test. Separately (manual `curl` against the deployed stack, before
  wiring the orchestrator side): confirmed a per-run container cannot resolve `talos-postgres` or
  `talos-api` by name (network isolation) while outbound internet (`wget` to a public host)
  succeeds; confirmed `NO_SOCKET`/no `docker` CLI inside a per-run container with `docker.sock`
  mounted only into the supervisor.
- **Retention job:** `GET /internal/v1/runs/retention-candidates?maxAgeDays=0` against the live
  stack returned real terminal runs from earlier phases' testing, correctly excluding ones with an
  `OPEN` PR. Manually invoked `talos_orchestrator.retention.run_once` inside the running
  `talos-orchestrator` container: completed without error (`deleted: 0` — the candidate runs'
  on-disk workspace directories no longer existed from earlier dev-cycle cleanup, a data-state
  artifact of accumulated manual testing across phases, not a code defect; the grouping/cleanup-call
  chain itself is additionally covered by `test_retention.py`'s unit tests with a fake runner
  client).
- **Rate limiter, live:** 10 consecutive wrong-password `POST /api/v1/auth/login` calls returned
  `401`; the 11th returned `429`.
- **Backup/restore drill, executed for real** against the dev Postgres — `pg_dump` (custom format)
  → fresh scratch `postgres:17` container with no shared state → `pg_restore` → row counts on 5
  tables (`users`, `projects`, `tasks`, `agent_runs`, `audit_events`) matched exactly, and the
  seeded admin user's email/role were byte-for-byte intact. Full transcript in
  `docs/security-model.md` §8. Scratch container and dump file cleaned up afterward.
- **Trivy scanning**, validated locally before trusting it in CI: `docker run --rm -v
  /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:0.69.3 image --exit-code 0 --severity
  CRITICAL,HIGH talos-runner-supervisor:latest` ran cleanly (exit 0) and surfaced real CVEs
  (pre-existing base-image ones — expected and out of scope to fix in this pass).
- Naming guard (`grep -ri agentos ...`) returns nothing.
- **Not checked:** a live deploy of the new images to a real Dokploy instance (no instance available,
  same limitation as Phase 10); the CI workflow's `workers-docker-build`/`container-scan` jobs
  themselves running on actual GitHub Actions infrastructure (validated by construction — matches
  the working local build commands and the already-passing `docker-build`/`smoke` job patterns —
  but not observed running on a real GitHub Actions runner this session).
