# Security model

Threat model, policy enforcement points, and abuse-case coverage (Sections 12 and 8.5 of the implementation plan). Written in Phase 11 alongside per-run Docker execution and the security hardening sweep.

## 1. Policy enforcement points (Section 12.1, recap)

Talos cannot intercept individual shell commands executed inside a third-party agent process, so enforcement happens at four real points, not a `checkCommand(cmd)` interceptor:

| Point | When | Mechanism |
|---|---|---|
| Pre-run | Before workspace prep | Validate task/config; refuse runs whose config or prompt targets blocked paths; verify approval requirements for `risk_level=HIGH` tasks. |
| Container/process level (**the hard boundary**) | During the run | Phase 11 replaced "subprocess + resource limits" with real per-run Docker containers -- see Section 2 below. |
| Agent-native hooks | During the run, where supported | `ClaudeCodeAdapter` configures Claude Code permission settings/hooks in the provider home to deny matching commands natively. |
| Post-run scan (**the review gate**) | REVIEWING state | Captured diff scanned against `files.blocked_patterns`/`approval_required_patterns`; captured command log scanned against command patterns. Matches set `review_status=RISK_FLAGGED` and the Review Center shows exactly what matched. |

Command block/approval lists are *advisory* except where enforced natively or at the container boundary; their guaranteed effect is flagging the run for mandatory human scrutiny before anything is pushed or deployed.

## 2. Phase 11: the container isolation boundary

Before this phase, `CustomShellAdapter`/`ClaudeCodeAdapter` spawned the agent as a plain subprocess *inside* `talos-runner-supervisor`'s own container -- a compromised or malicious agent process could read anything the supervisor process could read, not just its own workspace. Phase 11 wraps every run in its own `docker run` container (`packages/agent-adapter-spec/src/talos_agent_adapter_spec/container.py`), built from a `workers/*` image matched to the project's `stackType`.

**Design:**

