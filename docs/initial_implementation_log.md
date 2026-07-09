## 2026-07-09 — Phase 2: database and migrations

**Ask:** Implement Section 16 Phase 2: the full MVP schema from Section 9.2 (`V002__core_schema.sql`) and JPA entities/repositories for every remaining table, nothing from Section 9.4.

**Changed (backend):**
- `db/migration/V002__core_schema.sql` — `projects`, `project_configs`, `tasks`, `agent_runs`, `agent_run_steps`, `agent_run_logs`, `approvals`, `git_changes`, `pull_requests`, `integrations`, `secret_values`, `integration_credentials`, copied verbatim from Section 9.2, in FK-safe order.
- `dev.talos.projects`: `ProjectStatus` enum, `Project`/`ProjectConfig` entities + repositories. `ProjectConfig.setActive()` is the only setter added anywhere this phase — needed to enforce "at most one `is_active=true` row per project," the one mutation Section 9.2 names explicitly.
- `dev.talos.tasks`: `TaskStatus`/`TaskPriority`/`TaskRiskLevel` enums, `Task` entity + repository.
- `dev.talos.runs`: `RunStatus`/`TestStatus`/`ReviewStatus`/`StepType`/`StepStatus`/`LogStream`/`GitChangeType` enums, `AgentRun`/`AgentRunStep`/`AgentRunLog`/`GitChange` entities + repositories.
- `dev.talos.approvals`: `ApprovalStatus` enum, `Approval` entity + repository.
- `dev.talos.integrations`: `PullRequestStatus` enum, `Integration`/`PullRequest` entities + repositories. Neither table is explicitly assigned a module in Section 6.2; placed here since `Integration` is self-evidently this module's and `PullRequest` belongs to the GitHub push/PR pipeline Phase 9 assigns to `dev.talos.integrations` — see the phase report for this as a flagged judgment call, not a literal instruction.
- `dev.talos.secrets`: `SecretValue`/`IntegrationCredential` entities + repositories, both javadoc'd as never-exposed-via-REST per Section 12.2.
- Every unconstrained `VARCHAR` column that the DDL only comments example values for (`tasks.source`, `agent_runs.provider_auth_mode`, `approvals.approval_type`, `integrations.type`, `integration_credentials.auth_mode`) was left as a plain `String` field — no CHECK constraint in the DDL means no invented Java enum either.

**Root cause (bug fixed this phase):** `ddl-auto=validate` failed on first run: `wrong column type encountered in column [message] in table [agent_run_logs]; found [text (Types#VARCHAR)], but expecting [oid (Types#CLOB)]`. Every `TEXT`/`BYTEA` column had been mapped with `@Lob`, which on PostgreSQL makes Hibernate target `Types.CLOB`/`Types.BLOB` — implemented via Postgres's `oid` large-object mechanism, not the plain `text`/`bytea` types the migration declares. Removed `@Lob` from all seven affected fields (`AgentRun.prompt`/`summary`/`errorMessage`, `Approval.notes`, `ProjectConfig.configYaml`, `Task.description`, `AgentRunStep.summary`) plus `SecretValue`'s two `byte[]` fields; Hibernate's un-annotated default mapping for `String`/`byte[]` is exactly the `VARCHAR`-family/`VARBINARY`-family type Postgres's `TEXT`/`BYTEA` actually are.

**Changed (tests):**
- `apps/api/src/test/java/dev/talos/CoreSchemaRepositoryTest.java` (new) — one round-trip test per new entity, built as a real FK chain (project → task → run → step/log/change/approval/PR; integration → secret → credential) since orphan UUIDs would be rejected by the FK constraints anyway, so this doubles as a referential-integrity check. Plus `flywayMigrateTwice_isIdempotent`, which re-invokes the autowired `Flyway` bean's `.migrate()` inside a running context and asserts zero new migrations execute.

**Coverage:** `CoreSchemaRepositoryTest` (4 tests) + Phase 1's `AuthControllerIntegrationTest` (4 tests) = 8 tests, all green. Every one of the 12 new tables has at least one entity persisted and re-read in the test suite.

