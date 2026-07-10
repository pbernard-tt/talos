# Talos

Talos is a self-hosted, web-based control plane for running coding agents safely across a portfolio of software projects.

It gives you a project registry, Kanban-style task board, governed agent runs, live logs, diff review, human approvals, GitHub push/PR creation, and Dokploy deployment triggers. Talos does not build a new LLM or coding agent. It orchestrates existing tools through the `AgentAdapter` interface in [`packages/agent-adapter-spec`](packages/agent-adapter-spec).

The full architecture, data model, API surface, and phased implementation plan live in [`docs/Talos_Implementation_Plan.pdf`](docs/Talos_Implementation_Plan.pdf) from [`docs/src/talos-implementation-plan.md`](docs/src/talos-implementation-plan.md). That document is the single source of truth for contracts and state transitions.

## What Talos gives you

- **One dashboard for many repos.** Register projects, track tasks, and keep agent work visible instead of buried in separate terminal sessions.
- **Governed agent runs.** Start a run from a task, execute it on an isolated `agent/task-<id>-<slug>` branch, stream logs live, and retain an auditable step history.
- **Agent-agnostic execution.** The platform talks to adapters, not directly to providers. `custom-shell`, `claude-code`, `opencode`, `codex-cli`, and `openhands` (an HTTP client against a locally deployed OpenHands agent-server) are implemented; `gemini-cli` remains a backlog stub.
- **Real isolation boundary.** Runs execute in per-run Docker containers with their own worktree subpath, resource limits, no Docker socket, no internal service network, and provider homes outside the workspace.
- **Review before merge.** Talos captures diffs, scans configured risk patterns, shows the review result, and requires approval before any push, pull request, or deploy action.
- **Approval-gated delivery.** Approved runs can push a branch, open a GitHub pull request, and trigger a Dokploy deployment. Production deploys require their own deploy approval.
- **Secret discipline.** Provider credentials live outside workspaces, project secrets are injected only as enumerated environment variables, and injected values are masked before log persistence.
- **Production-oriented operations.** The reference deployment uses independent Docker images for each app, PostgreSQL, RabbitMQ, Redis, Traefik/TLS via Dokploy, health checks, rollback guidance, retention cleanup, and a documented backup/restore drill.

## What Talos is not

- Talos is not an LLM, model router, or replacement coding agent.
- Talos is not a generic CI/CD system; it controls agent work, review, approvals, push/PR, and deploy triggers.
- Talos does not expose production secrets to agent workers.
- Talos does not let Python services write directly to PostgreSQL. `talos-api` is the only database writer.

## Architecture at a glance

| Service | Technology | Responsibility |
|---|---|---|
| `talos-web` | Angular 22 | Dashboard, Kanban board, project registry, run viewer, diff/review UI, approvals, deploy actions. |
| `talos-api` | Spring Boot 4.1, Java 21 | Auth, public/internal REST APIs, all persistence writes, task/run state machines, approvals, audit, SSE fan-out. |
| `talos-orchestrator` | Python 3.12 | Consumes run requests from RabbitMQ, drives the run pipeline, selects worker images and adapters. |
| `talos-runner-supervisor` | Python 3.12, FastAPI | Owns workspaces, prepares git worktrees, launches per-run containers, streams logs, captures diffs, pushes branches. |

The four runtime paths are:

1. Browser → `talos-api`: REST + JWT, plus SSE for live run views.
2. `talos-api` → RabbitMQ (`talos.events`) → `talos-orchestrator`.
3. `talos-orchestrator` / `talos-runner-supervisor` → `talos-api` through `/internal/v1` with the internal service token.
4. `talos-orchestrator` → `talos-runner-supervisor` over internal HTTP.

## Fast local start

