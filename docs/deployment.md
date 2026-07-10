# Deployment

Operator runbook for deploying Talos itself to [Dokploy](https://dokploy.com) (Section 18 of the
implementation plan). Everything below is what a fresh operator needs to go from a bare VPS with
Dokploy installed to a running Talos instance, using [`infra/dokploy/docker-compose.prod.yml`](../infra/dokploy/docker-compose.prod.yml)
as the Compose app definition.

## 1. Prerequisites

- A VPS with [Dokploy](https://docs.dokploy.com/docs/core/installation) already installed (Dokploy
  itself provides the Traefik reverse proxy and TLS termination used below).
- Two DNS records pointing at the VPS: one for the dashboard (e.g. `talos.example.com`) and one for
  the API (e.g. `api.talos.example.com`) — matches the two `traefik.http.routers.*.rule` labels in
  the Compose file.
- This repository accessible to Dokploy (either as a connected GitHub repo, or any git remote
  Dokploy can pull from) — the Compose app builds each service's image from its own
  `apps/*/Dockerfile` rather than pulling pre-built images.

## 2. Create the Compose application in Dokploy

1. In Dokploy, create a new **Compose** application and point it at this repository, with
   `infra/dokploy/docker-compose.prod.yml` as the compose file path.
2. Set the application's environment variables (Dokploy injects these as `${VAR}` substitutions
   into the compose file at deploy time). These map 1:1 onto Appendix A of
   `docs/Talos_Implementation_Plan.pdf`:

   | Variable | Notes |
   |---|---|
   | `TALOS_DB_PASSWORD` | Postgres password (matches what `TALOS_DB_URL` in the compose file expects) |
   | `TALOS_RABBITMQ_PASSWORD` | RabbitMQ password for the `talos` user |
   | `TALOS_JWT_SECRET` | 64+ random characters — signs dashboard login JWTs |
   | `TALOS_INTERNAL_API_TOKEN` | 64+ random characters — shared secret for `/internal/v1`, used identically by talos-api, talos-orchestrator, and talos-runner-supervisor |
   | `TALOS_ADMIN_EMAIL` / `TALOS_ADMIN_PASSWORD` | Seeded admin login; rotate the password after first login |
   | `TALOS_SECRETS_KEY` | 32-byte value, base64-encoded (e.g. `openssl rand -base64 32`) — AES-256-GCM key for `secret_values` (GitHub/Dokploy integration credentials). **Losing this key makes every stored credential unrecoverable** — back it up outside the VPS. |
   | `TALOS_GITHUB_WEBHOOK_SECRET` | HMAC secret for the (post-MVP) inbound GitHub webhook; set to any random value now |

   Generate random secrets with `openssl rand -base64 48` (JWT/internal token) and
   `openssl rand -base64 32` (secrets key — must decode to exactly 32 bytes for AES-256).

3. Deploy. Dokploy brings the compose stack up in the order the file declares dependencies:
   `postgres` → `rabbitmq`/`redis` → `api` (talos-api runs its Flyway migrations on boot, so it
   won't report healthy until the schema is current) → `orchestrator`/`runner-supervisor` → `web`.
4. Confirm health: `api`'s health check is `GET /actuator/health`, `runner-supervisor`'s is
   `GET /health`, `web`'s is `GET /`. The `orchestrator` has no HTTP port (Section 4.3's four
   communication paths — it only consumes from RabbitMQ), so Dokploy tracks it via process
   liveness rather than an HTTP probe.
5. Log into `https://talos.example.com` with `TALOS_ADMIN_EMAIL`/`TALOS_ADMIN_PASSWORD` and change
   the password immediately.

## 3. Configuring GitHub and Dokploy integrations

Push/PR (Phase 9) and deploy (Phase 10) both need credentials configured through the API — there is
no dashboard form for this yet (see the Phase 9/10 phase reports' disclosed gaps), so use `curl`:

```bash
# GitHub: a fine-grained PAT with contents:write, pull_requests:write on the target repo(s).
curl -X POST https://api.talos.example.com/api/v1/integrations \
  -H "Authorization: Bearer <your JWT>" -H "Content-Type: application/json" \
  -d '{"type":"github","name":"primary-github","secret":"<PAT>","authMode":"pat"}'

# Dokploy: an API token generated from your own Dokploy profile settings (Settings -> API/Tokens),
# used by DeployService to trigger deploys of *managed* projects on your behalf.
curl -X POST https://api.talos.example.com/api/v1/integrations \
  -H "Authorization: Bearer <your JWT>" -H "Content-Type: application/json" \
  -d '{"type":"dokploy","name":"primary-dokploy","configJson":{"baseUrl":"https://your-dokploy-instance.com"},"secret":"<Dokploy API token>","authMode":"api_key"}'
```

Verify either with `POST /api/v1/integrations/{id}/test`.

## 4. Rollback

Images are tagged with the git SHA they were built from. To roll back a service:

1. In Dokploy, redeploy the application pinned to the previous known-good git SHA (Dokploy keeps a
   deployment history per app with the commit it built from).
2. Flyway migrations must stay backward-compatible one release back (never drop/rename a column a
   still-running previous version reads) — this is what makes an API rollback safe without also
   needing a database rollback. If a migration genuinely isn't backward-compatible, that's a design
   error to fix before merging, not something to work around at rollback time.
3. A failed deploy (health check never turns green) leaves the previous container running — Dokploy
   does not cut traffic to a container that hasn't passed its health check. Investigate via the
   Dokploy app's logs before retrying; don't redeploy repeatedly against an unknown failure.

## 5. Retention and volumes

- `talos_workspaces` and `talos_provider_homes` (mounted into `runner-supervisor`) grow over time —
  `TALOS_MAX_WORKSPACE_AGE_DAYS` (default 7) bounds workspace retention, but provider-home
  credentials persist until manually rotated.
- `talos_postgres_data` is the only volume holding data with no external source of truth (git holds
  the code; GitHub holds the PRs) — back it up. A `pg_dump`/`pg_restore` cycle against the
  `talos-postgres` container is sufficient for the VPS-scale deployments this MVP targets.
