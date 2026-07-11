# Architecture

Talos's target architecture, service boundaries, and communication contract are specified in Sections 4 and 6 of [`Talos_Implementation_Plan.pdf`](Talos_Implementation_Plan.pdf) — that document is the single source of truth. This page tracks the pinned technology versions actually in use in this repository (Section 4.0), verified at scaffold time.

## Pinned versions (verified 2026-07-09)

| Concern | Pinned decision | Verified version in this repo |
|---|---|---|
| Frontend | Angular 22, standalone components, signals, Angular Material + CDK, Node 22 LTS | `@angular/cli` 22.0.6, `@angular/material`/`@angular/cdk` 22.0.4, Node 22.23.1 (LTS "Krypton"), npm 10.9.8 |
| Backend API | Spring Boot 4.1.x, Java 21 LTS, Gradle (Kotlin DSL) | Spring Boot 4.1.0, `dev.talos` base package, Java 21.0.11 (Temurin/OpenJDK), Gradle 9.5.1 via wrapper (`./gradlew`) |
| Migrations | Flyway, bundled with Spring Boot | `flyway-database-postgresql`, `V0xx__description.sql` naming from Phase 2 onward |
| Database | PostgreSQL 17 + pgvector extension | `pgvector/pgvector:pg17` (Docker) |
| Queue | RabbitMQ 4.1, topic exchange `talos.events`, quorum queues | `rabbitmq:4.1-management` (Docker) |
| Cache / pub-sub | Redis 7 | `redis:7` (Docker) |
| Orchestrator | Python 3.12, `uv`, `aio-pika`, `httpx`, `pytest` | Python 3.12.3 (uv-managed interpreter, pinned via `.python-version`), uv 0.11.28 |
| Runner supervisor | Python 3.12, FastAPI + uvicorn | Same Python 3.12.3 interpreter; `fastapi` 0.139.0, `uvicorn` 0.51.0 |
| Contracts | OpenAPI 3.1 + per-event JSON Schema in `packages/contracts` | Stub `openapi.yaml` (3.1.0) as of Phase 0; endpoints/events added phase by phase |
| IDs | UUID v7, application-side | Not yet generated (no entities until Phase 2) |
| Timestamps | `TIMESTAMPTZ`, UTC, ISO-8601 | Enforced from Phase 2's schema onward |
| Live updates | Server-Sent Events fed from Redis pub/sub; no WebSockets | Implemented in Phase 5 |
| Deployment | Docker image per service, deployed via Dokploy | Dockerfiles present for all four apps as of Phase 0; Dokploy runbook in Phase 10 |

The host machine used for initial development also runs Node 24 and a Java 11 toolchain for unrelated projects; `apps/web/.nvmrc` pins Node 22 for this repo specifically, and `nvm use` (or any Node-version manager honoring `.nvmrc`) should be run before working in `apps/web`.

**Amendment (2026-07-09):** Section 4.0 of the implementation plan pins Maven for `apps/api`. At the operator's explicit request, before Phase 1 started, the build tool was switched to Gradle (Kotlin DSL, `./gradlew`), preserving every other pinned decision (Spring Boot 4.1.0, Java 21, package `dev.talos`, the exact starter set). See `docs/phase-reports/phase-0-report.md` and `docs/initial_implementation_log.md` for what changed and how it was verified.

## System diagram

See Section 4.1 of the implementation plan for the full request-flow diagram (browser/Telegram/WhatsApp/webhook → `talos-web` / inbound webhooks → `talos-api` → PostgreSQL/Redis/RabbitMQ → `talos-orchestrator` → `talos-runner-supervisor` → agent adapter layer → isolated git worktree).

## Service boundaries

| Service | Technology | Responsibility |
|---|---|---|
| `talos-web` | Angular 22 | Dashboard, Kanban, project registry, run viewer, diff/review UI, approvals. |
| `talos-api` | Spring Boot 4.1 (Java 21) | Auth, REST APIs, all persistence writes, task/run state machines, approvals, memory ingestion/retrieval, audit, SSE fan-out. Sole writer to PostgreSQL. |
| `talos-orchestrator` | Python 3.12 | Consumes run requests from RabbitMQ, drives the run pipeline, selects agent adapters. Holds no durable state. |
| `talos-runner-supervisor` | Python 3.12 (FastAPI) | Prepares git worktrees, executes agent adapters, streams logs, captures diffs. |

## Inter-service communication (Section 4.3)

1. Browser → API: REST over HTTPS with JWT, plus SSE for live run views.
2. API → RabbitMQ → Orchestrator: the API publishes events on the `talos.events` exchange; the orchestrator consumes them. The orchestrator never receives HTTP calls from the API.
3. Orchestrator/Runner supervisor → API (`/internal/v1`): the API is the only writer to PostgreSQL. Neither Python service opens a database connection.
4. Orchestrator → Runner supervisor: plain HTTP on the internal Docker network.

## Project memory (Phase 13)

Project memory is API-owned to preserve the sole-writer rule. `talos-api` enables pgvector via
Flyway, stores masked source documents in `memory_documents`, stores embedded chunks in
`memory_chunks`, and serves project-scoped retrieval through
`GET /internal/v1/projects/{id}/memory/search`. The orchestrator never reads or writes the
database; it sends configured `context.docs` content to
`POST /internal/v1/projects/{id}/memory/documents`, retrieves relevant chunks, and inserts them into
the auditable Section 7.3 prompt between direct project context and the task. Setting
`memory.enabled: false` in `talos.yaml` skips ingestion/retrieval and preserves the pre-memory prompt
format.

## Repository layout

See the root [`README.md`](../README.md) for the monorepo tree. Each app under `apps/` builds its own Docker image (`apps/<name>/Dockerfile`) and is independently deployable; the only shared runtime state is PostgreSQL, RabbitMQ, and Redis. `packages/agent-adapter-spec` is shared source (not runtime state) between `talos-orchestrator` and `talos-runner-supervisor`, installed as an editable path dependency via `uv`.
