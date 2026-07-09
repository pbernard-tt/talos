## 2026-07-09 — apps/api build tool: Maven → Gradle

**Ask:** Before starting Phase 1, switch `apps/api` from Maven to Gradle. Operator chose Kotlin DSL over Groovy when asked.

**Root cause / context:** Not a bug fix — an explicit operator-directed deviation from Section 4.0 of the implementation plan, which pins Maven. Everything else pinned in that section (Spring Boot 4.1.0, Java 21, package `dev.talos`, exact starter set) is unchanged.

**Changed (backend):**
- Regenerated the project via start.spring.io with `type=gradle-project-kotlin` instead of `maven-project`, same dependency list as the original Phase 0 scaffold. Hit the same `bootVersion` metadata bug as Phase 0 (`4.1.0.RELEASE` doesn't resolve — the Gradle codepath fails server-side at generation time rather than deferring to a later build failure like Maven's did); used `4.1.0`.
- `apps/api/build.gradle.kts` (new), `settings.gradle.kts` (new), `gradle/wrapper/*`, `gradlew`/`gradlew.bat` (new) — replace `pom.xml`, `mvnw`/`mvnw.cmd`, `.mvn/`.
- `build.gradle.kts` — added `tasks.named<Jar>("jar") { enabled = false }`. The Spring Boot Gradle plugin's default `jar` task emits a `-plain.jar` alongside the executable `bootJar` output; left enabled, the Dockerfile's `COPY .../talos-api-*.jar app.jar` wildcard matched both files and failed. Disabling the plain jar (we only ever ship the executable one) is the standard fix.
- `apps/api/Dockerfile` — build stage now copies `gradle/`, `gradlew`, `build.gradle.kts`, `settings.gradle.kts`, runs `./gradlew dependencies` then `./gradlew bootJar -x test`; runtime stage copies from `build/libs/` instead of `target/`.
- `apps/api/.dockerignore`, `.gitignore`, `.gitattributes` — regenerated for Gradle (`.gradle/`, `build/`, wrapper jar exception) in place of the Maven versions.
- Root `.gitignore` — "Java / Maven" section replaced with "Java / Gradle" (`.gradle/`, wrapper-jar exception path updated).
- `src/main/java/dev/talos/TalosApiApplication.java` and `src/main/resources/application.yml` (Phase 0's trimmed config, no `contextLoads` test) carried over untouched — this was a build-tool swap, not a source change.
- `.github/workflows/ci.yml` — `api` job now uses `gradle/actions/setup-gradle@v4` and `./gradlew --no-daemon build` instead of `./mvnw -B verify`.
- `README.md`, `docs/architecture.md`, `docs/phase-reports/phase-0-report.md` — updated to describe Gradle instead of Maven; `docs/architecture.md` and the phase-0 report both record this as an explicit approved deviation from Section 4.0, distinct from the earlier mechanical/tooling corrections.

**Verification:** `./gradlew build` (after removing stale `build/`) — BUILD SUCCESSFUL, empty test suite (`NO-SOURCE` on `:test`), matching Phase 0's Maven baseline. `docker build -f apps/api/Dockerfile apps/api` — succeeded end to end (dependency resolution → `bootJar` → runtime image), confirming the `-plain.jar` fix. Did not re-run `apps/web` or the Python projects' checks since this change is scoped entirely to `apps/api`.

**Known blockers / follow-ups:**
- CI still hasn't run on GitHub Actions (no remote configured); the updated `api` job is unexercised there, same caveat as Phase 0.

## 2026-07-09 — Phase 0: repository and tooling setup

**Ask:** Bootstrap the Talos monorepo per Section 16 Phase 0 of the implementation plan: the full directory tree from Section 5 (minus Phase 12 remote-trigger adapters), dev infra compose, contract scaffolding, `.env.example` per app, pinned-version docs, and a working build for every app.

**Changed (repo root):**
- `.gitattributes` — `text=auto eol=lf` with explicit LF for `*.sh`/`mvnw`; the host's global `core.autocrlf=true` would otherwise corrupt shell scripts and Dockerfiles committed from this WSL environment. Set `core.autocrlf=false` locally on this repo as well.
- `.gitignore`, `.dockerignore` — standard excludes; root `.dockerignore` matters because `apps/orchestrator` and `apps/runner-supervisor` build with the repo root as Docker context (see below).
- `README.md` — monorepo layout, service table, local dev commands.

**Changed (apps/web):** Angular 22 scaffolded via `ng new` (standalone components, signals, Vitest, SCSS, no SSR) plus `ng add @angular/material` for Material + CDK. Node 22.23.1 installed via `nvm` and pinned in `apps/web/.nvmrc` — the host's default Node (24.11.1, from linuxbrew, ahead of nvm on `PATH`) is below Angular 22's minimum. Replaced the generated splash template (`app.html`) with a bare `<router-outlet />` since routes arrive in later phases, and updated `app.spec.ts` accordingly. Added `Dockerfile` (multi-stage, nginx:1.27-alpine) and `docker-entrypoint.sh`, which renders `/assets/env-config.json` from `TALOS_API_URL` at container start per Appendix A ("injected at container start into the served config").

**Changed (apps/api):** Spring Boot 4.1.0 scaffolded via start.spring.io (package `dev.talos`, Maven, Java 21; web/data-jpa/security/validation/postgresql/flyway/actuator/amqp/data-redis/configuration-processor starters). start.spring.io's `bootVersion` id (`4.1.0.RELEASE`) doesn't match the actual Maven Central artifact version (`4.1.0`, no `.RELEASE` suffix for Boot 4.x) — corrected in `pom.xml`. Removed the generated `contextLoads` test: it boots the full context including a live Postgres/Flyway connection, which doesn't exist yet (no entities, no compose-started DB in CI at this phase); Phase 0's acceptance criteria explicitly allow empty test suites, and a real `contextLoads` test returns in Phase 1/2 backed by Testcontainers. Replaced `application.properties` with `application.yml` wiring the Appendix A env vars (`TALOS_DB_URL`, `TALOS_RABBITMQ_URL`, `TALOS_REDIS_URL`, `TALOS_JWT_SECRET`, etc.) with local defaults. Added `Dockerfile` (multi-stage, eclipse-temurin 21).

**Changed (packages/agent-adapter-spec, apps/orchestrator, apps/runner-supervisor):** Three `uv`-managed Python 3.12 projects (`uv init --lib` / `--app --package`). `agent-adapter-spec` is consumed by both apps as an editable path dependency (`tool.uv.sources`), matching Section 6.3's "shares `packages/agent-adapter-spec` with the orchestrator." Pinned dependencies: `aio-pika`/`httpx` (orchestrator), `fastapi`/`uvicorn[standard]`/`httpx` (runner-supervisor), `pytest`/`pytest-asyncio` (dev, both). Orchestrator's module layout (`main.py`, `pipeline.py`, `api_client.py`, `runner_client.py`, `adapters.py`, `locks.py`) follows Section 6.3's explicit file list exactly; runner-supervisor has no such prescribed layout in the plan, so it was left as a single bootstrap stub rather than inventing submodule names ahead of Phase 6. Every stub function raises `NotImplementedError` naming the phase that fills it in, rather than silently doing nothing. Each project has one real (not fake) test — a package-import assertion — because an empty pytest suite exits 5 (failure), not 0. Added `Dockerfile` for each app; both use the **repo root** as build context (`docker build -f apps/orchestrator/Dockerfile .`) so the shared `agent-adapter-spec` source can be copied in alongside the app.

**Changed (packages/contracts, packages/project-config-schema):** `openapi.yaml` stub (OpenAPI 3.1, empty `paths`, error-envelope schema from Section 10.1) plus `events/README.md` describing the event envelope from Section 11. No endpoints or event schemas defined yet — those are added in the phase that implements their producer/consumer, not speculatively now. `project-config-schema/README.md` placeholder; `talos.schema.json` itself is Phase 3 work.

**Changed (infra):** `docker-compose.dev.yml` — `postgres:17`, `rabbitmq:4.1-management`, `redis:7`, each with a healthcheck and a named volume. Host-side ports for Postgres (5433) and RabbitMQ (5673/15673) are shifted from their defaults because this dev machine already runs unrelated native services on 5432/5672/15672; container-internal ports are untouched, so `TALOS_*_URL` values used between containers on the Docker network stay standard. `infra/dokploy/README.md` and `workers/*/README.md` placeholders for Phase 10/11 content — directories exist per the Section 5 tree, but no Dockerfiles are written yet since there's nothing to build.

**Coverage:** No application logic exists yet, so "coverage" here means each app's toolchain is provably wired up: `mvnw verify`, `ng build` + `ng test`, and `uv run pytest` (x3) all exit 0, and all four Dockerfiles build successfully.

**Verification:**
- `apps/web`: `npm run build` — success (222.76 kB initial, 2.95s). `ng test --watch=false` (Vitest) — 2/2 passed.
- `apps/api`: `./mvnw verify` — BUILD SUCCESS, empty test suite (0 tests), jar produced at `target/talos-api-0.0.1-SNAPSHOT.jar`.
- `packages/agent-adapter-spec`, `apps/orchestrator`, `apps/runner-supervisor`: `uv run pytest` — 1/1 passed each.
- `docker build` for all four apps (`talos-web`, `talos-api`, `talos-orchestrator`, `talos-runner-supervisor`) — all succeeded; `talos-web` additionally smoke-tested by running the container and curling `/` (200) and `/assets/env-config.json` (200, correct `TALOS_API_URL` substitution).
- `docker compose -f infra/docker-compose.dev.yml up -d` — all three services reached `healthy` within ~5s; torn down after verification (`docker compose down`).
- `grep -ri agentos . --exclude-dir=.git --exclude-dir=docs --exclude-dir=.github` — zero results. (The unscoped command self-matches this rule's own text in `docs/` and the CI step name; CI's `naming-guard` job excludes `docs/` and `.github/` for that reason.)
- CI workflow (`.github/workflows/ci.yml`) written to run all of the above on every PR, but not yet exercised by an actual GitHub Actions run (no remote configured for this repo yet).

**Notes:** Docker and `uv` were not present in the initial shell session (`which docker`/`which uv` failed); the operator confirmed both were installed and accessible via `sg docker -c '<cmd>'` (group membership not yet active in existing shells) before scaffolding began. Git is configured locally (not globally) with `user.name = Paul Bernard`, `user.email = paulvbernard73@gmail.com`, per operator instruction to commit after each phase goal without co-authoring trailers.

**Known blockers / follow-ups:**
- CI has not actually run in GitHub Actions (repo has no remote yet); the workflow is written and locally-equivalent commands were verified manually.
- `apps/api` has no entities, migrations, or real config validation yet — `application.yml`'s datasource block is a Phase 1/2 placeholder that will need revisiting once Flyway migrations exist.
- `packages/contracts/runner-api.yaml` (Section 10.5) does not exist yet; deferred to Phase 6 when the runner supervisor's HTTP contract is implemented.
