# Phase 0 report — Repository and tooling setup

## What works

- Monorepo skeleton matches Section 5 of the implementation plan (minus Phase 12's `telegram-adapter`/`whatsapp-adapter`, which are explicitly deferred).
- `apps/web`: Angular 22, standalone components, signals, Angular Material + CDK, Vitest. Builds and tests pass.
- `apps/api`: Spring Boot 4.1.0, Java 21, Maven, package `dev.talos`. Builds; all pinned starters (web, data-jpa, security, validation, postgresql, flyway, actuator, amqp, data-redis) present. `application.yml` wires the Appendix A env vars with local defaults.
- `apps/orchestrator`, `apps/runner-supervisor`, `packages/agent-adapter-spec`: three `uv`-managed Python 3.12 projects; the two apps consume `agent-adapter-spec` as an editable path dependency. Orchestrator's module layout matches Section 6.3 exactly.
- `packages/contracts`: `openapi.yaml` stub (OpenAPI 3.1, error envelope schema, no paths yet) and `events/` with the event-envelope contract documented.
- `infra/docker-compose.dev.yml`: postgres:17, rabbitmq:4.1-management, redis:7, all with healthchecks; verified to reach `healthy` from a cold `docker compose up`.
- Every app (`web`, `api`, `orchestrator`, `runner-supervisor`) has a working `Dockerfile`; all four build successfully. `talos-web`'s image was additionally run and smoke-tested (serves `index.html`, renders `env-config.json` from `TALOS_API_URL`).
- `.env.example` present for all four apps, matching Appendix A exactly.
- `docs/architecture.md` records the pinned stack and the actual verified versions in use.
- `grep -ri agentos .` returns nothing.

## What is stubbed

- No business logic anywhere: no entities, no migrations, no auth, no endpoints, no adapters, no pipeline. Every Python module that Section 6.3 names for the orchestrator exists as a file but raises `NotImplementedError` (or is a one-line docstring) rather than doing anything.
- `apps/api` has no tests (the generated `contextLoads` test was removed — it required a live Postgres connection that doesn't exist yet; Phase 0's acceptance criteria explicitly allow empty suites). A real context-loading test returns in Phase 1/2 with Testcontainers.
- `packages/contracts/openapi.yaml` has an empty `paths` object; `runner-api.yaml` (Section 10.5) doesn't exist yet.
- `packages/project-config-schema/talos.schema.json` doesn't exist yet (Phase 3).
- `workers/*` and `infra/dokploy/` are placeholder directories with a README each — no Dockerfiles or runbook content (Phase 11 and Phase 10 respectively).
- `docs/security-model.md`, `docs/agent-run-lifecycle.md`, `docs/provider-auth.md`, `docs/deployment.md` are one-paragraph placeholders naming the phase that will fill them in.

## Deviations from the plan

- **Spring Boot version string.** The plan pins "Spring Boot 4.1.x." start.spring.io's `bootVersion` metadata returns `4.1.0.RELEASE`, but that exact string does not resolve on Maven Central (Boot dropped the `.RELEASE` suffix from its actual artifact versions starting well before 4.x). Used `4.1.0` in `pom.xml`, which is the correct Maven Central version and still satisfies "4.1.x." No sign-off needed — this is a packaging-metadata correction, not an architectural change.
- **Host port remapping in `docker-compose.dev.yml`.** Postgres (5433) and RabbitMQ (5673/15673) host-side ports are shifted from their defaults because the development machine already runs unrelated native services on 5432/5672/15672. Container-internal ports, and therefore every `TALOS_*_URL` value used between containers on the Docker network, are untouched. This only affects host-machine convenience access (e.g. `psql -p 5433`); it does not change any contract in the plan.
- **Node version.** The plan pins Node 22 LTS; the development machine's default `PATH` resolves to Node 24 (via linuxbrew), which is below Angular 22's minimum supported range at the patch level installed. Installed Node 22.23.1 via `nvm` and pinned it in `apps/web/.nvmrc`. Both are within the "22 LTS" family the plan specifies.

Both deviations are mechanical (packaging/tooling corrections), not architectural, so they're recorded here rather than gated on sign-off before continuing — flag if that judgment is wrong.

## Acceptance criteria (Section 16, Phase 0)

| Criterion | Status |
|---|---|
| `docker compose -f infra/docker-compose.dev.yml up` starts infra healthy | ✅ verified (postgres, rabbitmq, redis all `healthy`) |
| `./mvnw verify` passes (empty suite allowed) | ✅ |
| `ng build` passes | ✅ (`ng test` also passes, 2/2) |
| `uv run pytest` passes (empty suite allowed) | ✅ x3 (agent-adapter-spec, orchestrator, runner-supervisor — each has one real import-smoke test since pytest exits non-zero on true empty collection) |
| `grep -ri agentos .` returns nothing | ✅ |
| CI green | ⚠️ workflow written (`.github/workflows/ci.yml`), runs the same checks as above; not yet exercised on GitHub Actions because this repository has no remote configured yet |

## Next

Phase 1 — Backend foundation: seeded admin, `POST /auth/login`, JWT filter, error envelope, audit writer, `V001__users_and_audit.sql`.
