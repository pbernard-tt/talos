# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Talos is a self-hosted, web-based, agent-agnostic control plane that orchestrates existing coding agents (Claude Code, OpenCode, Codex CLI, OpenHands, ...) across a portfolio of software projects: project registry, Kanban task board, governed agent runs on isolated git worktrees, a diff/review center, and an approval-gated push/PR/deploy pipeline. Talos does not build a new coding agent or LLM — it orchestrates existing agents through the `AgentAdapter` interface in `packages/agent-adapter-spec`.

**Single source of truth:** `docs/Talos_Implementation_Plan.pdf` (Revision 2), authored in `docs/src/talos-implementation-plan.md`. Every table, endpoint, event, enum, and state transition in this codebase must match Sections 7–11 of that document verbatim. If a contract is ambiguous or contradicts observed reality (e.g. a CLI flag changed), stop and ask rather than guessing — do not invent architecture.

## Hard constraints

1. Never build a new LLM or coding agent — only orchestrate existing agents through `AgentAdapter`.
2. Nothing outside `packages/agent-adapter-spec` may couple to a specific agent provider; the orchestrator must run identically with `CustomShellAdapter`.
3. Adapter implementation order is fixed and exclusive: `CustomShellAdapter` (Phase 6) → `ClaudeCodeAdapter` (Phase 7). `OpenCodeAdapter`, `CodexCliAdapter`, `OpenHandsAdapter` remain `NotImplementedError` stubs until Phase 12.
4. Each app under `apps/` builds its own Docker image and is deployable alone. No shared runtime state except PostgreSQL, RabbitMQ, and Redis.
5. `talos-api` (Spring Boot) is the **only** writer to PostgreSQL. The orchestrator and runner supervisor mutate state exclusively through `/internal/v1` with the shared service token; neither Python service opens a database connection.
6. Safe defaults everywhere: runs execute only on `agent/task-<id>-<slug>` branches in isolated worktrees; nothing is pushed, PR'd, or deployed without an `APPROVED` approval row, enforced server-side and covered by a test.
7. Never expose production secrets to agent workers. Runners receive only enumerated injected env vars; provider credentials live in isolated provider homes outside every workspace; all injected values are masked in every log path.
8. No deploy trigger (Phase 10) before the review/approval flow (Phase 8) is complete and tested. Until then `DeployProvider` is an interface with a no-op implementation.
9. Naming is canonical — see below. Run `grep -ri agentos . --exclude-dir=.git --exclude-dir=docs --exclude-dir=.github --exclude=CLAUDE.md --exclude=AGENTS.md` before every commit; it must return nothing. (The unscoped command self-matches this rule's own text in `docs/` and this file — CI's `naming-guard` job uses the scoped form.)
10. `docker compose -f infra/docker-compose.dev.yml up` must work at the end of every phase.

## Process

Work strictly phase by phase (Section 16 of the plan, Phase 0 → 11). Within a phase, complete one ticket at a time (≤ half a day each). A phase is done only when its acceptance criteria pass, all tests are green, and `docs/phase-reports/phase-N-report.md` is written (what works, what is stubbed, deviations — deviations require sign-off before continuing). Write tests alongside code, not after the phase.