**Verification:** `./gradlew build` — BUILD SUCCESSFUL, 8/8 tests. `docker compose -f infra/docker-compose.dev.yml up -d --build` — `talos-api` healthy; `psql` confirmed all 15 tables (14 domain + `flyway_schema_history`) exist and both `V001`/`V002` rows show `success=t`; re-ran the Phase 1 login smoke test to confirm no regression. Compose stack and built image torn down afterward. `grep -ri agentos . --exclude-dir=.git --exclude-dir=docs --exclude-dir=.github --exclude=CLAUDE.md --exclude=AGENTS.md` — zero results. Did not re-run `apps/web`/Python suites (untouched this phase). CI still hasn't run on GitHub Actions (no remote) — unchanged caveat.

**Known blockers / follow-ups:**
- Every entity's `created_at`/`updated_at` is still `insertable=false, updatable=false` (DB-default only) — correct for now since nothing mutates rows yet, but each phase that adds a real update path (Phase 3 onward) needs to flip `updated_at` to `updatable=true` and set it explicitly to actually satisfy Section 9.1's "maintained by the API on every mutation" rule. Flagged in the phase report so it isn't silently forgotten.
- CI still hasn't run on GitHub Actions (no remote configured).

## 2026-07-09 — Phase 1: backend foundation (auth, errors, audit)

**Ask:** Implement Section 16 Phase 1: a running API with login, JWT security, the standard error envelope, and an audit writer — `dev.talos.auth`, `dev.talos.audit`, `dev.talos.common`, `V001__users_and_audit.sql`.

