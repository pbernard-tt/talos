# Talos

Talos is a self-hosted, web-based, agent-agnostic control plane that orchestrates existing coding agents (Claude Code, OpenCode, Codex CLI, OpenHands, ...) across a portfolio of software projects. It provides a project registry, a Kanban task board, governed agent runs on isolated git worktrees, a diff/review center, and an approval-gated push/PR/deploy pipeline.

Talos does not build a new coding agent or LLM. It orchestrates existing agents through a stable `AgentAdapter` interface, so the platform works identically whether the underlying agent is a deterministic shell script or a subscription-based CLI.

The full architecture, data model, API surface, and phased implementation plan live in [`docs/Talos_Implementation_Plan.pdf`](docs/Talos_Implementation_Plan.pdf) (source: `docs/src/talos-implementation-plan.md`). That document is the single source of truth for this codebase.

## Monorepo layout

```
talos/
  apps/
    web/                    # Angular 22 dashboard
    api/                    # Spring Boot 4.1 API (dev.talos) — sole writer to PostgreSQL
    orchestrator/           # Python run pipeline coordinator
    runner-supervisor/      # Python workspace/adapter executor
  workers/                  # Base + stack Docker images for per-run execution (Phase 11)
  packages/
    contracts/              # openapi.yaml + event JSON Schemas — source of truth for all services
    project-config-schema/  # talos.schema.json for talos.yaml
    agent-adapter-spec/     # Python AgentAdapter ABC, dataclasses, contract tests
  infra/
    docker-compose.dev.yml  # postgres, rabbitmq, redis for local dev
    dokploy/                # production deployment runbook
  scripts/
    smoke.sh                # end-to-end smoke test (create project -> run -> approval)
  docs/
    phase-reports/          # phase-N-report.md written at each phase gate
```

## Services

| Service | Technology | Responsibility |
|---|---|---|
| `talos-web` | Angular 22 | Dashboard, Kanban, project registry, run viewer, diff/review UI, approvals. |
| `talos-api` | Spring Boot 4.1 (Java 21) | Auth, REST APIs, all persistence writes, task/run state machines, approvals, audit, SSE fan-out. |
| `talos-orchestrator` | Python 3.12 | Consumes run requests from RabbitMQ, drives the run pipeline, selects agent adapters. Holds no durable state. |
| `talos-runner-supervisor` | Python 3.12 (FastAPI) | Prepares git worktrees, executes agent adapters, streams logs, captures diffs. |

Each app builds its own Docker image and is independently deployable; the only shared runtime state is PostgreSQL, RabbitMQ, and Redis.

## Local development

Requires Docker, Node 22 LTS, Java 21, Maven, and [`uv`](https://docs.astral.sh/uv/).

```bash
# Start infra (Postgres 17, RabbitMQ 4.1, Redis 7)
docker compose -f infra/docker-compose.dev.yml up

# Web
cd apps/web && npm install && npm run build

# API
cd apps/api && ./mvnw verify

# Orchestrator / Runner supervisor
cd apps/orchestrator && uv run pytest
cd apps/runner-supervisor && uv run pytest
```

## Status

Implementation proceeds phase by phase per Section 16 of the implementation plan. See `docs/phase-reports/` for what is currently working and what remains stubbed, and `docs/initial_implementation_log.md` for a running log of changes.