Local development needs Docker, Node 22 LTS, Java 21, and [`uv`](https://docs.astral.sh/uv/). The API uses the bundled Gradle wrapper, so no separate Gradle install is required.

Build the per-run worker images first. Agent runs use these images through the host Docker daemon.

```bash
docker build -f workers/base-agent-runner/Dockerfile -t workers/base-agent-runner:latest .
docker build -f workers/java-runner/Dockerfile -t workers/java-runner:latest .
docker build -f workers/node-runner/Dockerfile -t workers/node-runner:latest .
docker build -f workers/python-runner/Dockerfile -t workers/python-runner:latest .
```

Start the local Talos backend/orchestration stack:

```bash
TALOS_DOCKER_GID=$(stat -c '%g' /var/run/docker.sock) \
  docker compose -f infra/docker-compose.dev.yml up -d --build
```

Run the end-to-end smoke test:

```bash
./scripts/smoke.sh
```

Run the Angular dashboard locally:

```bash
cd apps/web
nvm use
npm ci
npm start
```

The dev API defaults to `http://localhost:8080`, with seeded credentials `admin@talos.local` / `talos-dev-admin`. Those credentials are deliberately dev-only and must never be reused in production.

If Docker reports a permission error on `/var/run/docker.sock`, refresh group membership or run the Docker commands through your system's Docker group wrapper, for example `sg docker -c "<command>"`.

## Production deployment in minutes

The production reference path is Dokploy, using [`infra/dokploy/docker-compose.prod.yml`](infra/dokploy/docker-compose.prod.yml). Dokploy supplies Traefik, TLS, app build/deploy orchestration, and deployment history. The deeper operator runbook is [`docs/deployment.md`](docs/deployment.md).

Prerequisites:

- A VPS with Docker and Dokploy installed.
- Two DNS records pointing to the VPS, for example `talos.example.com` and `api.talos.example.com`.
- A GitHub fork or any git remote that Dokploy can pull.
- Docker socket access for `talos-runner-supervisor`; it launches the isolated per-run containers.

On the VPS, build the worker images once from your fork. Rebuild them whenever files under `workers/` change.

```bash
git clone https://github.com/<you>/Talos.git
cd Talos
docker build -f workers/base-agent-runner/Dockerfile -t workers/base-agent-runner:latest .
docker build -f workers/java-runner/Dockerfile -t workers/java-runner:latest .
docker build -f workers/node-runner/Dockerfile -t workers/node-runner:latest .
docker build -f workers/python-runner/Dockerfile -t workers/python-runner:latest .
```

In Dokploy, create a Compose application:

- Repository: your fork or deployment remote.
- Compose file path: `infra/dokploy/docker-compose.prod.yml`.
- Web domain: your dashboard host, stored as `TALOS_WEB_DOMAIN`.
- API domain: your API host, stored as `TALOS_API_DOMAIN`.

Set these environment variables in the Dokploy application:

| Variable | Example / generation | Purpose |
|---|---|---|
| `TALOS_WEB_DOMAIN` | `talos.example.com` | Traefik host rule for the dashboard. No scheme. |
| `TALOS_API_DOMAIN` | `api.talos.example.com` | Traefik host rule for the API and browser runtime API URL. No scheme. |
| `TALOS_DB_PASSWORD` | `openssl rand -hex 32` | PostgreSQL password. |
| `TALOS_RABBITMQ_PASSWORD` | `openssl rand -hex 32` | RabbitMQ password; hex avoids URL delimiter problems in the AMQP URL. |
| `TALOS_JWT_SECRET` | `openssl rand -base64 48` | Signs dashboard JWTs. |
| `TALOS_INTERNAL_API_TOKEN` | `openssl rand -base64 48` | Shared token for `/internal/v1` calls. Use the same value for API, orchestrator, and runner supervisor. |
| `TALOS_ADMIN_EMAIL` | `you@example.com` | Initial admin login. |
| `TALOS_ADMIN_PASSWORD` | `openssl rand -hex 24` | Initial admin password. Rotate after first login. |
| `TALOS_SECRETS_KEY` | `openssl rand -base64 32` | 32-byte base64 key for encrypted integration secrets. Back this up; losing it makes stored credentials unrecoverable. |
| `TALOS_GITHUB_WEBHOOK_SECRET` | `openssl rand -base64 48` | HMAC secret for inbound GitHub webhooks. |
| `TALOS_DOCKER_GID` | `stat -c '%g' /var/run/docker.sock` | Lets `talos-runner-supervisor` use the host Docker socket as its non-root user. |

Deploy the Compose app. Dokploy builds the four long-running app images and starts the services in dependency order: PostgreSQL, RabbitMQ, Redis, API, runner supervisor, orchestrator, then web.

After the health checks pass:

1. Open `https://$TALOS_WEB_DOMAIN`.
2. Log in with `TALOS_ADMIN_EMAIL` and `TALOS_ADMIN_PASSWORD`.
3. Change the seeded password.
4. Configure GitHub and Dokploy integrations as described in [`docs/deployment.md`](docs/deployment.md#4-configuring-github-and-dokploy-integrations).
5. Register your first project, create a task, start a run, review the diff, approve it, then push/PR/deploy from the run page.

## Monorepo layout

```text
talos/
  apps/
    web/                    # Angular dashboard
    api/                    # Spring Boot API; sole PostgreSQL writer
    orchestrator/           # Python run pipeline coordinator
    runner-supervisor/      # Python workspace/container/adapter executor
    telegram-adapter/       # Chat task intake + notifications via the Telegram Bot API (optional)
    whatsapp-adapter/       # Chat task intake + notifications via the WhatsApp Cloud API (optional)
  workers/                  # Per-run execution images: base, Java, Node, Python
  packages/
    contracts/              # OpenAPI + event JSON Schemas
    project-config-schema/  # talos.yaml JSON Schema
    agent-adapter-spec/     # AgentAdapter ABC, dataclasses, contract tests
  infra/
    docker-compose.dev.yml  # Local development stack
    dokploy/                # Production Compose app definition
  scripts/
    smoke.sh                # End-to-end local smoke test
  docs/
    phase-reports/          # Phase acceptance reports
```

## Documentation

- [`docs/deployment.md`](docs/deployment.md) — production deployment, integrations, rollback, volumes, backup notes.
- [`docs/security-model.md`](docs/security-model.md) — isolation, secret handling, log masking, rate limiting, retention, backup/restore drill.
- [`docs/provider-auth.md`](docs/provider-auth.md) — Claude Code provider-home authentication.
- [`docs/agent-run-lifecycle.md`](docs/agent-run-lifecycle.md) — run state machine and lifecycle details.
- [`docs/architecture.md`](docs/architecture.md) — pinned stack and service boundaries.
- [`docs/phase-reports/`](docs/phase-reports) — what works, what is stubbed, and verification per phase.
- [`docs/initial_implementation_log.md`](docs/initial_implementation_log.md) — chronological implementation log.

## License

Talos is open source under the [MIT License](LICENSE).