**Changed (backend):**
- `db/migration/V001__users_and_audit.sql` — `users`/`audit_events` DDL, copied verbatim from Section 9.2.
- `dev.talos.common`: `UuidV7` (wraps `com.github.f4b6a3:uuid-creator`'s `getTimeOrderedEpoch()`), `ErrorResponse`/`ApiException`/`GlobalExceptionHandler` (the Section 10.1 envelope), `TalosProperties` (`@ConfigurationProperties(prefix="talos")` binding the `TALOS_*` env vars from Appendix A).
- `dev.talos.audit`: `AuditEvent` entity (`details_json` mapped as `Map<String,Object>` via `@JdbcTypeCode(SqlTypes.JSON)`, Hibernate's native JSON support — no extra library needed), `AuditEventRepository`, `AuditService.record(...)`.
- `dev.talos.auth`: `Role` enum, `User` entity (id/createdAt/updatedAt all `insertable=false,updatable=false`, left to the DB's `DEFAULT now()` — nothing mutates a `User` after creation yet, so there's no premature `@PreUpdate`/touch() machinery), `UserRepository`, `AdminSeeder` (idempotent `ApplicationRunner`, see phase report for why this isn't a SQL migration), `JwtService` (`io.jsonwebtoken:jjwt` 0.13.0 — not covered by any Boot starter, added explicitly), `AuthenticatedUser` principal, `JwtAuthenticationFilter`, `JsonAuthenticationEntryPoint`, `SecurityConfig` (stateless, `/actuator/health` + `POST /auth/login` public, everything else authenticated), `AuthService`/`AuthController`/`dto.LoginRequest`/`dto.LoginResponse` for `POST /api/v1/auth/login`.
- `apps/api/build.gradle.kts` — added `uuid-creator`, `jjwt-{api,impl,jackson}`, `spring-boot-testcontainers` + pinned `testcontainers-{junit-jupiter,postgresql}:2.0.5`; disabled the Spring Boot plugin's plain `jar` task output was already off from the Maven→Gradle switch, unrelated here. Added `tasks.withType<JavaCompile> { options.compilerArgs.add("-Xlint:deprecation") }` permanently after it caught a real deprecated-API usage (see Notes below and the phase report).
- `apps/api/src/main/resources/application.yml` — disabled `management.health.redis`/`management.health.rabbit` (neither integration is load-bearing until Phase 5+; leaving them on made `/actuator/health` falsely flaky).
- `apps/api/src/main/java/dev/talos/common/GlobalExceptionHandler.java` — added a specific `NoResourceFoundException -> 404` handler. Without it, the catch-all `Exception` handler turned every unmatched route into a 500. Found via manual `curl` smoke testing against the live compose stack, not by the unit tests (see phase report Notes for why).
- `infra/docker-compose.dev.yml` — added the `api` service (build context `../apps/api`, **not** repo root — unlike `orchestrator`/`runner-supervisor`, `apps/api` has no shared-package dependency, so it builds from its own directory), wired to `postgres`/`rabbitmq`/`redis` with `depends_on: condition: service_healthy` and dev-only placeholder secrets (same "committed on purpose, obviously fake" pattern as the Phase 0 Postgres credentials).

**Changed (tests):**
- `apps/api/src/test/java/dev/talos/auth/AuthControllerIntegrationTest.java` — `@SpringBootTest` + `@AutoConfigureMockMvc` + Testcontainers `postgres:17` via `@ServiceConnection`. Four cases: seeded login succeeds and writes the audit row, wrong password → 401, unauthenticated request to an arbitrary path → 401, `/actuator/health` → 200. RabbitMQ/Redis aren't containerized for this test (not used yet); pointed their config at `localhost` addresses that are never dialed during the test's requests, rather than standing up containers for integrations nothing exercises.

**Coverage:** `AuthControllerIntegrationTest` (4 tests) is currently the only test class in `apps/api`; it exercises the full login round-trip end to end against a real Postgres, not mocks.

**Verification:** `./gradlew build` — BUILD SUCCESSFUL (compile + all 4 tests, via Testcontainers). Manual `curl` pass against `docker compose -f infra/docker-compose.dev.yml up -d --build`: health public (200), correct login (200 + JWT), wrong password (401 `INVALID_CREDENTIALS`), no token (401 `UNAUTHORIZED`), valid token on an unmatched route (404 `NOT_FOUND`, confirming the `NoResourceFoundException` fix). Confirmed the audit row directly via `psql`. Compose stack and the built image torn down afterward. `grep -ri agentos . --exclude-dir=.git --exclude-dir=docs --exclude-dir=.github --exclude=CLAUDE.md --exclude=AGENTS.md` — zero results. Did not re-run `apps/web`/Python test suites (untouched this phase). CI has not run on GitHub Actions yet (no remote configured) — same standing caveat as Phase 0.

**Notes:**
- Three separate "the obvious API from Spring Boot 3 / Testcontainers 1.x has moved" surprises this phase, all from being on genuinely new major versions (Boot 4.1, Jackson 3, Testcontainers 2.0, Spring Framework 7): Jackson's `ObjectMapper` moved to `tools.jackson.databind` (new Maven groupId `tools.jackson`, not `com.fasterxml.jackson`); `org.springframework.lang.NonNull` is deprecated in favor of JSpecify's `org.jspecify.annotations.NonNull`; Testcontainers' `junit-jupiter`/`postgresql` artifacts were renamed to `testcontainers-junit-jupiter`/`testcontainers-postgresql` and `PostgreSQLContainer` moved packages and dropped its generic self-type. None of these are called out in the implementation plan (reasonably — it predates verifying exact library APIs), so each was resolved by inspecting the actual resolved classpath/jar contents rather than guessing, per the plan's "verify against observed reality" instruction for exactly this kind of drift.
- `grep -ri agentos .`'s scoping needed to grow again: `CLAUDE.md`/`AGENTS.md` (added between Phase 0 and Phase 1) document the naming rule itself, so the unscoped command self-matched them the same way it already self-matched `docs/`. Extended CI's `naming-guard` job and both files' own text to also `--exclude=CLAUDE.md --exclude=AGENTS.md`. Phase 0's report/log entries are left untouched (accurate when written); see the Phase 1 report's Deviations section for why that's not a rewrite of history.

**Known blockers / follow-ups:**
- CI still hasn't run on GitHub Actions (no remote). Docker is available by default on GitHub's `ubuntu-latest` runners, so `AuthControllerIntegrationTest`'s Testcontainers usage is expected to work there unmodified, but that's unconfirmed.
- `apps/api`'s `.dockerignore`/`.gitignore`/`.gitattributes` and `application.yml` datasource block are all still Phase 0/1-era; nothing about them is expected to change until Phase 2 adds the rest of the schema.

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