- `talos-runner-supervisor` is the *only* container that mounts `/var/run/docker.sock` (it needs it to launch per-run containers). Every container it spawns gets **no** Docker socket, no Docker CLI, and no host filesystem access -- verified live and by `packages/agent-adapter-spec/tests/test_container_execution.py::test_containerized_run_has_no_docker_socket_or_cli`.
- Each container mounts only *its own* run's worktree subpath via `--mount type=volume,...,volume-subpath=...` (Docker 25+) -- not the whole shared `talos_workspaces` volume. A sibling run's planted `.env` is not merely permission-denied, it is completely absent from the container's filesystem view. Verified by `test_containerized_run_cannot_see_a_sibling_runs_planted_env_file`.
- `--cap-drop ALL`, `--security-opt no-new-privileges`, `--memory`, `--cpus`, `--pids-limit` (env-var-configurable, `TALOS_RUN_*`) on every container.
- Each container joins the isolated `talos_run_network` instead of the compose default network: no route to postgres/rabbitmq/redis/api, only normal outbound internet (needed for `npm install`/`mvnw`/etc. via Docker's default NAT). Verified live: `nc` to `talos-postgres:5432` and `talos-api:8080` both fail to resolve from inside a per-run container; outbound HTTPS succeeds.
- Secrets reach the container via `--env-file` (a temp file on the supervisor's own filesystem, deleted once the container has started), never `-e` argv, which `ps` on the host would expose.
- Timeout kill: `docker run`'s default `--sig-proxy=true` forwards the adapter's existing SIGTERM/SIGKILL process-group signals into the container; an unconditional `docker kill talos-run-{run_id}` runs as a safety net regardless. `--rm` guarantees no orphaned containers survive a kill. Verified by `test_containerized_run_is_hard_killed_at_timeout`.

**Accepted residual risks (documented deviations, not gaps to silently ignore):**

1. `docker inspect`/`docker exec` from anyone who already has host Docker-daemon access (i.e., already has the same privilege as `talos-runner-supervisor` itself) can still see a running container's env vars. Docker has no way to fully hide this short of Swarm secrets, which is out of scope for this MVP. The masking sweep (Section 3) covers a different boundary: logs, transcripts, and the audit trail, which *are* fully covered.
2. No per-project network allow/deny egress policy -- every per-run container gets the same outbound-internet-allowed, internal-services-denied network. A full egress ACL system is flagged as post-MVP; `talos_run_network`'s blanket isolation from postgres/rabbitmq/redis/api is the load-bearing control for this pass.
3. `talos-runner-supervisor` itself is deliberately **not** attached to `talos_run_network` (unlike every other service, which the plan's own Docker Compose docs might suggest would be the "natural" way to get Compose to create the network) -- attaching it there would let a compromised per-run container reach the supervisor's own HTTP API, which has no request-level auth of its own (see Section 6). Instead, the supervisor creates `talos_run_network` lazily via its already-held Docker socket (`container.py::ensure_network`), the same privilege boundary as everything else it does.

## 3. Secret handling and log masking

- `secret_values` table: AES-256-GCM with `TALOS_SECRETS_KEY`, accessed only by `dev.talos.secrets`; `secret_ref` UUIDs everywhere else; never returned by any REST endpoint.
- A run receives only env vars explicitly listed for its project and approved; delivered process-env (or, since Phase 11, `--env-file`) only, never written to disk inside the workspace.
- Every log path (runner, orchestrator, API) replaces occurrences of any injected secret value with `***` before persistence -- covered by `test_env_values_never_appear_in_emitted_events` (both adapters) and, for the containerized path, `test_containerized_run_masks_secrets_in_emitted_events`.
- Provider credentials live in isolated `provider_home` directories outside every run's workspace, mounted read/write only into that agent's own containers (shared across runs of the *same* agent, never across agents).

## 4. Rate limiting (Phase 11)

`POST /api/v1/auth/login` is the one unauthenticated endpoint (everything else already requires a JWT or the internal service token), making it the most brute-forceable surface. `dev.talos.auth.LoginRateLimitFilter` enforces a Redis-backed fixed-window counter (`INCR` + `EXPIRE`, reusing the Redis dependency already load-bearing for locks/SSE -- no new library) keyed by client IP (`X-Forwarded-For`'s first hop when present, since Talos is deployed behind Dokploy/Traefik; falls back to the raw connection address otherwise).

- Default: `TALOS_LOGIN_RATE_LIMIT_MAX_ATTEMPTS=10` per `TALOS_LOGIN_RATE_LIMIT_WINDOW_SECONDS=60`.
- Exceeding the limit returns `429` with the same `{error: {code, message}}` envelope as every other error response (`RATE_LIMITED`).
- **Accepted residual risk:** if talos-api is ever exposed directly without a reverse proxy in front (i.e., not the documented Dokploy/Traefik deployment topology), a caller could spoof a fresh `X-Forwarded-For` value per request and evade the limiter entirely. Out of scope for this pass -- flagged here rather than silently assumed away.
- A blanket API-wide limiter was deliberately not built: every other endpoint already requires auth, and a broad limiter risks throttling legitimate SSE/polling traffic.

## 5. Workspace retention

Section 8.3's rule -- terminal runs (`COMPLETED`/`FAILED`/`REJECTED`/`CANCELLED`) with no `OPEN` pull request, older than the retention window -- is now actually enforced end to end, not just exposed as an unused cleanup endpoint:

- `talos-api`: `GET /internal/v1/runs/retention-candidates?maxAgeDays=` (`RunService.getRetentionCandidates`) queries terminal runs older than the cutoff and excludes any with an `OPEN` `pull_requests` row.
- `talos-orchestrator`: a periodic asyncio task (`retention.py::run_periodically`, default every `TALOS_RETENTION_INTERVAL_SECONDS=21600`) calls that endpoint, groups candidates by project, and calls `talos-runner-supervisor`'s existing `POST /workspaces/cleanup` per project to actually delete the workspace directories.
- This job lives in the orchestrator, not `talos-api`, because only the orchestrator can reach both `talos-api` and `talos-runner-supervisor` (Section 4.3's four communication paths) -- `talos-api` has no path to the runner supervisor at all.

## 6. Runner supervisor's own auth boundary (closed 2026-07-11, initial review #5)

`talos-runner-supervisor` authenticates every endpoint except `/health` with the shared service token: `app.require_internal_token` (a FastAPI dependency on each route) compares `X-Talos-Internal-Token` against `Settings.internal_api_token` in constant time, returning 401 on a missing/wrong token and failing closed with 503 when the token is unconfigured. `talos-orchestrator`'s `RunnerClient` sends the header on every call — it already held `TALOS_INTERNAL_API_TOKEN` for talos-api's `/internal/v1`, so no new secret was introduced. Network topology remains the outer boundary (only the orchestrator can reach the supervisor on the internal Docker network, and per-run containers stay off that path — Section 2), but the token now also removes the "anything on the internal network — or on the dev host, where the dev compose publishes `8081:8081` — can drive the runner" class of risk. History: this was Phase 11's disclosed deviation 1 (network-topology-only), closed by the initial-review pass.

## 7. CI dependency and container scanning (Phase 11)

`.github/workflows/ci.yml` gained two report-only jobs (`exit-code: '0'` -- not hard-blocking PRs on pre-existing base-image CVEs unrelated to Talos's own code, a deliberate MVP choice):

- `dependency-scan`: Trivy filesystem scan of the whole repo.
- `container-scan`: Trivy image scan of all 8 built images (`talos-web`, `talos-api`, `talos-orchestrator`, `talos-runner-supervisor`, and the 4 `workers/*` images).

**A note on `trivy-action` itself:** in March 2026, a supply-chain attack on `aquasecurity/trivy-action` force-pushed 76 of 77 version tags (and all `setup-trivy` tags) to a malicious commit that stole CI/CD secrets from anyone whose workflow referenced a mutable tag like `@0.28.0`. Every `trivy-action` reference in `ci.yml` is pinned to the confirmed-safe commit SHA for v0.35.0 (`57a97c7e7821a5776cebc9bb87c984fa69cba8f1`), not a tag, precisely because tags can be rewritten. Verified locally before wiring into CI: `docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:0.69.3 image --exit-code 0 --severity CRITICAL,HIGH talos-runner-supervisor:latest` ran cleanly and surfaced real (pre-existing, base-image) CVEs, confirming the scan approach works before trusting it in CI.

## 8. Backup and restore drill (executed 2026-07-10)

Performed once against the running dev Postgres (`talos-postgres`, database `talos`), per Phase 11's acceptance criteria ("restore drill documented"):

```
$ docker exec talos-postgres psql -U talos -d talos -tAc "select 'users', count(*) from users
    union all select 'projects', count(*) from projects
    union all select 'tasks', count(*) from tasks
    union all select 'agent_runs', count(*) from agent_runs
    union all select 'audit_events', count(*) from audit_events;"
users|1
projects|6
tasks|6
agent_runs|6
audit_events|139

# 1. Backup: pg_dump in custom format, copied out of the container
$ docker exec talos-postgres pg_dump -U talos -d talos -F c -f /tmp/talos-backup.dump
$ docker cp talos-postgres:/tmp/talos-backup.dump ./talos-backup.dump
-> 130791 bytes

# 2. Restore: fresh scratch Postgres 17 + pgvector container, no shared state with talos-postgres
$ docker run -d --name talos-restore-drill -e POSTGRES_DB=talos -e POSTGRES_USER=talos \
    -e POSTGRES_PASSWORD=talos pgvector/pgvector:pg17
$ docker cp ./talos-backup.dump talos-restore-drill:/tmp/talos-backup.dump
$ docker exec talos-restore-drill pg_restore -U talos -d talos --no-owner --no-privileges \
    /tmp/talos-backup.dump
-> completed with no errors

# 3. Verify: identical row counts on the restored instance
$ docker exec talos-restore-drill psql -U talos -d talos -tAc "select 'users', count(*) from users
    union all select 'projects', count(*) from projects
    union all select 'tasks', count(*) from tasks
    union all select 'agent_runs', count(*) from agent_runs
    union all select 'audit_events', count(*) from audit_events;"
users|1
projects|6
tasks|6
agent_runs|6
audit_events|139

# 4. Spot-check a specific row survived intact
$ docker exec talos-restore-drill psql -U talos -d talos -tAc "select email, role from users;"
admin@talos.local|OWNER

# 5. Teardown
$ docker rm -f talos-restore-drill
$ rm ./talos-backup.dump
```

Result: **PASS**. All table row counts matched exactly between the original and restored databases, and a spot-checked row (the seeded admin user) was byte-for-byte intact. The production equivalent of step 1 (`pg_dump`) should run on a schedule against the Dokploy-managed `talos_postgres_data` volume; this drill validates the *mechanism* (custom-format dump/restore round-trips cleanly), not a specific automated schedule, which is out of scope for this phase.
