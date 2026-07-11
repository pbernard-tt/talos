# Deployment

Operator runbook for deploying Talos itself to [Dokploy](https://dokploy.com) (Section 18 of the
implementation plan). This path uses [`infra/dokploy/docker-compose.prod.yml`](../infra/dokploy/docker-compose.prod.yml)
as the Dokploy Compose application definition.

## 1. Prerequisites

- A VPS with Docker and [Dokploy](https://docs.dokploy.com/docs/core/installation) already
  installed. Dokploy provides the Traefik reverse proxy and TLS termination used by the Compose
  file.
- Two DNS records pointing at the VPS: one for the dashboard, stored as `TALOS_WEB_DOMAIN`
  (for example `talos.example.com`), and one for the API, stored as `TALOS_API_DOMAIN`
  (for example `api.talos.example.com`). Do not include `https://` in either value.
- This repository accessible to Dokploy, either as a GitHub fork or any git remote Dokploy can pull
  from. The Compose app builds each long-running service from this repo.
- Docker socket access for `talos-runner-supervisor`. It is the only Talos service that mounts
  `/var/run/docker.sock`, and it uses that socket to launch the isolated per-run worker containers.
- PostgreSQL must include the pgvector extension. The reference Compose file uses
  `pgvector/pgvector:pg17`; if you replace it with a managed database, enable pgvector before
  starting `talos-api` so Flyway can create `memory_chunks.embedding vector(64)`.

## 2. Build the worker images on the VPS

The Dokploy Compose app builds the four long-running services: web, API, orchestrator, and runner
supervisor. Agent execution itself runs in separate `workers/*` images launched by
`talos-runner-supervisor` through the host Docker daemon, so those images must exist on the same VPS
before the first run.

Build them once after cloning your fork on the VPS. Rebuild them whenever files under `workers/`
change.

```bash
git clone https://github.com/<you>/Talos.git
cd Talos
docker build -f workers/base-agent-runner/Dockerfile -t workers/base-agent-runner:latest .
docker build -f workers/java-runner/Dockerfile -t workers/java-runner:latest .
docker build -f workers/node-runner/Dockerfile -t workers/node-runner:latest .
docker build -f workers/python-runner/Dockerfile -t workers/python-runner:latest .
```

If Docker reports a permission error on `/var/run/docker.sock`, run the commands with the host's
Docker group wrapper, for example `sg docker -c "docker build -f workers/base-agent-runner/Dockerfile -t workers/base-agent-runner:latest ."`.

## 3. Create the Compose application in Dokploy

1. In Dokploy, create a new **Compose** application and point it at this repository, with
   `infra/dokploy/docker-compose.prod.yml` as the compose file path.
2. Set the application's environment variables. Dokploy injects these as `${VAR}` substitutions into
   the compose file at deploy time.

   | Variable | Notes |
   |---|---|
   | `TALOS_WEB_DOMAIN` | Dashboard host, e.g. `talos.example.com`; no scheme. |
   | `TALOS_API_DOMAIN` | API host, e.g. `api.talos.example.com`; no scheme. |
   | `TALOS_DB_PASSWORD` | Postgres password. `openssl rand -hex 32` is safe. |
   | `TALOS_RABBITMQ_PASSWORD` | RabbitMQ password for the `talos` user. Use URL-safe output such as `openssl rand -hex 32` because the value is embedded in an AMQP URL. |
   | `TALOS_JWT_SECRET` | 64+ random characters, e.g. `openssl rand -base64 48`; signs dashboard login JWTs. |
   | `TALOS_INTERNAL_API_TOKEN` | 64+ random characters, e.g. `openssl rand -base64 48`; shared secret for `/internal/v1`, used identically by talos-api, talos-orchestrator, and talos-runner-supervisor. |
   | `TALOS_ADMIN_EMAIL` / `TALOS_ADMIN_PASSWORD` | Seeded admin login; rotate the password after first login. `openssl rand -hex 24` is sufficient for the initial password. |
   | `TALOS_SECRETS_KEY` | 32-byte value, base64-encoded (`openssl rand -base64 32`) for AES-256-GCM encryption of stored integration credentials. Losing this key makes every stored credential unrecoverable, so back it up outside the VPS. |
   | `TALOS_GITHUB_WEBHOOK_SECRET` | HMAC secret for inbound GitHub webhooks; `openssl rand -base64 48` is sufficient. |
   | `TALOS_DOCKER_GID` | Host Docker socket group id, usually from `stat -c '%g' /var/run/docker.sock`; lets the non-root `talos` user inside `talos-runner-supervisor` use the mounted socket. |
   | `TALOS_WORKER_IMAGE_BASE` / `TALOS_WORKER_IMAGE_JAVA` / `TALOS_WORKER_IMAGE_NODE` / `TALOS_WORKER_IMAGE_PYTHON` | Optional overrides if you tag or publish worker images differently. Defaults in the Compose file are `workers/*-runner:latest`. |
   | `TALOS_RUN_MEMORY_LIMIT` / `TALOS_RUN_CPU_LIMIT` / `TALOS_RUN_PIDS_LIMIT` | Optional per-run container limits; defaults are `1g`, `1`, and `256`. |

3. Deploy. Dokploy brings the compose stack up in dependency order:
   `postgres` (PostgreSQL 17 with pgvector) → `rabbitmq`/`redis` → `api` (talos-api runs Flyway migrations on boot, so it
   does not report healthy until the schema is current) → `runner-supervisor`/`orchestrator` →
   `web`.
4. Confirm health:
   - `talos-api`: `GET /actuator/health`
   - `talos-runner-supervisor`: `GET /health`
   - `talos-web`: `GET /`
   - `talos-orchestrator`: process liveness only; it has no HTTP port because it consumes from
     RabbitMQ.
5. Log into `https://$TALOS_WEB_DOMAIN` with `TALOS_ADMIN_EMAIL`/`TALOS_ADMIN_PASSWORD` and change
   the password immediately.

## 4. Configuring GitHub and Dokploy integrations

Push/PR and deploy both need credentials configured through the API. There is not yet a dashboard
form for integrations, so use `curl` after logging in and obtaining a JWT from the dashboard or
`POST /api/v1/auth/login`.

```bash
# GitHub: a fine-grained PAT with contents:write and pull_requests:write on the target repo(s).
curl -X POST "https://${TALOS_API_DOMAIN}/api/v1/integrations" \
  -H "Authorization: Bearer <your JWT>" \
  -H "Content-Type: application/json" \
  -d '{"type":"github","name":"primary-github","secret":"<PAT>","authMode":"pat"}'

# Dokploy: an API token generated from your own Dokploy profile settings (Settings -> API/Tokens),
# used by DeployService to trigger deploys of managed projects on your behalf.
curl -X POST "https://${TALOS_API_DOMAIN}/api/v1/integrations" \
  -H "Authorization: Bearer <your JWT>" \
  -H "Content-Type: application/json" \
  -d '{"type":"dokploy","name":"primary-dokploy","configJson":{"baseUrl":"https://your-dokploy-instance.com"},"secret":"<Dokploy API token>","authMode":"api_key"}'
```

Verify either integration with `POST /api/v1/integrations/{id}/test`.

## 5. Rollback

Images are tagged with the git SHA they were built from. To roll back a service:

1. In Dokploy, redeploy the application pinned to the previous known-good git SHA. Dokploy keeps a
   deployment history per app with the commit it built from.
2. Flyway migrations must stay backward-compatible one release back. Never drop or rename a column a
   still-running previous API version reads. If a migration is not backward-compatible, fix the
   migration before merging rather than treating rollback as a manual database repair task.
3. A failed deploy whose health check never turns green leaves the previous container running.
   Investigate through the Dokploy app logs before retrying.

If a rollback crosses a change to `workers/*`, rebuild the worker images on the VPS to the matching
git SHA as well; those images are used by future agent runs and are not rebuilt automatically by the
Compose app.

## 6. Retention, volumes, and backups

- `talos_workspaces` and `talos_provider_homes` are mounted into `talos-runner-supervisor`.
  `TALOS_MAX_WORKSPACE_AGE_DAYS` bounds workspace retention, but provider-home credentials persist
  until manually rotated.
- `talos_postgres_data` is the only volume holding data with no external source of truth. Back it
  up. A `pg_dump` / `pg_restore` cycle against the `talos-postgres` container is sufficient for the
  VPS-scale deployments this MVP targets; restore into a PostgreSQL image/database with pgvector
  enabled, because Phase 13 memory uses the `vector` type.
- `TALOS_SECRETS_KEY` must be backed up with the database. Without it, encrypted integration
  credentials in the restored database cannot be decrypted.

The backup/restore mechanism was validated in [`docs/security-model.md`](security-model.md#8-backup-and-restore-drill-executed-2026-07-10).