**`docs/initial_implementation_log.md`** — append one entry per completed ticket (or per meaningful change), newest entry at the top; never rewrite or delete earlier entries. Each entry: `## <date> — <short title>` heading, an **Ask**, and always a **Verification** section (exactly what was checked, with results, and what wasn't checked and why). Include only the other sections that are relevant (Root cause, Changed (backend)/(frontend) with per-file bullets naming the *why*, Coverage, Notes, Known blockers/follow-ups).

## Naming conventions

| Surface | Value |
|---|---|
| Product/UI | Talos |
| Java base package | `dev.talos` |
| Docker services | `talos-api`, `talos-web`, `talos-orchestrator`, `talos-runner-supervisor` |
| Env var prefix | `TALOS_` |
| Database name | `talos` |
| Queue exchange | `talos.events` (routing keys like `task.run.requested`) |
| Project config file | `talos.yaml` |

## Pinned stack

Angular 22 (standalone, signals, no NgRx, Material+CDK) · Spring Boot 4.1.x / Java 21 / Gradle (see deviation below) · PostgreSQL 17 · RabbitMQ 4.1 (topic exchange, quorum queues) · Redis 7 · Python 3.12 + `uv` (orchestrator: `aio-pika`+`httpx`; runner supervisor: FastAPI+uvicorn) · OpenAPI 3.1 + per-event JSON Schema in `packages/contracts` · UUID v7 IDs · `TIMESTAMPTZ` UTC everywhere · SSE (no WebSockets) for live updates. Full rationale and exact verified versions: `docs/architecture.md`.

**Approved deviation:** the plan pins Maven for `apps/api`; this repo uses **Gradle with Kotlin DSL** instead (operator-directed, before Phase 1). Everything else in Section 4.0 is unchanged. See `docs/architecture.md` and `docs/initial_implementation_log.md` for what changed.

## Commands

Node 22 LTS is required for `apps/web` — the pinned version is in `apps/web/.nvmrc`; run `nvm use` there first if your default Node is different (Angular 22 will refuse to build otherwise).

```bash
# Infra (Postgres 17 on :5433, RabbitMQ 4.1 on :5673/:15673, Redis 7 on :6379 —
# host ports shifted from defaults to avoid clashing with other local services;
# container-internal ports and inter-service TALOS_*_URL values are standard)
docker compose -f infra/docker-compose.dev.yml up -d
docker compose -f infra/docker-compose.dev.yml down

# apps/web (Angular 22 / Vitest)
cd apps/web
npm ci
npm run build                          # ng build
npx ng test --watch=false              # full suite
npx vitest run src/app/app.spec.ts     # single spec file

# apps/api (Spring Boot 4.1 / Gradle, Kotlin DSL)
cd apps/api
./gradlew build                                    # compile + test + jar
./gradlew test                                      # tests only
./gradlew test --tests "dev.talos.SomeClassTest"    # single test class

# apps/orchestrator, apps/runner-supervisor, packages/agent-adapter-spec (uv / Python 3.12)
cd <project>
uv sync
uv run pytest                                        # full suite
uv run pytest tests/test_package.py::test_name       # single test

# Naming guard (must return nothing before any commit)
grep -ri agentos . --exclude-dir=.git --exclude-dir=docs --exclude-dir=.github --exclude=CLAUDE.md --exclude=AGENTS.md

# Docker image per app (orchestrator/runner-supervisor build from repo root — see Architecture)
docker build -f apps/web/Dockerfile apps/web
docker build -f apps/api/Dockerfile apps/api
docker build -f apps/orchestrator/Dockerfile .
docker build -f apps/runner-supervisor/Dockerfile .
```

If `docker`/`sg docker -c '<cmd>'` reports a permission error on `/var/run/docker.sock`, the shell's group membership hasn't refreshed since the user was added to the `docker` group — wrap commands as `sg docker -c "<command>"` rather than trying to fix group membership mid-session.

## Architecture

**Service boundaries** (`apps/`): `talos-web` (Angular — dashboard, Kanban, review UI, approvals) · `talos-api` (Spring Boot — auth, all REST, all persistence writes, task/run state machines, approvals, audit, SSE fan-out) · `talos-orchestrator` (Python, stateless — consumes run requests, drives the pipeline, selects adapters) · `talos-runner-supervisor` (Python/FastAPI — owns the filesystem, prepares git worktrees, executes adapters, streams logs, captures diffs).

**The four communication paths are the entire spine of the system — nothing else exists:**
1. Browser → `talos-api`: REST + JWT, plus one SSE connection per open run view.
2. `talos-api` → RabbitMQ (`talos.events` exchange) → `talos-orchestrator`. The orchestrator never receives HTTP calls from the API.
3. `talos-orchestrator`/`talos-runner-supervisor` → `talos-api` via `/internal/v1` only, service-token-authenticated. This is the *only* way either Python service touches durable state.
4. `talos-orchestrator` → `talos-runner-supervisor`: plain HTTP on the internal Docker network.

**Log flow:** runner supervisor streams stdout/stderr over a chunked HTTP response to the orchestrator → orchestrator batches (50 lines or 2s) → `POST /internal/v1/runs/{id}/logs` → API persists + publishes to Redis `talos:run:{run_id}:logs` → `GET /api/v1/runs/{id}/events/stream` (SSE) relays to the browser.

**Monorepo layout:**
```
apps/{web,api,orchestrator,runner-supervisor}   # one Docker image each, independently deployable
workers/                                        # per-run execution base + stack images (Phase 11)
packages/contracts/                             # openapi.yaml + events/*.json — source of truth for all services
packages/project-config-schema/                 # talos.schema.json for talos.yaml (Phase 3)
packages/agent-adapter-spec/                    # Python AgentAdapter ABC + contract tests, shared by orchestrator & runner-supervisor
infra/docker-compose.dev.yml                    # postgres/rabbitmq/redis dev infra
scripts/smoke.sh                                # end-to-end smoke test (Phase 6+)
docs/phase-reports/phase-N-report.md            # written at every phase gate
```

`apps/orchestrator` and `apps/runner-supervisor` both consume `packages/agent-adapter-spec` as an editable `uv` path dependency (`tool.uv.sources` in each `pyproject.toml`) — because of this, their `Dockerfile`s build with the **repo root** as context (`docker build -f apps/orchestrator/Dockerfile .`), not their own directory, so the shared package can be copied in alongside the app. `apps/web` and `apps/api` build from their own directories as usual.

`apps/orchestrator`'s module layout (`main.py`, `pipeline.py`, `api_client.py`, `runner_client.py`, `adapters.py`, `locks.py`) is prescribed by Section 6.3 of the plan — don't rename or restructure it. `apps/runner-supervisor` has no prescribed module layout in the plan; don't invent one ahead of the phase that needs it.

Every Python stub function that isn't implemented yet raises `NotImplementedError` naming the phase that fills it in, rather than doing nothing silently. Each Python project needs at least one real test — `pytest` exits non-zero on a truly empty collection, so "empty suites allowed" in a phase's acceptance criteria means "no meaningful tests yet," not "zero test files."

## Git

Local repo identity (not global): `user.name = Paul Bernard`, `user.email = paulvbernard73@gmail.com`. Commit after each phase goal/ticket, not continuously. Keep commit messages brief, clear, and concise. Do not add co-authoring trailers.
